package com.ante.user_info_crawler

import javax.servlet.http.HttpServlet
import com.google.appengine.api.datastore.DatastoreService
import com.ante.gae_interface.DatastoreWrapper
import com.google.appengine.api.datastore.Entity
import com.google.appengine.api.datastore.KeyFactory
import com.ante.wavelet.StaticWaveletTransform
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import com.google.appengine.api.taskqueue.QueueFactory
import com.ante.gae_interface.TaskQueueWrapper
import com.ante.gae_interface.TaskQueueHandler
import com.ante.soundcloud_interface.SoundcloudUser
import com.google.appengine.api.datastore.Query
import scala.collection.JavaConversions
import com.google.appengine.api.datastore.PreparedQuery
import com.google.api.client.googleapis.extensions.appengine.auth.oauth2.AppIdentityCredential
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.services.bigquery.Bigquery
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.bigquery.model.Dataset
import com.google.api.services.bigquery.model.TableReference
import com.google.api.services.bigquery.model.TableFieldSchema
import com.google.api.services.bigquery.model.TableSchema
import com.google.api.services.bigquery.model.Table
import scala.collection.JavaConverters
import com.google.api.services.bigquery.model.TableDataInsertAllRequest
import com.google.api.services.bigquery.model.TableRow
import com.ante.gae_interface.BigqueryWrapper
import com.ante.gae_interface.AppEngineWrapper
import java.util.logging.Level
import com.ante.soundcloud_interface.SoundcloudWebsiteGetter
import com.ante.soundcloud_interface.SoundcloudUrls
import com.google.appengine.api.taskqueue.TaskOptions
import com.ante.soundcloud_interface.SoundcloudTrack
import com.ante.wavelet.StaticWaveletTransform.WaveletSource
import com.ante.wavelet.WaveletFilter

class TrackWavelet extends TaskQueueHandler
{
  import TrackWavelet.snapshot_to_wavelet_input
  
  def get_stats_from_appengine(datastore : DatastoreService, track_id : Int, iteration : Int) : Array[TrackSnapshot] =
  {
    TrackSnapshot.getStatisticsForTrack(datastore, track_id)
  }
  	
  def runTask(req : HttpServletRequest, task_name : String)
  {
	val datastore = DatastoreWrapper.connect_to_google_database()
    val bigquery = BigqueryWrapper.connect(BigqueryWrapper.authorizeFromAppEngine)
	val (id, iteration) = TaskQueueWrapper.userIdAndIterationForTask(req)
	val method = StaticWaveletTransform.default_filter
	val wavelet = TrackWavelet.compute_wavelets(TrackSnapshot.getStatisticsForTrack(datastore, id), id, iteration, method)
	if (wavelet.isEmpty)
	  return
    val row = TrackWavelet.get_bigquery_row(id, iteration, method.name, wavelet)
    TrackWavelet.store_in_bigquery(bigquery, row :: Nil)
  }
  def onRepeat(req : HttpServletRequest, task_name : String)
  {
    val datastore = DatastoreWrapper.connect_to_google_database()
	val (id, iteration) = TaskQueueWrapper.userIdAndIterationForTask(req)
  	datastore.put(new Entity(KeyFactory.createKey("failed_track_wavelets", (id.toLong << 32) | iteration.toLong)))
  }
}

object UserInfoWavelet
{
    implicit def snapshot_to_wavelet_input(snapshot : UserStatisticsSnapshot) : StaticWaveletTransform.WaveletSource =
      StaticWaveletTransform.WaveletSource(snapshot.iteration, snapshot.playback_count)
  	
	val logger : java.util.logging.Logger = java.util.logging.Logger.getLogger(UserInfoWavelet.getClass.getName);

  def getQueue() =
    QueueFactory.getQueue("statistics-wavelet")
  def getQueueRelative() =
    QueueFactory.getQueue("statistics-relative-wavelet")
    
    def entityForWavelet(name : String, iteration : Int, user_id : Int, wavelet : Array[Float]) : Entity =
    {
  	  val entity = new Entity(KeyFactory.createKey(name, (user_id.toLong << 32) | iteration))
  	  entity.setProperty("iteration", iteration.toLong)
  	  for (i <- 0 until wavelet.length)
  	    entity.setProperty("playback_count" + i.toString, wavelet(i).toDouble)
  	  entity
    }
  
  val dataset_id = "wavelets"
  val table_id = "playcount"
  val table_id_relative = "playcount_relative"
    
  def wavelet_fields() : List[TableFieldSchema] =
  {
    new TableFieldSchema().setName("iteration").setType("INTEGER") ::
	new TableFieldSchema().setName("method").setType("STRING") ::
	new TableFieldSchema().setName("playcount_abs").setType("INTEGER") ::
	(for (i <- 0 until 10)
	  yield new TableFieldSchema().setName("playcount" + i.toString).setType("FLOAT")).toList
  }
    
  def getTable(dataset_id : String, table_id : String) : Table =
  {
	  val reference = new TableReference().setProjectId(AppEngineWrapper.project).setDatasetId(dataset_id).setTableId(table_id)
	  val fields = new TableFieldSchema().setName("user_id").setType("INTEGER") :: wavelet_fields()
		  			
	  import JavaConverters._
	  val schema = new TableSchema().setFields(fields.asJava)
	  new Table().setTableReference(reference).setSchema(schema)
  }
  
  def createDefaultTables(bigquery : Bigquery)
  {
    BigqueryWrapper.createTable(AppEngineWrapper.project, dataset_id, getTable(dataset_id, table_id), bigquery)
    BigqueryWrapper.createTable(AppEngineWrapper.project, dataset_id, getTable(dataset_id, table_id_relative), bigquery)
  }
  
  def store_in_bigquery(user_id : Int, iteration : Int, wavelet : Array[Float], table : String)
  {
    val bigquery = BigqueryWrapper.connect(BigqueryWrapper.authorizeFromAppEngine)
	
	val row = new TableRow().
		set("user_id", user_id).
	  	set("iteration", iteration)
	for (i <- 0 until wavelet.length)
	  row.set("playcount" + i.toString, wavelet(i))
	

	val response = BigqueryWrapper.insert(new TableDataInsertAllRequest.Rows().setJson(row) :: Nil, dataset_id, table, bigquery)
	if (response.getInsertErrors() != null && response.getInsertErrors().size() != 0)
	{
	  import JavaConverters._
	  for (error <- response.getInsertErrors().asScala)
	  {
	    for (error_b <- error.getErrors().asScala)
	    {
	      println(error_b.getMessage())
	    }
	  }
	}
  }
}

object TrackWavelet
{
  implicit def snapshot_to_wavelet_input(snapshot : TrackSnapshot) : StaticWaveletTransform.WaveletSource =
    StaticWaveletTransform.WaveletSource(snapshot.iteration, snapshot.playback_count)
  def compute_wavelets(stats : Array[TrackSnapshot], track_id : Int, iteration : Int, method : WaveletFilter) : Array[Float] =
  {
  	compute_wavelets(stats map snapshot_to_wavelet_input, track_id, iteration, method)
  }
  def compute_wavelets(stats : Array[WaveletSource], track_id : Int, iteration : Int, method : WaveletFilter) : Array[Float] =
  {
  	if (stats.isEmpty)
  	  return Array[Float]()
    val num_levels = (math.log(TrackWavelet.getNumIterations(stats)) / math.log(2)).toInt
    if (num_levels <= 0)
      return Array[Float]()
    StaticWaveletTransform.compute_wavelets(stats, iteration, num_levels, method)
  }
  	
	val logger : java.util.logging.Logger = java.util.logging.Logger.getLogger(UserInfoWavelet.getClass.getName);

  val queue_name = "track-wavelet"
  def getQueue() =
    QueueFactory.getQueue(queue_name)
    
  def buildTaskOptions(track_id : Int, iteration : Int) : TaskOptions =
  {
    TaskQueueWrapper.taskForUser(track_id, iteration).method(TaskOptions.Method.PULL)
  }
  
  def enqueueTracks(tracks : Iterable[SoundcloudTrack], iteration : Int)
  {
	def build_task(track : SoundcloudTrack) =
	  buildTaskOptions(track.id, iteration)
	TaskQueueWrapper.addToQueue(getQueue, tracks map build_task)
  }
  def enqueueTracksWithPlaycounts(tracks : Iterable[(Int, Map[Int, Long])], iteration : Int)
  {
	def build_task(track : (Int, Map[Int, Long])) =
	  buildTaskOptions(track._1, iteration).param("playcounts", track._2.mkString(","))
	TaskQueueWrapper.addToQueue(getQueue, tracks map build_task)
  }
  
  val dataset_id = "wavelets"
  val table_id = "track_playcount"
    
  def getTable(dataset_id : String, table_id : String) : Table =
  {
	val reference = new TableReference().setProjectId(AppEngineWrapper.project).setDatasetId(dataset_id).setTableId(table_id)
    val fields = new TableFieldSchema().setName("track_id").setType("INTEGER") :: UserInfoWavelet.wavelet_fields()
	import JavaConverters._
	new Table().setTableReference(reference).setSchema(new TableSchema().setFields(fields.asJava))
  }
  
  def createDefaultTables(bigquery : Bigquery)
  {
    BigqueryWrapper.createTable(AppEngineWrapper.project, dataset_id, getTable(dataset_id, table_id), bigquery)
  }
  
  def updateDefaultTable(bigquery : Bigquery)
  {
    bigquery.tables().patch(AppEngineWrapper.project, dataset_id, table_id, getTable(dataset_id, table_id)).execute()
  }
  
  def get_bigquery_row(track_id : Int, iteration : Int, method : String, wavelet: Array[Float]) : TableDataInsertAllRequest.Rows =
  {
	val row = new TableRow().
		set("track_id", track_id).
	  	set("iteration", iteration).
	  	set("method", method)
	for (i <- 0 until wavelet.length)
	  row.set("playcount" + i.toString, wavelet(i))
	new TableDataInsertAllRequest.Rows().setJson(row)
  }
  
  def store_in_bigquery(bigquery : Bigquery, rows : Seq[TableDataInsertAllRequest.Rows])
  {
	val response = BigqueryWrapper.insert(rows, dataset_id, table_id, bigquery)
	if (response.getInsertErrors() != null && response.getInsertErrors().size() != 0)
	{
	  import JavaConverters._
	  for (error <- response.getInsertErrors().asScala)
	  {
	    for (error_b <- error.getErrors().asScala)
	    {
	      logger.log(Level.SEVERE, error_b.getMessage())
	    }
	  }
	  throw new Exception("Failed to insert, see the log for error messages")
	}
  }
  
  def getNumIterations(stats : Array[WaveletSource]) : Int =
  {
    stats.maxBy(_.iteration).iteration - stats.minBy(_.iteration).iteration
  }
}

class ComputeAllWavelets extends HttpServlet
{
  override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
  {
    val bigquery = BigqueryWrapper.connect(BigqueryWrapper.authorizeFromAppEngine)
    UserInfoWavelet.createDefaultTables(bigquery)
    TrackWavelet.createDefaultTables(bigquery)
    
    val yesterdays_iteration = AnteInfo.get_current_iteration - 1
    val datastore = DatastoreWrapper.connect_to_google_database()
    val tasks = for (id <- PeopleToTrack.get_people_to_track(datastore))
      yield TaskQueueWrapper.taskForUser(id, yesterdays_iteration)
    //TaskQueueWrapper.addToQueue(UserInfoWavelet.getQueue, tasks)
    //TaskQueueWrapper.addToQueue(UserInfoWavelet.getQueueRelative, tasks)
    TaskQueueWrapper.addToQueue(AddToTrackWavelet.getQueue, tasks)
  }
}

class ConvertTrackPlaycounts extends TaskQueueHandler
{
  def get_id(req : HttpServletRequest) : Int =
  {
	req.getParameter("track_id").toInt
  }
  override def runTask(req : HttpServletRequest, task_name : String)
  {
	val datastore = DatastoreWrapper.connect_to_google_database()
	val id = get_id(req)
	val snapshots = TrackSnapshot.getStatisticsForTrack(datastore, id)
	val entity = SoundcloudGetter.getPlaycountEntity(datastore, id)
	snapshots map (SoundcloudGetter.addPlaycount(entity, _))
	DatastoreWrapper.put(datastore, entity :: Nil)
  }
  override def onRepeat(req : HttpServletRequest, task_name : String)
  {
    val datastore = DatastoreWrapper.connect_to_google_database()
	val id = get_id(req)
  	datastore.put(new Entity(KeyFactory.createKey("failed_convert_track_playcount", id)))
  }
}

object ConvertTrackPlaycounts
{
  def getQueue() =
    QueueFactory.getQueue("convert-track-playcounts")
}

class AddToTrackWavelet extends TaskQueueHandler
{
  override def runTask(req : HttpServletRequest, task_name : String)
  {
	val datastore = DatastoreWrapper.connect_to_google_database()
	val (id, iteration) = TaskQueueWrapper.userIdAndIterationForTask(req)
	val user_and_tracks = SoundcloudWebsiteGetter.getAllTracksForUser(SoundcloudUrls.get_tracks_url, _.track_count)(id)
	user_and_tracks match
	{
	  case Some((user, tracks)) =>
	  {
	    TrackWavelet.enqueueTracks(tracks, iteration)
	    
	    // used this once to convert track playcounts
	    /*def build_task(track : SoundcloudTrack) =
	      TaskOptions.Builder.withParam("track_id", track.id.toString)
		TaskQueueWrapper.addToQueue(ConvertTrackPlaycounts.getQueue, tracks map build_task)*/
	  }
	  case None => SoundcloudUser.store_user_deleted(id, datastore)
	}
  }
  override def onRepeat(req : HttpServletRequest, task_name : String)
  {
    val datastore = DatastoreWrapper.connect_to_google_database()
	val (id, iteration) = TaskQueueWrapper.userIdAndIterationForTask(req)
	datastore.put(new Entity(KeyFactory.createKey("failed_add_to_track_wavelets", (id.toLong << 32) | iteration.toLong)))
  }
}

object AddToTrackWavelet
{
  
  def getQueue() =
    QueueFactory.getQueue("add-to-track-wavelet")
}

class DeleteAllWavelets extends HttpServlet
{
	override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
	{
	    val iteration = req.getParameter("iteration").toInt
		val datastore = DatastoreWrapper.connect_to_google_database()
		def getAll(entity_type : String) =
		  datastore.prepare(new Query(entity_type).addFilter("iteration", Query.FilterOperator.EQUAL, iteration))
		def deleteAll(query : PreparedQuery)
	    {
			val all_keys = JavaConversions.asScalaIterator(query.asIterator()) map ((entity : Entity) => entity.getKey())
			val as_list = all_keys.toList
			if (as_list.length > 10000) // just to prevent me from accidentally deleting everything
			  return					// this function is only for debugging offline
			datastore.delete(JavaConversions.asJavaIterable(as_list))
	    }
		deleteAll(getAll("statistics_wavelet"))
		deleteAll(getAll("statistics_wavelet_relative"))
	    resp.setContentType("text/plain")
	    resp.getWriter().println("Deleted all Wavelets")
	}
}

class GetTimeSeries extends HttpServlet
{
  override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
  {
    val id_param = req.getParameter("id")
    val id = SoundcloudUser.idFromParameter(id_param)
    val id_and_iteration = id_param.toLong
    val iteration = if (id_and_iteration > (2.toLong << 32))
        id_and_iteration.toInt
      else
        AnteInfo.get_current_iteration - 1
    val datastore = DatastoreWrapper.connect_to_google_database()
    val stats = SoundcloudGetter.getStatisticsForUser(datastore, id)
    
    val as_32_values = StaticWaveletTransform.build_array(stats map UserInfoWavelet.snapshot_to_wavelet_input, iteration, 32)
    
    resp.setContentType("application/json")
    resp.getWriter().print("[" + as_32_values.mkString(", ") + "]")
  }
}

class GetTrackTimeSeries extends HttpServlet
{
  override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
  {
    val id_param = req.getParameter("id")
    val id = SoundcloudUser.idFromParameter(id_param)
    val id_and_iteration = id_param.toLong
    val iteration = if (id_and_iteration > (2.toLong << 32))
        id_and_iteration.toInt
      else
        AnteInfo.get_current_iteration - 1
    val datastore = DatastoreWrapper.connect_to_google_database()
    val stats = TrackSnapshot.getStatisticsForTrack(datastore, id)
    
    val playcounts = TrackSnapshot.get_playcounts_for_track(datastore, id).toArray.sortBy(_._1)
    
    val num_iterations = TrackWavelet.getNumIterations(stats map TrackWavelet.snapshot_to_wavelet_input)
    
    val filled = StaticWaveletTransform.build_array(stats map TrackWavelet.snapshot_to_wavelet_input, iteration, num_iterations)
    
    resp.setContentType("application/json")
    resp.getWriter().print("{\n\"playcounts\" : [" + filled.mkString(", ") + "],\n\n \"placounts_second\" : [" + playcounts.mkString(", ") + "],\n\n \"log_playcount\" : [" + (filled map (Math.log(_))).mkString(", ") + "]\n\n \"stats\" : [" + stats.mkString(", ") + "],\n \"iteration\" : " + iteration.toString + "\n}")
  }
}
