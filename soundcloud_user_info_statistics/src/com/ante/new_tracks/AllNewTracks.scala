package com.ante.new_tracks

import javax.servlet.http.HttpServlet
import java.util.logging.Logger
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import com.ante.soundcloud_interface.SoundcloudWebsiteGetter
import com.ante.gae_interface.UrlFetcherWrapper
import com.ante.soundcloud_interface.SoundcloudUrls
import com.ante.soundcloud_interface.SoundcloudTrack
import com.ante.gae_interface.DatastoreWrapper
import com.google.appengine.api.datastore.Entity
import com.google.appengine.api.datastore.KeyFactory
import java.util.logging.Level
import com.google.appengine.api.datastore.EntityNotFoundException
import java.util.concurrent.Future
import com.ante.soundcloud_interface.SoundcloudWebsiteGetter.SoundcloudError
import com.google.appengine.api.urlfetch.HTTPResponse
import com.google.appengine.api.datastore.Query
import com.google.appengine.api.datastore.DatastoreService

object TimeStuff
{
  def get_current_time() : Long =
    System.currentTimeMillis()
  
  val one_second = 1000
  val one_minute = 60 * one_second
  val one_hour = 60 * one_minute
  
  def time_to_minute(time : Long) : Long =
  {
	time / one_minute
  }
}

class AllNewTracks extends HttpServlet
{
  val log = Logger.getLogger("AllNewTracks")
  
  val new_tracks_kind = "new_tracks"
  
  def key_for_minute(minute : Long) =
    KeyFactory.createKey(new_tracks_kind, minute)
    
  val track_ids_key = "track_ids"
    
  def entity_for_tracks(minute : Long, tracks : Iterable[SoundcloudTrack]) : Entity =
  {
	val entity = new Entity(key_for_minute(minute))
	import collection.JavaConverters._
	entity.setUnindexedProperty(track_ids_key, (tracks map (_.id)).asJavaCollection)
	entity
  }
  
  def entity_for_first_day_playcount(minute : Long, tracks : Iterable[SoundcloudTrack]) : Entity =
  {
    val entity = new Entity(AllNewTracks.key_for_playcount(minute))
    val user_entries = collection.mutable.Map[Int, Long]()
    for (track <- tracks
        if (!track.playback_count.isEmpty))
    {
      val user_id = track.user_id
      val playback_count = track.playback_count.get
      if (user_entries.getOrElseUpdate(user_id, playback_count) <= playback_count)
      {
        user_entries(user_id) = playback_count
        entity.setUnindexedProperty(user_id.toString, playback_count)
      }
    }
    entity
  }
  
  
  def store_todays_tracks(time : Long)
  {
    val tracks_url = SoundcloudUrls.get_latest_tracks_url(SoundcloudUrls.max_limit, 0)
    val minute = TimeStuff.time_to_minute(time)
    val got_tracks = SoundcloudWebsiteGetter.responseToTracks(UrlFetcherWrapper.fetchWebsite(tracks_url))
    got_tracks match
    {
      case Right(tracks) =>
      {
	    val datastore = DatastoreWrapper.connect_to_google_database
	    datastore.put(entity_for_tracks(minute, tracks))
      }
      case Left(error) =>
      {
        log.log(Level.SEVERE, "Couldn't get latest tracks from Soundcloud for minute " + minute.toString + ":\n" + error.text)
      }
    }
  }
  
  def track_response_to_track(url : String, response : Future[HTTPResponse]) : Either[SoundcloudError, SoundcloudTrack] =
  {
    val json = SoundcloudWebsiteGetter.jsonWebsiteContent(response)
    json match
    {
      case Right(json) => Right(SoundcloudTrack.from_json(json))
      case Left(error) =>
	  {
	    import SoundcloudWebsiteGetter.SoundcloudErrorType._
	    error.errorType match
	    {
	      case ObjectDeleted => Left(error)
	      case SoundcloudSaysTryAgain =>
	      {
	        val second_response = UrlFetcherWrapper.fetchWebsite(url)
	        val second_json = SoundcloudWebsiteGetter.jsonWebsiteContent(second_response)
	        second_json match
	        {
	          case Right(json) => Right(SoundcloudTrack.from_json(json))
	          case Left(error) => Left(error)
	        }
	      }
	      case OtherDidntGetJson => Left(error)
	      case InternalServerError => Left(error)
          case ServiceUnavailable => Left(error)
	    }
	  }
	}
  }
  
  def store_playcounts_for_yesterdays_tracks(time : Long)
  {
    val minute = TimeStuff.time_to_minute(time - TimeStuff.one_hour)
    val datastore = DatastoreWrapper.connect_to_google_database
    try
    {
    	val entity = datastore.get(key_for_minute(minute))
    	val track_ids = entity.getProperty(track_ids_key).asInstanceOf[java.util.Collection[Long]]
    	import collection.JavaConverters._
    	val track_urls = track_ids.asScala map SoundcloudUrls.get_track_url
    	val track_responses = track_urls map UrlFetcherWrapper.fetchWebsite
    	val track_or_errors = for ((url, response) <- track_urls zip track_responses)
    	  yield track_response_to_track(url, response)
    	val tracks = for (Right(track) <- track_or_errors) yield track
    	val errors = for
    	{
    	  Left(error) <- track_or_errors
    	  if (error.errorType != SoundcloudWebsiteGetter.SoundcloudErrorType.ObjectDeleted)
    	}
    		yield error
    	
    	if (!errors.isEmpty)
    	{
    	  log.severe("Got errors while getting tracks:\n" + (errors map (_.text)).mkString("\n"))
    	}
    	datastore.put(entity_for_first_day_playcount(minute, tracks))
    }
    catch
    {
      case _ : EntityNotFoundException => // ignore. the error should have gotten logged when we
        								  // first tried to get the tracks one day ago
    }
  }
  
  override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
  {
    val time = TimeStuff.get_current_time
    store_todays_tracks(time)
    store_playcounts_for_yesterdays_tracks(time)
  }
}

object AllNewTracks
{
  val day_playcount_kind = "first_day_playcount"
  def key_for_playcount(minute : Long) =
    KeyFactory.createKey(day_playcount_kind, minute)
}

class FilterNewTracks extends HttpServlet
{
  override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
  {
    val time = TimeStuff.get_current_time
    val id_two_days_ago = TimeStuff.time_to_minute(time - 2 * TimeStuff.one_hour)
    val id_one_day_ago = TimeStuff.time_to_minute(time - TimeStuff.one_hour)
	val datastore = DatastoreWrapper.connect_to_google_database
    val entities = FilterNewTracks.get_playcount_entities(datastore, id_two_days_ago, id_one_day_ago)
    val playcounts = FilterNewTracks.get_playcounts(entities)
    val to_keep = FilterNewTracks.top_by_playcounts(playcounts)
    datastore.put(FilterNewTracks.entity_for_users(time, to_keep))
  }
}

object FilterNewTracks
{
  val log = Logger.getLogger("FilterNewTracks")
  def get_playcount_entities(datastore : DatastoreService, min_time : Long, max_time : Long) : Iterable[Entity] =
  {
    val kind = AllNewTracks.day_playcount_kind
	val query = new Query(kind)
	query.addFilter(Entity.KEY_RESERVED_PROPERTY, Query.FilterOperator.GREATER_THAN_OR_EQUAL, AllNewTracks.key_for_playcount(min_time))
	query.addFilter(Entity.KEY_RESERVED_PROPERTY, Query.FilterOperator.LESS_THAN, AllNewTracks.key_for_playcount(max_time))
	val results = datastore.prepare(query)
	import scala.collection.JavaConverters._
	results.asIterable().asScala
  }
  
  def get_playcounts(playcount_entities : Iterable[Entity]) : collection.mutable.Map[Long, Int] =
  {
    val map = collection.mutable.HashMap[Long, Int]()
    import collection.JavaConverters._
    for {entity <- playcount_entities
        property <- entity.getProperties().asScala}
    {
      val user_id = property._1.toLong
      val playcount = property._2.asInstanceOf[Long].toInt
      if (map.getOrElseUpdate(user_id, playcount) > playcount)
        map(user_id) = playcount
    }
    map
  }
  
  def top_by_playcounts(playcounts : collection.mutable.Map[Long, Int]) : Iterable[Long] =
  {
    val sorted = playcounts.toArray.sortBy(_._2)
    // keep top 0.5%
    // seems like otherwise there's too much crap
    for (i <- 1 to (sorted.size / 200))
      yield sorted(sorted.size - i)._1
  }
  
  val hourly_users_key = "hourly_users_to_watch"
  val hourly_users_property = "users_to_watch"
  
  def entity_for_users(time : Long, to_keep : Iterable[Long]) : Entity =
  {
    val day = time / TimeStuff.one_hour
    val key = KeyFactory.createKey(hourly_users_key, day)
    val entity = new Entity(key)
    import collection.JavaConverters._
    entity.setUnindexedProperty(hourly_users_property, to_keep.asJavaCollection)
    entity
  }
}
