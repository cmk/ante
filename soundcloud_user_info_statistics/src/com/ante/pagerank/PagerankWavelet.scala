package com.ante.pagerank

import javax.servlet.http.HttpServlet
import com.ante.user_info_crawler.AnteInfo
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import com.google.appengine.api.datastore.DatastoreServiceFactory
import com.google.appengine.api.datastore.Query
import com.google.appengine.api.datastore.Entity
import net.liftweb.json
import net.liftweb.json.JsonAST.JArray
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.JsonAST.JDouble
import net.liftweb.json.JsonAST.JInt
import org.scalatest._
import com.google.appengine.api.datastore.KeyFactory
import com.google.appengine.api.datastore.DatastoreService
import com.ante.gae_interface.DatastoreWrapper
import com.google.appengine.api.taskqueue.QueueFactory
import com.google.appengine.api.taskqueue.TaskOptions
import com.ante.gae_interface.TaskQueueWrapper
import com.google.appengine.api.datastore.FetchOptions
import com.google.appengine.api.datastore.Cursor
import com.ante.wavelet.StaticWaveletTransform
import com.google.appengine.api.taskqueue.TaskAlreadyExistsException
import java.util.logging.Level
import com.google.appengine.api.datastore.PreparedQuery
import scala.collection.JavaConversions

case class PagerankSnapshot(id : Int, iteration : Int, pagerank : Float)

class PagerankWavelet extends HttpServlet
{
  	def get_pageranks_for_user(datastore : DatastoreService, user_id : Int) : Array[PagerankSnapshot] =
  	{
  	  val entities = DatastoreWrapper.getEntitiesForUser(datastore, user_id, "pagerank")
  	  (entities map PagerankWavelet.entity_to_snapshot).toArray
  	}
 
  	/*def entity_for_wavelet(iteration : Int, user_id : Int, wavelet : Array[Float]) : Entity =
  	{
  	  val entity = new Entity(KeyFactory.createKey("pagerank_wavelet", (user_id.toLong << 32) | iteration))
  	  entity.setProperty("iteration", iteration.toLong)
  	  for (i <- 0 until wavelet.length)
  	    entity.setProperty(i.toString, wavelet(i).toDouble)
  	  entity
  	}*/
  	def entity_for_relative_wavelet(iteration : Int, user_id : Int, wavelet : Array[Float]) : Entity =
  	{
  	  val entity = new Entity(KeyFactory.createKey("pagerank_relative_wavelet", (user_id.toLong << 32) | iteration))
  	  entity.setProperty("iteration", iteration.toLong)
  	  for (i <- 0 until wavelet.length)
  	    entity.setProperty(i.toString, wavelet(i).toDouble)
  	  entity
  	}
  	
    private implicit def snapshot_to_wavelet_input(snapshot : PagerankSnapshot) : StaticWaveletTransform.WaveletSource =
      StaticWaveletTransform.WaveletSource(snapshot.iteration, snapshot.pagerank)
  	
  	/*def compute_and_store_wavelet(datastore : DatastoreService, user_id : Int, iteration : Int)
  	{
  	  val pageranks = get_pageranks_for_user(datastore, user_id)
  	  val wavelet = StaticWaveletTransform.compute_wavelets(pageranks, iteration)
      datastore.put(entity_for_wavelet(iteration, user_id, wavelet))
  	}*/
  	def compute_and_store_relative_wavelet(datastore : DatastoreService, user_id : Int, iteration : Int)
  	{
  	  val pageranks = get_pageranks_for_user(datastore, user_id)
  	  val wavelet = StaticWaveletTransform.compute_wavelets(pageranks, iteration, 5, StaticWaveletTransform.default_filter)
      datastore.put(entity_for_relative_wavelet(iteration, user_id, wavelet))
  	}
  	
  	def storeWaveletFailed(id : Int, iteration : Int, datastore : DatastoreService)
  	{
  	  datastore.put(new Entity(KeyFactory.createKey("failed_pagerank_wavelets", (id.toLong << 32l) | iteration.toLong)))
  	}
  	
  override def doPost(req : HttpServletRequest, resp : HttpServletResponse)
  {
	  val datastore = DatastoreWrapper.connect_to_google_database()
	  val retries : Int = req.getHeader("X-AppEngine-TaskRetryCount").toInt
	  val (id, iteration) = TaskQueueWrapper.userIdAndIterationForTask(req)
	  if (retries > 0)
	  {
	    storeWaveletFailed(id, iteration, datastore)
	    return
	  }
	  try
	  {
		//compute_and_store_wavelet(datastore, id, iteration)
	    compute_and_store_relative_wavelet(datastore, id, iteration)
	  }
	  catch
	  {
	    case ex : Throwable =>
	    {
	      PagerankWavelet.logger.log(java.util.logging.Level.SEVERE, "Error while trying to compute the wavelet for " + id.toString + ", iteration " + iteration.toString)
	      throw ex
	    }
	  }
  }
}

object PagerankWavelet
{
  private val logger : java.util.logging.Logger = java.util.logging.Logger.getLogger(PagerankWavelet.getClass.getName);
  
	def entity_to_snapshot(entity : Entity) =
	  PagerankSnapshot(
		      (entity.getKey().getId() >> 32).toInt,
		      entity.getProperty("iteration").asInstanceOf[Long].toInt,
		      entity.getProperty("pagerank").asInstanceOf[Double].toFloat)
	
  def getQueue() =
    QueueFactory.getQueue("pagerank-wavelet")
}

class ComputeAllWavelets extends HttpServlet
{
  override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
  {
    val yesterdays_iteration = AnteInfo.get_current_iteration - 1
    QueueWavelets.queue_wavelets(yesterdays_iteration, null)
  }
}

object QueueWavelets
{
	val logger : java.util.logging.Logger = java.util.logging.Logger.getLogger(QueueWavelets.getClass.getName);
	private def get_pageranks_for_iteration(iteration : Int, cursor : String) : (Array[PagerankSnapshot], String) =
	{
		val datastore = DatastoreWrapper.connect_to_google_database()
		val query = new Query("pagerank")
		query.addFilter("iteration", Query.FilterOperator.EQUAL, iteration)
		val fetch_options = FetchOptions.Builder.withLimit(1000)
		if (cursor != null)
		{
		  fetch_options.startCursor(Cursor.fromWebSafeString(cursor))
		}
		val results = datastore.prepare(query).asQueryResultList(fetch_options)
		import scala.collection.JavaConverters._
		val snapshots = for (result : Entity <- results.asScala)
		  yield PagerankWavelet.entity_to_snapshot(result)
		
		(snapshots.toArray, results.getCursor().toWebSafeString())
	}
	
	  def queue_wavelets(iteration : Int, cursor : String) =
	  {
	    val (pageranks, next_cursor) = get_pageranks_for_iteration(iteration, cursor)
	    val tasks = for (snapshot <- pageranks)
	      yield TaskQueueWrapper.taskForUser(snapshot.id, iteration)
	    try
	    {
	      TaskQueueWrapper.addToQueue(PagerankWavelet.getQueue, tasks)
	      logger.info("Started tasks for IDs" + (pageranks map (_.id)).mkString(", "))
	    }
	    catch
	    {
	      case e : TaskAlreadyExistsException =>
	      {
	    	import scala.collection.JavaConverters._
	        logger.log(Level.SEVERE, "Already had a few tasks. Curious:\n" + e.getTaskNames().asScala.mkString(", "), e)
	      	// print and ignore the error
	      	// not sure why I get this every now and then. the cursor
	      	// should take care of this. but it doesn't 
	      }
	    }
	    if (tasks.length != 0)
	    {
	      queue_next(iteration, next_cursor)
	    }
	  }
	  private def queue_next(iteration : Int, cursor : String)
	  {
	    val task = TaskOptions.Builder.withParam("iteration", iteration.toString).param("cursor", cursor)
	    TaskQueueWrapper.addToQueue(getQueue, List(task))
	  }
	private def getQueue() =
		QueueFactory.getQueue("pagerank-wavelet-starter")
}

class ComputeNextWavelet extends HttpServlet
{
  	def storeWaveletStarterFailed(iteration : Int, cursor : String, datastore : DatastoreService)
  	{
  	  val entity = new Entity(KeyFactory.createKey("failed_pagerank_wavelet_starter", cursor))
  	  entity.setUnindexedProperty("iteration", iteration)
  	  datastore.put(entity)
  	}
  override def doPost(req : HttpServletRequest, resp : HttpServletResponse)
  {
	  val datastore = DatastoreWrapper.connect_to_google_database()
	  val iteration : Int = req.getParameter("iteration").toInt
	  val cursor : String = req.getParameter("cursor")
	  val retries : Int = req.getHeader("X-AppEngine-TaskRetryCount").toInt
	  if (retries > 0)
	  {
	    storeWaveletStarterFailed(iteration, cursor, datastore)
	    return
	  }
	  try
	  {
		  QueueWavelets.queue_wavelets(iteration, cursor)
	  }
	  catch
	  {
	    case ex : Throwable =>
	    {
	      QueueWavelets.logger.log(java.util.logging.Level.SEVERE, "Error while trying to start the wavelet computation for iteration " + iteration.toString)
	      throw ex
	    }
	  }
  }
}

class PopulatePageranks extends HttpServlet
{
  case class PagerankSnapshotWithShiftedID(id : Long, iteration : Int, pagerank : Float)
  val pageranks =
    Array(
        PagerankSnapshotWithShiftedID(4294985924l, 	18628, 	4.40827989223e-06f),
		PagerankSnapshotWithShiftedID(4294985929l, 	18633, 	6.56639986119e-06f),
		PagerankSnapshotWithShiftedID(4294985930l, 	18634, 	7.21852984498e-06f),
		PagerankSnapshotWithShiftedID(4294985932l, 	18636, 	8.15709972812e-06f),
		PagerankSnapshotWithShiftedID(4294985933l, 	18637, 	8.75891964824e-06f),
		PagerankSnapshotWithShiftedID(4294985934l, 	18638, 	9.28848021431e-06f),
		PagerankSnapshotWithShiftedID(4294985937l, 	18641, 	1.08837002699e-05f),
		PagerankSnapshotWithShiftedID(4294985938l, 	18642, 	1.13223004519e-05f),
		PagerankSnapshotWithShiftedID(4294985939l, 	18643, 	1.18180996651e-05f),
		PagerankSnapshotWithShiftedID(4294985940l, 	18644, 	1.27886996779e-05f),
		PagerankSnapshotWithShiftedID(4294985941l, 	18645, 	1.3532399862e-05f),
		PagerankSnapshotWithShiftedID(8589953220l, 	18628, 	1.62929991347e-05f),
		PagerankSnapshotWithShiftedID(8589953225l, 	18633, 	2.51451001532e-05f),
		PagerankSnapshotWithShiftedID(8589953226l, 	18634, 	2.69619995379e-05f),
		PagerankSnapshotWithShiftedID(8589953228l, 	18636, 	3.19717000821e-05f),
		PagerankSnapshotWithShiftedID(8589953229l, 	18637, 	3.47899003827e-05f),
		PagerankSnapshotWithShiftedID(8589953230l, 	18638, 	3.69982990378e-05f),
		PagerankSnapshotWithShiftedID(8589953231l, 	18639, 	3.95124989154e-05f),
		PagerankSnapshotWithShiftedID(8589953233l, 	18641, 	4.38304996351e-05f),
		PagerankSnapshotWithShiftedID(8589953234l, 	18642, 	4.77565990877e-05f),
		PagerankSnapshotWithShiftedID(8589953235l, 	18643, 	4.98941990372e-05f),
		PagerankSnapshotWithShiftedID(8589953236l, 	18644, 	5.49869000679e-05f),
		PagerankSnapshotWithShiftedID(8589953237l, 	18645, 	5.89588998992e-05f)
		    )
    
	override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
	{
		val datastore = DatastoreServiceFactory.getDatastoreService()
	    for (pagerank <- pageranks)
	    {
	      val entity = new Entity(KeyFactory.createKey("pagerank", pagerank.id))
	      entity.setProperty("iteration", pagerank.iteration)
	      entity.setUnindexedProperty("pagerank", pagerank.pagerank)
	      datastore.put(entity)
	    }
	}
  
}

class DeleteAllPagerankWavelets extends HttpServlet
{
	override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
	{
	    val iteration = req.getParameter("iteration").toInt
		val datastore = DatastoreWrapper.connect_to_google_database()
		def getAll(entity_type : String) =
		  datastore.prepare(new Query(entity_type).addFilter("iteration", Query.FilterOperator.EQUAL, iteration))
		def deleteAll(query : PreparedQuery)
	    {
			val all_keys = JavaConversions.asScalaIterator(query.asIterator(FetchOptions.Builder.withLimit(20000))) map ((entity : Entity) => entity.getKey())
			val as_list = all_keys.toList
			//if (as_list.length > 10000) // just to prevent me from accidentally deleting everything
			//  return					// this function is only for debugging offline
			datastore.delete(JavaConversions.asJavaIterable(as_list))
	    }
		deleteAll(getAll("pagerank_wavelet"))
		deleteAll(getAll("pagerank_relative_wavelet"))
	    resp.setContentType("text/plain")
	    resp.getWriter().println("Deleted all Wavelets")
	}
}