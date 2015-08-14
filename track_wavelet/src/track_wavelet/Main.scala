package track_wavelet

import com.ante.gae_interface.BigqueryWrapper
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.services.taskqueue.Taskqueue
import com.google.api.client.json.jackson2.JacksonFactory
import com.ante.gae_interface.AppEngineWrapper
import com.google.api.services.taskqueue.TaskqueueRequestInitializer
import com.google.api.services.taskqueue.model.TaskQueue
import com.google.api.services.taskqueue.model.Task
import scala.collection.JavaConversions
import scala.collection.JavaConverters
import com.google.api.services.datastore.client.Datastore
import com.google.api.services.datastore.client.DatastoreFactory
import com.google.api.services.datastore.client.DatastoreHelper
import com.google.api.services.datastore.client.DatastoreOptions
import com.ante.gae_interface.DatastoreWrapper
import com.ante.user_info_crawler.TrackSnapshot
import com.google.api.services.bigquery.Bigquery
import scala.collection.mutable.SynchronizedQueue
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import com.google.api.services.datastore.DatastoreV1.KindExpression
import com.google.api.services.datastore.DatastoreV1.PropertyReference
import com.google.api.services.datastore.DatastoreV1.Value
import com.google.api.services.datastore.DatastoreV1.Key
import com.google.api.services.datastore.DatastoreV1.PropertyFilter
import com.google.api.services.datastore.DatastoreV1.Filter
import com.google.api.services.datastore.DatastoreV1.LookupRequest
import com.ante.wavelet.Derivatives
import com.ante.wavelet.StaticWaveletTransform
import com.ante.user_info_crawler.TrackWavelet
import com.ante.hot_tracks.FindHotStuff
import com.google.api.services.datastore.DatastoreV1.CommitRequest
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ArrayBlockingQueue

case class ThreadedProfiler(to_increment : AtomicLong)
{
  val start_time = System.nanoTime()
  def finish()
  {
    to_increment.addAndGet(System.nanoTime() - start_time)
  }
}

object Main
{
  
  val project_name = "s~" + AppEngineWrapper.project
  val taskqueue_name = TrackWavelet.queue_name
    
  def splitPayload(payload : String) : Map[String, String] =
  {
    val split = payload.split("&")
    val split_again = split map (_.split("="))
    def url_decode(str : String) : String =
      java.net.URLDecoder.decode(str, "UTF-8")
    (for (pair <- split_again)
      yield url_decode(pair(0)) -> url_decode(pair(1))).toMap
  }
  
  def taskParams(task : Task) : Map[String, String] =
  {
    val parsed = javax.xml.bind.DatatypeConverter.parseBase64Binary(task.getPayloadBase64())
    splitPayload(new String(parsed))
  }
    
  def userIdAndIterationForTask(task_params : Map[String, String]) : (Int, Int) =
  {
    (task_params("user_id").toInt, task_params("iteration").toInt)
  }
  
  def playcountsFromPrintedMap(printed_map : String) : Map[Int, Long] =
  {
    (for {entry <- printed_map.split(",")
        	pair = entry.split("->")
        	if (pair.length == 2)
        	id = pair(0).trim.toInt
        	playcount = pair(1).trim.toLong}
        		yield (id -> playcount)).toMap
  }
  
  def playcountsFromTask(task_params : Map[String, String]) : Option[Map[Int, Long]] =
  {
    task_params.get("playcounts") match
    {
      case Some(playcounts) => Some(playcountsFromPrintedMap(playcounts))
      case None => None
    }
  }
  
  class LocalTaskQueue(val queue : BlockingQueue[Option[Task]])
  {
    def enqueue(task : Task)
    {
      queue.put(Some(task))
    }
    def shutdown()
    {
      queue.put(None)
    }
    def isEmpty() : Boolean =
    {
      val polled = queue.peek()
      return polled == null || (polled match
      {
        case Some(task) => false
        case None => true
      })
    }
    def dequeue() : Option[Task] =
    {
      queue.take match
      {
        case Some(task) => Some(task)
        case None =>
        {
          queue.put(None)
          None
        }
      }
    }
  }
  
  class TaskRunner(credential : GoogleCredential, queue : LocalTaskQueue) extends Runnable
  {
    override def run()
    {
      val taskqueue : Taskqueue = new Taskqueue.Builder(AppEngineWrapper.http_transport, AppEngineWrapper.json_factory, credential)
        .setApplicationName(project_name).build();
      val datastore : Datastore = DatastoreWrapper.connect_from_outside()
      val bigquery = BigqueryWrapper.connect(BigqueryWrapper.authorizeFromOutside)
      while (true)
      {
        queue.dequeue() match
        {
          case Some(task) => run_one_task(datastore, taskqueue, task, bigquery)
          case None => return
        }
      }
    }
  }
  
  val count_tracks = new AtomicInteger(0)
  val time_for_snapshots = new AtomicLong(0)
  val time_for_wavelets = new AtomicLong(0)
  val time_for_bigquery = new AtomicLong(0)
  val time_for_finish = new AtomicLong(0)

  def run_one_task(datastore : Datastore, taskqueue : Taskqueue, task : Task, bigquery : Bigquery)
  {
    val task_params = taskParams(task)
    val (track_id, iteration) = userIdAndIterationForTask(task_params)
    def finish()
    {
      val profile_finish = ThreadedProfiler(time_for_finish)
      taskqueue.tasks().delete(project_name, taskqueue_name, task.getId()).execute()
      count_tracks.incrementAndGet()
      profile_finish.finish
    }
    val profile_snapshots = ThreadedProfiler(time_for_snapshots)
    val snapshots : Map[Int, Long] = playcountsFromTask(task_params) match
    {
      case Some(playcounts) => playcounts
      case None => TrackSnapshot.get_playcounts_for_track(datastore, track_id)
    }
    profile_snapshots.finish
    if (snapshots.isEmpty || !snapshots.contains(iteration))
    {
      finish()
      return
    }
    val profile_wavelets = ThreadedProfiler(time_for_wavelets)
    def to_wavelet_source(snapshot : (Int, Long)) =
      StaticWaveletTransform.WaveletSource(snapshot._1, snapshot._2.toFloat)
    val as_wavelet_source = (snapshots.toArray map to_wavelet_source).sortBy(_.iteration)
    val wavelets = for (filter <- StaticWaveletTransform.supported_filters)
      yield (filter.name, TrackWavelet.compute_wavelets(as_wavelet_source, track_id, iteration, filter))
    val as_log_wavelet_source = as_wavelet_source map (x => StaticWaveletTransform.WaveletSource(x.iteration, Math.log10(x.value).toFloat))
    val log_wavelets = for (filter <- StaticWaveletTransform.supported_filters)
      yield (filter.name + "_log", TrackWavelet.compute_wavelets(as_log_wavelet_source, track_id, iteration, filter))
    val derivatives_input = StaticWaveletTransform.build_array(as_wavelet_source, iteration, iteration - as_wavelet_source.head.iteration) map (_.toDouble)
    val log_derivatives_input = derivatives_input map Math.log10
    val derivatives = ("first_derivative", Derivatives.FirstDerivative(derivatives_input) map (_.toFloat)) ::
    	("first_derivative_diff", Derivatives.FirstDerivativeAdjusted(derivatives_input) map (_.toFloat)) ::
    	("second_derivative", Derivatives.SecondDerivative(derivatives_input) map (_.toFloat)) ::
    	("first_derivative_log", Derivatives.FirstDerivative(log_derivatives_input) map (_.toFloat)) ::
    	("first_derivative_diff_log", Derivatives.FirstDerivativeAdjusted(log_derivatives_input) map (_.toFloat)) ::
    	("second_derivative_log", Derivatives.SecondDerivative(log_derivatives_input) map (_.toFloat)) ::
    	Nil
    profile_wavelets.finish
    val profile_bigquery = ThreadedProfiler(time_for_bigquery)
    val rows = for ((filter_name, wavelet) <- wavelets ++ log_wavelets ++ derivatives)
      yield TrackWavelet.get_bigquery_row(track_id, iteration, filter_name, wavelet)
    TrackWavelet.store_in_bigquery(bigquery, rows)
    profile_bigquery.finish
    finish()
  }
  
  class KeepManyThreads(val thread_generator : () => Runnable, val success_counter : AtomicInteger, val desired_threads : Int) extends Runnable
  {
    var is_running = true
    override def run()
    {
      import scala.collection.mutable.ArrayBuffer
	  val count = new AtomicInteger(0)
	  var threads : ArrayBuffer[Thread] = new ArrayBuffer[Thread]
	  while (is_running)
	  {
	    val new_threads = (for (i <- 0 until (desired_threads - count.get()))
          yield new Thread(new CountingRunner(thread_generator(), count)))
        new_threads map (_.start)
	    threads ++= new_threads
        val before = success_counter.get()
        Thread.sleep(60000)
        val after = success_counter.get()
        println((after - before).toString + " items per minute with " + desired_threads.toString + " threads")
        println("snapshots: " + (time_for_snapshots.get() / 1000000000.0).toString)
        println("wavelets: " + (time_for_wavelets.get() / 1000000000.0).toString)
        println("bigquery: " + (time_for_bigquery.get() / 1000000000.0).toString)
        println("finish: " + (time_for_finish.get() / 1000000000.0).toString)
        threads = threads filter (_.isAlive())
	  }
      threads map (_.join)
    }
    
    class CountingRunner(val wrapped : Runnable, val count : AtomicInteger) extends Runnable
    {
      override def run()
      {
        count.incrementAndGet()
        try
        {
          wrapped.run()
        }
        finally
        {
          count.decrementAndGet()
        }
      }
    }
  }
  
  def store_cool_stuff()
  {
    val bigquery = BigqueryWrapper.connect(BigqueryWrapper.authorizeFromOutside)
    val stuff = FindHotStuff.build_entity(bigquery, 18702)
    val datastore = DatastoreWrapper.connect_from_outside
    val store = CommitRequest.newBuilder()
    store.setMode(CommitRequest.Mode.NON_TRANSACTIONAL)
    store.getMutationBuilder().addUpsert(stuff)
    datastore.commit(store.build())
  }

  def main(args: Array[String])
  {
    AppEngineWrapper.is_outside_appengine = true
    
    //TrackWavelet.updateDefaultTable(BigqueryWrapper.connect(BigqueryWrapper.authorizeFromOutside))
    
    val credential = AppEngineWrapper.authorizeFromOutside("https://www.googleapis.com/auth/taskqueue" :: "https://www.googleapis.com/auth/taskqueue.consumer" :: Nil)
    
    //store_cool_stuff()

    val taskqueue : Taskqueue = new Taskqueue.Builder(AppEngineWrapper.http_transport, AppEngineWrapper.json_factory, credential)
    	.setApplicationName(project_name).build();
    
    val local_queue = new LocalTaskQueue(new LinkedBlockingQueue[Option[Task]])
    
    val desired_threads = 50
    def create_thread() : Runnable =
      new TaskRunner(credential, local_queue)
    
    val keep_threads = new KeepManyThreads(create_thread, count_tracks, desired_threads)
    val keep_threads_thread = new Thread(keep_threads)
    keep_threads_thread.start()
    
    val start_time = System.nanoTime()
    try
    {
      while (true)
      {
        val batch_size = 1000
	    val tasks = taskqueue.tasks().lease(project_name, taskqueue_name, batch_size, 600).execute()
	    if (tasks.getItems() == null || tasks.getItems().size() == 0)
	      return
	    import JavaConverters._
	    tasks.getItems().asScala map (local_queue.enqueue(_))
	    while (local_queue.queue.size() > batch_size / 2)
	    {
	      Thread.sleep(100)
	    }
      }
      while (!local_queue.isEmpty)
        Thread.sleep(100)
    }
    finally
    {
      local_queue.shutdown()
      keep_threads.is_running = false
      keep_threads_thread.join()
	  
	  val end_time = System.nanoTime()
	  println(((end_time - start_time) / 1000000).toString + "ms")
	  println(count_tracks.get().toString + " tracks")
    }
  }
}


class ParseTaskParamsSpec extends FlatSpec with Matchers
{
  "valid text" should "be parsed correctly" in
  {
      val parsed : Map[Int, Long] = Main.playcountsFromPrintedMap("1 -> 5,2 -> 6")
      parsed should be (Map[Int, Long](1 -> 5, 2 -> 6))
  }
  "an empty map" should "be parsed correctly" in
  {
      val parsed : Map[Int, Long] = Main.playcountsFromPrintedMap("")
      parsed should be (Map())
  }
}

