package com.ante.user_info_crawler

import com.google.appengine.api.taskqueue.Queue
import com.google.appengine.api.taskqueue.QueueFactory
import com.google.appengine.api.taskqueue.TaskOptions
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.logging.Logger
import com.ante.gae_interface.DatastoreWrapper

class CrawlStarter extends HttpServlet
{
  val log = Logger.getLogger("CrawlStarter");
  
  override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
  {
    val num_buckets = AnteInfo.num_hours_per_iteration
    val current_bucket = AnteInfo.get_current_bucket(num_buckets)
    val datastore = DatastoreWrapper.connect_to_google_database()
    val ids_to_scan = PeopleToTrack.get_people_to_track(datastore)
    for (id <- ids_to_scan if AnteInfo.get_bucket_for_id(id, num_buckets) == current_bucket)
      CrawlStarter.crawlUser(id)
  }
}

object CrawlStarter
{
	def crawlUser(id : Int)
	{
	  val queue = QueueFactory.getQueue("crawl-user-info")
	  queue.add(TaskOptions.Builder.withParam("id", id.toString))
	}
}
