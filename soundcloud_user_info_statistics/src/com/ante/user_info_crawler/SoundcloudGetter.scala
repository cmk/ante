package com.ante.user_info_crawler

import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.logging.Logger
import com.ante.soundcloud_interface.SoundcloudUser
import com.ante.soundcloud_interface.SoundcloudTrack
import com.ante.soundcloud_interface.SoundcloudUrls
import java.util.concurrent.Future
import com.google.appengine.api.urlfetch
import scala.collection.mutable.ArrayBuilder
import java.security.GeneralSecurityException
import java.io.IOException
import com.google.api.services.datastore.client.DatastoreOptions
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import java.io.File
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.appengine.api.datastore.DatastoreService
import com.google.appengine.api.datastore.DatastoreServiceFactory
import com.google.appengine.api.datastore.Entity
import com.google.appengine.api.datastore.Key
import com.google.appengine.api.datastore.KeyFactory
import com.google.appengine.api.datastore.Query
import com.ante.gae_interface.DatastoreWrapper
import com.ante.gae_interface.UrlFetcherWrapper.fetchWebsite
import com.ante.soundcloud_interface.SoundcloudWebsiteGetter
import com.ante.gae_interface.BigqueryWrapper
import com.ante.gae_interface.AppEngineWrapper
import com.google.api.services.bigquery.model.TableRow
import com.google.api.services.bigquery.model.TableDataInsertAllRequest
import com.google.api.services.datastore.client.Datastore
import com.google.appengine.api.datastore.EntityNotFoundException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar

class SoundcloudGetter extends HttpServlet
{
	val log : Logger = Logger.getLogger("SoundcloudGetter")
	
	// I noticed that there are a lot of tracks which have basically zero playcounts
	// there is no reason to keep track of them every single day
	// this function filters out tracks with low playcounts on certain days
	def filter_tracks(tracks : Array[SoundcloudTrack], iteration : Int) : Array[SoundcloudTrack] =
	{
	  val date_format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
	  val always_track_date = Calendar.getInstance()
	  always_track_date.add(Calendar.DAY_OF_MONTH, -7)
	  val always_track_time = always_track_date.getTime
	  def should_keep(track : SoundcloudTrack) : Boolean =
	  {
	    val date = date_format.parse(track.created_at)
	    if (always_track_time.before(date))
	      return true
	    
	    val playback_count = track.playback_count.getOrElse(0)
	    if (playback_count == 0)
	      return true
	    val hash = track.id - iteration
	    if (playback_count < 100)
	    {
	      // only store values once every eight days
	      (hash % 8) == 0
	    }
	    else if (playback_count < 1000)
	    {
	      // only store values once every four days
	      (hash % 4) == 0
	    }
	    else if (playback_count < 10000)
	    {
	      // only store values once every two days
	      (hash % 2) == 0
	    }
	    else
	      true
	  }
	  tracks filter should_keep
	}
	
	def getAndStoreSnapShot(datastore : DatastoreService, id : Int)
	{
		val iteration = AnteInfo.get_current_iteration
		val user_and_tracks = SoundcloudWebsiteGetter.getAllTracksForUser(SoundcloudUrls.get_tracks_url, _.track_count)(id)
		user_and_tracks match
		{
		  case Some((user, unfiltered_tracks)) =>
		  {
			  datastore.put(UserStatisticsSnapshot.entityForSnapshot(new UserStatisticsSnapshot(user, unfiltered_tracks, iteration)))
			  val filtered_tracks : Array[SoundcloudTrack] = filter_tracks(unfiltered_tracks, iteration)
			  if (filtered_tracks.isEmpty)
			    return
			  val snapshots : Array[TrackSnapshot] = for (track <- filtered_tracks)
			      yield new TrackSnapshot(track, iteration)
			  SoundcloudGetter.storeInBigquery(snapshots)
			  DatastoreWrapper.put(datastore, snapshots map TrackSnapshot.entityForTrack)
			  val playcount_entities : Iterable[(TrackSnapshot, Entity)] = for {snapshot <- snapshots
			       if (snapshot.playback_count != 0)}
			    yield (snapshot, SoundcloudGetter.playcountEntityForSnapshot(datastore, snapshot))
			  if (playcount_entities.isEmpty)
			    return
			  
			  DatastoreWrapper.put(datastore, playcount_entities map (_._2))
			  val tasks : Iterable[(Int, Map[Int, Long])] = for ((snapshot, entity) <- playcount_entities)
			    yield (snapshot.id, TrackSnapshot.get_playcounts_for_track(entity))
			  TrackWavelet.enqueueTracksWithPlaycounts(tasks, iteration)
		  }
		  case None => SoundcloudUser.store_user_deleted(id, datastore)
		}
	}
	
	override def doPost(req : HttpServletRequest, resp : HttpServletResponse)
	{
	  val datastore = DatastoreWrapper.connect_to_google_database()
	  val id : Int = req.getParameter("id").toInt
	  try
	  {
		  getAndStoreSnapShot(datastore, id)
	  }
	  catch
	  {
		  case ex : Throwable =>
		  {
			  SoundcloudGetter.logger.log(java.util.logging.Level.SEVERE, "Error while trying to get statistics for ID " + id.toString)
			  throw ex
		  }
	  }
	}
}

object SoundcloudGetter
{
	private val logger = java.util.logging.Logger.getLogger(SoundcloudGetter.getClass.getName)
	
	val dataset_id = "timeseries"
	
	def storeInBigquery(snapshots : Traversable[TrackSnapshot])
	{
		val bigquery = BigqueryWrapper.connect(BigqueryWrapper.authorizeFromAppEngine)
		val rows = snapshots map TrackSnapshot.rowForTrack
		val project = AppEngineWrapper.project
		def insertable_row(row : TableRow) : TableDataInsertAllRequest.Rows =
		{
		  new TableDataInsertAllRequest.Rows().setJson(row)
		}
		val insertable_rows : Array[TableDataInsertAllRequest.Rows] = (rows map insertable_row).toArray
		BigqueryWrapper.createTable(project, dataset_id, TrackSnapshot.tableForTracks(project, dataset_id), bigquery)
		BigqueryWrapper.insert(insertable_rows, dataset_id, TrackSnapshot.table_id, bigquery)
	}

  	def getStatisticsForUser(datastore : DatastoreService, user_id : Int) : Array[UserStatisticsSnapshot] =
  	{
		val entities = DatastoreWrapper.getEntitiesForUser(datastore, user_id, UserStatisticsSnapshot.entity_kind)
		(entities map UserStatisticsSnapshot.snapshotForEntity).toArray
  	}
	  
	def getPlaycountEntity(datastore : DatastoreService, track_id : Int) : Entity =
	{
	  val key = KeyFactory.createKey(TrackSnapshot.playcount_table, track_id.toLong)
	  try
	  {
	    datastore.get(key)
	  }
	  catch
	  {
	    case _ : EntityNotFoundException => new Entity(key)
	  }
	}
	def addPlaycount(entity : Entity, track : TrackSnapshot)
	{
	  entity.setUnindexedProperty(track.iteration.toString, track.playback_count)
	}
	def playcountEntityForSnapshot(datastore : DatastoreService, track : TrackSnapshot) : Entity =
	{
	  val entity = getPlaycountEntity(datastore, track.id)
	  addPlaycount(entity, track)
	  entity
	}
}

