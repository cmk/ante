package com.ante.user_info_crawler

import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import com.ante.gae_interface.DatastoreWrapper
import com.ante.gae_interface.UrlFetcherWrapper
import com.ante.soundcloud_interface.SoundcloudWebsiteGetter
import com.ante.soundcloud_interface.SoundcloudUrls
import com.ante.soundcloud_interface.SoundcloudUser
import com.ante.soundcloud_interface.SoundcloudTrack
import com.google.appengine.api.datastore.Entity
import com.google.appengine.api.datastore.Key
import com.google.appengine.api.datastore.KeyFactory
import com.google.api.services.datastore.client.Datastore
import com.google.appengine.api.datastore.Query
import com.google.appengine.api.datastore.Query.FilterPredicate
import com.google.appengine.api.datastore.Query.CompositeFilterOperator
import scala.collection.JavaConversions
import com.google.appengine.api.datastore.Query.Filter
import com.google.appengine.api.datastore.DatastoreService
import com.google.appengine.api.taskqueue.QueueFactory
import com.google.appengine.api.taskqueue.Queue
import com.google.appengine.api.taskqueue.TaskOptions
import com.google.appengine.api.datastore.Text
import com.google.appengine.api.taskqueue.TaskAlreadyExistsException
import com.google.appengine.api.memcache.MemcacheServiceFactory
import com.google.appengine.api.taskqueue.TransientFailureException
import com.ante.gae_interface.TaskQueueWrapper

class FavoritesCrawler extends HttpServlet
{
	def keyForTrack(track : SoundcloudTrack) : Key =
	  KeyFactory.createKey("track", track.id)
	  
	def keyForFavorites(user_id : Int) : Key =
	  KeyFactory.createKey("favorites", user_id)
	  
	def entityForTrack(track : SoundcloudTrack) : Entity =
	{
		val entity = new Entity(keyForTrack(track))
		def setOptionalUnindexedProperty[A](name : String, value : Option[A])
		{
		  value match
		  {
		    case Some(s) => entity.setUnindexedProperty(name, s)
		    case None =>
		  }
		}
		import track._
		// todo: java has ways of doing this automatically
		entity.setUnindexedProperty("permalink", permalink)
		entity.setUnindexedProperty("title", title)
		entity.setUnindexedProperty("artwork_url", artwork_url)
		if (description != null)
			entity.setUnindexedProperty("description", new Text(description))
		entity.setUnindexedProperty("user_id", user_id)
		entity.setUnindexedProperty("sharing", sharing)
		setOptionalUnindexedProperty("shared_to_count", shared_to_count)
		entity.setUnindexedProperty("embeddable_by", embeddable_by)
		entity.setUnindexedProperty("purchase_url", purchase_url)
		entity.setUnindexedProperty("purchase_title", purchase_title)
		entity.setUnindexedProperty("duration", duration)
		entity.setUnindexedProperty("genre", genre)
		if (tag_list != null)
			entity.setUnindexedProperty("tag_list", new Text(tag_list))
		entity.setUnindexedProperty("track_type", track_type)
		setOptionalUnindexedProperty("bpm", bpm)
		setOptionalUnindexedProperty("label_id", label_id)
		entity.setUnindexedProperty("label_name", label_name)
		entity.setUnindexedProperty("created_at", created_at)
		setOptionalUnindexedProperty("release_day", release_day)
		setOptionalUnindexedProperty("release_month", release_month)
		setOptionalUnindexedProperty("release_year", release_year)
		setOptionalUnindexedProperty("streamable", streamable)
		setOptionalUnindexedProperty("stream_url", stream_url)
		entity.setUnindexedProperty("downloadable", downloadable)
		setOptionalUnindexedProperty("download_url", download_url)
		setOptionalUnindexedProperty("download_count", download_count)
		entity.setUnindexedProperty("state", state)
		entity.setUnindexedProperty("license", license)
		entity.setUnindexedProperty("waveform_url", waveform_url)
		entity.setUnindexedProperty("video_url", video_url)
		entity.setUnindexedProperty("commentable", commentable)
		setOptionalUnindexedProperty("comment_count", comment_count)
		entity.setUnindexedProperty("isrc", isrc)
		entity.setUnindexedProperty("key_signature", key_signature)
		setOptionalUnindexedProperty("playback_count", playback_count)
		setOptionalUnindexedProperty("favoritings_count", favoritings_count)
		entity.setUnindexedProperty("original_format", original_format)
		setOptionalUnindexedProperty("original_content_size", original_content_size)
		entity
	}
	
	def entityForGraph(id : Int, track_ids : Array[Int], user_ids : Array[Int]) : Entity =
	{
	  val entity = new Entity(keyForFavorites(id))
	  entity.setUnindexedProperty("track_ids", collection.JavaConversions.asJavaCollection(track_ids))
	  entity.setUnindexedProperty("user_ids", collection.JavaConversions.asJavaCollection(user_ids))
	  entity
	}
	
	def keyForUser(user : SoundcloudUser) : Key =
	  KeyFactory.createKey("user", user.id)
	
	def entityForUser(user : SoundcloudUser) : Entity =
	{
	    val entity = new Entity(keyForUser(user))
		import user._
		entity.setUnindexedProperty("permalink", permalink)
		
		entity.setUnindexedProperty("followers_count", followers_count)
		entity.setUnindexedProperty("followings_count", followings_count)
		entity.setUnindexedProperty("playlist_count", playlist_count)
		entity.setUnindexedProperty("public_favorites_count", public_favorites_count)
		entity.setUnindexedProperty("track_count", track_count)
		
		entity.setUnindexedProperty("username", username)
		if (description != null)
			entity.setUnindexedProperty("description", new Text(description))
		entity.setUnindexedProperty("avatar_url", avatar_url)
		
		entity.setUnindexedProperty("city", city)
		entity.setUnindexedProperty("country", country)
		
		entity.setUnindexedProperty("first_name", first_name)
		entity.setUnindexedProperty("last_name", last_name)
		
		entity.setUnindexedProperty("discogs_name", discogs_name)
		entity.setUnindexedProperty("myspace_name", myspace_name)
		
		entity.setUnindexedProperty("plan", plan)
		
		entity.setUnindexedProperty("website", website)
		entity.setUnindexedProperty("website_title", website_title)
	    entity
	}
	
	def restartSelfWithNewIds(datastore : DatastoreService, ids : Set[Int])
	{
	  if (ids.isEmpty)
	    return
	  val keys = for (id <- ids)
	    yield keyForFavorites(id)
	  // the datastore would crash when I asked for 2101 favorites at once
	  // so I'll just ask in groups of 1000s. another possible source of
	  // errors is that I get an entity when really I just want to know whether
	  // it would return something or not
	  val to_call = ids -- (keys.grouped(1000).map(FavoritesCrawler.get_existing_ids(datastore, _)) flatMap identity)
	  
	  if (!to_call.isEmpty)
	  {
	    try
	    {
		  FavoritesCrawler.crawlTasks(to_call map FavoritesCrawler.taskForUser)
	    }
	    catch
	    {
	      case ex : TaskAlreadyExistsException => // ignore
	    }
	  }
	}
	
	def storeUserFailed(id : Int, datastore : DatastoreService)
	{
	  datastore.put(new Entity(KeyFactory.createKey("failed_favorites_crawls", id)))
	}
	
	def crawlFavorites(datastore : DatastoreService, id : Int)
	{
	  val user_and_tracks = SoundcloudWebsiteGetter.getAllTracksForUser(SoundcloudUrls.get_favorites_url, _.public_favorites_count)(id)
	  user_and_tracks match
	  {
	    case Some((user, tracks)) =>
	    {
	      //val entities = tracks map entityForTrack
		  val track_ids = (tracks map ((track : SoundcloudTrack) => track.id)).sorted
		  val user_ids = (tracks map ((track : SoundcloudTrack) => track.user_id)).sorted
		  datastore.put(entityForUser(user))
		  datastore.put(entityForGraph(id, track_ids, user_ids))
		  // todo: put a memcache layer around this to only store tracks that aren't
		  // already in memcache
		  //datastore.put(collection.JavaConversions.asJavaIterable(entities))
		  restartSelfWithNewIds(datastore, user_ids.toSet)
	    }
	    case None => SoundcloudUser.store_user_deleted(id, datastore)
	  }
	}
  
	override def doPost(req : HttpServletRequest, resp : HttpServletResponse)
	{
	  val datastore = DatastoreWrapper.connect_to_google_database()
	  val id : Int = req.getHeader("X-AppEngine-TaskName").toInt
	  val retries : Int = req.getHeader("X-AppEngine-TaskRetryCount").toInt
	  if (retries > 0)
	  {
	    storeUserFailed(id, datastore)
	    return
	  }
	  try
	  {
		  crawlFavorites(datastore, id)
	  }
	  catch
	  {
	    case ex : Throwable =>
	    {
	      FavoritesCrawler.logger.log(java.util.logging.Level.SEVERE, "Error while trying to crawl the favorites for user " + id.toString)
	      throw ex
	    }
	  }
	}
}

object FavoritesCrawler
{
  private val logger : java.util.logging.Logger = java.util.logging.Logger.getLogger(FavoritesCrawler.getClass.getName);
  
  def taskForUser(id : Int) : TaskOptions =
  {
    TaskOptions.Builder.withTaskName(id.toString)
  }
  def crawlTasks(tasks : Iterable[TaskOptions])
  {
    TaskQueueWrapper.addToQueue(getQueue, tasks)
  }
  
  private def getQueue() =
    QueueFactory.getQueue("crawl-favorites")
    
  val existing_ids_local = scala.collection.mutable.Set[Int]()
    
  private def get_existing_ids(datastore : DatastoreService, keys : Set[Key]) : Iterable[Int] =
  {
    val unknown = scala.collection.mutable.Set[Key]()
    unknown ++= keys
    
    def key_to_id(key : Key) = key.getId().toInt
    
    val existing_keys = scala.collection.mutable.Set[Key]()
    val from_memory = unknown.filter((key) => existing_ids_local.contains(key_to_id(key)))
    existing_keys ++= from_memory
    unknown --= from_memory
    if (unknown.isEmpty)
      return existing_keys map key_to_id
    
    val memcache = MemcacheServiceFactory.getMemcacheService()
    val from_memcache = unknown.filter((key) => memcache.get(key_to_id(key)) != null)
    existing_keys ++= from_memcache
    unknown --= from_memcache
    
	for (new_key <- from_memcache)
	  existing_ids_local += key_to_id(new_key)
    
    if (unknown.isEmpty)
      return existing_keys map key_to_id

   	val from_datastore = JavaConversions.asScalaSet(datastore.get(JavaConversions.asJavaIterable(unknown)).keySet())
   	existing_keys ++= from_datastore
	for (new_key <- from_datastore)
	{
	  val id = key_to_id(new_key)
	  memcache.put(id, true)
	  existing_ids_local += id
	}
	
	existing_keys map key_to_id
  }
}

// for debugging purposes
class CrawlLorde extends HttpServlet
{
	override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
	{
	    FavoritesCrawler.crawlTasks(List(FavoritesCrawler.taskForUser(27622444)))
	    resp.setContentType("text/plain")
	    resp.getWriter().println("Crawling Lorde")
	}
}

// for debugging purposes
class DeleteAllFavorites extends HttpServlet
{
	override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
	{
		val datastore = DatastoreWrapper.connect_to_google_database()
		val all_entities = datastore.prepare(new Query("favorites"))
		val all_keys = JavaConversions.asScalaIterator(all_entities.asIterator()) map ((entity : Entity) => entity.getKey())
		val as_list = all_keys.toList
		if (as_list.length > 10000) // just to prevent me from accidentally deleting everything
		  return					// this function is only for debugging offline
		datastore.delete(JavaConversions.asJavaIterable(as_list))
	    resp.setContentType("text/plain")
	    resp.getWriter().println("Deleted all Favorites")
	}
}
