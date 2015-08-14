package com.ante.gae_interface

import com.google.appengine.api.taskqueue.Queue
import com.google.appengine.api.taskqueue.TaskOptions
import com.google.appengine.api.taskqueue.TaskAlreadyExistsException
import scala.collection.JavaConversions
import com.google.appengine.api.taskqueue.TransientFailureException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletResponse

abstract class TaskQueueHandler extends HttpServlet
{
  val logger : java.util.logging.Logger = java.util.logging.Logger.getLogger(getClass.getName)
  
  override def doPost(req : HttpServletRequest, resp : HttpServletResponse)
  {
	  val task_name : String = req.getHeader("X-AppEngine-TaskName")
	  val retries : Int = req.getHeader("X-AppEngine-TaskRetryCount").toInt
	  if (retries > 0)
	  {
	    onRepeat(req, task_name)
	    return
	  }
	  try
	  {
	    runTask(req, task_name)
	  }
	  catch
	  {
	    case ex : Throwable =>
	    {
	      logger.log(java.util.logging.Level.SEVERE, "Error for task " + task_name)
	      throw ex
	    }
	  }
  }
  
  def onRepeat(req : HttpServletRequest, task_name : String)
  def runTask(req : HttpServletRequest, task_name : String)
}

object TaskQueueWrapper
{
  def addToQueue(task_queue : Queue, tasks : Iterable[TaskOptions])
  {
    // can add only up to 100 tasks at a time
    val max_tasks_addable = 100
    var already_exists : TaskAlreadyExistsException = null
    for (list <- tasks.grouped(max_tasks_addable))
    {
      def add_to_queue() { task_queue.add(JavaConversions.asJavaIterable(list)) }
      try
      {
        try
        {
        	add_to_queue()
        }
        catch
        {
          // yeah this is annoying. this is documented as
          // "Intermittent failure. The requested operation may succeed if attempted again."
          // so I try it two more times...
          case _ : TransientFailureException =>
          {
            try
            {
              add_to_queue()
            }
            catch
            {
              case _ : TransientFailureException => add_to_queue()
            }
          }
        }
      }
      catch
      {
        // Queue.add() promises that if this exception gets thrown
        // all tasks that could be added will be added. since I add
        // tasks in batches of 100, I have to make sure that I do
        // not change that accidentally. for example if the first
        // 100 throw, then the second 100 would never get run.
        // to fix that I remember the exception and will throw it later
        case ex : TaskAlreadyExistsException => already_exists = ex
      }
    }
    if (already_exists != null) throw already_exists
  }
  
  
  def taskForUser(user_id : Int, iteration : Int) : TaskOptions =
  {
    //TaskOptions.Builder.withTaskName(((user_id.toLong << 32) | iteration.toLong).toString)
    TaskOptions.Builder.withParam("user_id", user_id.toString).param("iteration", iteration.toString)
  }
  def userIdAndIterationForTask(req : HttpServletRequest) : (Int, Int) =
  {
    //val id_and_iteration = req.getHeader("X-AppEngine-TaskName").toLong
	//((id_and_iteration >> 32).toInt, id_and_iteration.toInt)
    (req.getParameter("user_id").toInt, req.getParameter("iteration").toInt)
  }
}
