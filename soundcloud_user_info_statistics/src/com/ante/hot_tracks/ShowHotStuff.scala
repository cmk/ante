package com.ante.hot_tracks

import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import com.ante.gae_interface.DatastoreWrapper
import com.google.appengine.api.datastore.KeyFactory
import com.ante.gae_interface.UrlFetcherWrapper
import com.ante.soundcloud_interface.SoundcloudUrls
import com.ante.soundcloud_interface.SoundcloudWebsiteGetter
import com.ante.soundcloud_interface.SoundcloudTrack
import net.liftweb.json.JsonAST.JString
import java.util.logging.Logger
import java.util.logging.Level
import net.liftweb.json.JsonAST.JValue
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import com.ante.user_info_crawler.TrackSnapshot
import com.google.appengine.api.datastore.DatastoreService
import com.google.appengine.api.datastore.Key
import com.google.appengine.api.datastore.Entity
import com.google.appengine.api.datastore.EntityNotFoundException
import com.google.appengine.api.memcache.MemcacheServiceFactory
import com.google.appengine.api.memcache.ErrorHandlers
import com.google.api.services.bigquery.Bigquery
import com.ante.gae_interface.BigqueryWrapper
import scala.util.matching.Regex
import com.ante.user_info_crawler.AnteInfo
import com.google.api.services.bigquery.model.QueryResponse
import com.google.api.client.googleapis.json.GoogleJsonResponseException

case class ShowTrackStep1(id : Long)
{
  val request = UrlFetcherWrapper.fetchWebsite(SoundcloudUrls.get_track_url(id))
}
case class ShowTrackStep2(step1 : ShowTrackStep1, iteration : Option[Int], datastore : DatastoreService, element_id : String, graph_width : Int, graph_height : Int)
{
  import step1._
  val playcount_map = TrackSnapshot.get_playcounts_for_track(datastore, id)
  val playcounts = TrackSnapshot.playcounts_to_array(playcount_map)
  val dotted_index = iteration match
  {
    case Some(iteration) => Some(iteration - playcount_map.minBy(_._1)._1)
    case None => None
  }
  val graph_html = ShowTrackStep2.graph_html(playcounts, element_id, graph_width, graph_height, dotted_index)
}
object ShowTrackStep2
{
  def graph_html(data : Iterable[Float], element_id : String, width : Int, height : Int, dotted_index : Option[Int]) : String =
  {
    val dotted_index_str = dotted_index match
    {
      case Some(index) => index.toString
      case None => "null"
    }

	val graph_javascript = "compact_linear_graph([" + data.mkString(", ") + "], \"" + element_id + "\", " + width.toString + ", " + height.toString + ", " + dotted_index_str + ");"
	
	"<svg class=\"chart\" id=\"" + element_id + "\"></svg>\n" +
		"<script type=\"text/javascript\">" + graph_javascript + "</script>"
  }
}
case class ShowTrackStep3(step2 : ShowTrackStep2, track : SoundcloudTrack)
{
  import step2._
  import step1._
}
object ShowTrackStep3
{
def show_track_step3(steps2 : Iterable[ShowTrackStep2]) : Iterable[Option[ShowTrackStep3]] =
{
  for {step <- steps2
	  response = SoundcloudWebsiteGetter.responseToTrack(step.step1.request)}
    yield response match
    {
      case Right(track) => Some(ShowTrackStep3(step, track))
      case Left(_) => None
    }
}
}
case class ShowTrackStep4(step3 : ShowTrackStep3, embed_width : Int, embed_height : Int)
{
  def get_oembed_url(track : SoundcloudTrack) : String =
	"https://soundcloud.com/oembed?format=json&url=" + track.permalink_url + "&maxwidth=" + embed_width.toString + "px" + "&maxheight=" + embed_height.toString + "px"
  import step3._
  import step2._
  import step1._
  
  val embed_request = UrlFetcherWrapper.fetchWebsite(get_oembed_url(track))
}

object ShowTrackStep4
{
  def show_track_step4(steps3 : Iterable[Option[ShowTrackStep3]], embed_width : Int, embed_height : Int) : Iterable[Option[ShowTrackStep4]] =
  {
    for (step <- steps3)
      yield step match
      {
      case Some(step) => Some(ShowTrackStep4(step, embed_width, embed_height))
      case None => None
      }
  }
}

case class ShowTrackStep5(step4 : ShowTrackStep4, embed_html : String)
{
  import step4._
  import step3._
  import step2._
  import step1._
  
  val html = embed_html + graph_html
}
object ShowTrackStep5
{
def show_track_step5(steps4 : Iterable[Option[ShowTrackStep4]]) : Iterable[Option[ShowTrackStep5]] =
{
  for {step <- steps4
	  response = step match
	  {
	  case Some(step) => Some(SoundcloudWebsiteGetter.jsonWebsiteContent(step.embed_request))
	  case None => None
	  }
  	}
  		yield response match
  		{
  		  case Some(Right(json)) => Some(ShowTrackStep5(step.get, ShowHotStuff.extract_html(json)))
  		  case Some(Left(_)) => None
  		  case None => None
  		}
}
}

class ShowHotStuff extends HttpServlet
{
  val log = Logger.getLogger("ShowHotStuff")
  
  override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
  {
    val text = new StringBuilder()
    text ++= """<!DOCTYPE html>
<html>
<head>
  <title>Some Tracks</title>
</head>
<body>
  <link href="graph.css" rel="stylesheet" type="text/css" />"""
    text ++= ShowHotStuff.graph_script_html()
    resp.setContentType("text/html")
    val iteration_param = req.getParameter("iteration")
    val iteration = if (iteration_param == null)
      18696
    else
      iteration_param.toInt
    val datastore = DatastoreWrapper.connect_to_google_database
    val entity = datastore.get(KeyFactory.createKey("hot_stuff", iteration))
    val top_ids = entity.getProperty("top").asInstanceOf[java.util.Collection[Long]]
    val old_ids = entity.getProperty("old").asInstanceOf[java.util.Collection[Long]]
    import collection.JavaConverters._
    val top_requests = top_ids.asScala map (ShowTrackStep1(_))
    val old_requests = old_ids.asScala map (ShowTrackStep1(_))
    var top_count = 0
    def top_step2(step1 : ShowTrackStep1) =
    {
      val result = ShowTrackStep2(step1, Some(iteration), datastore, "top_" + top_count.toString, 400, 400)
      top_count = top_count + 1
      result
    }
    var old_count = 0
    def old_step2(step1 : ShowTrackStep1) =
    {
      val result = ShowTrackStep2(step1, Some(iteration), datastore, "old_" + old_count.toString, 400, 400)
      old_count = old_count + 1
      result
    }
    val step2 = ((top_requests map top_step2), (old_requests map old_step2))
    val step3 = (ShowTrackStep3.show_track_step3(step2._1), ShowTrackStep3.show_track_step3(step2._2))
    val step4 = (ShowTrackStep4.show_track_step4(step3._1, 400, 400), ShowTrackStep4.show_track_step4(step3._2, 400, 400))
    val step5 = (ShowTrackStep5.show_track_step5(step4._1), ShowTrackStep5.show_track_step5(step4._2))
    text ++= previous_next_page_buttons(iteration)
    text ++= """
  <h1>Top Stuff (from recent to older)</h1>"""
    for (Some(result) <- step5._1)
      text ++= result.html
    text ++= """
  <h1>Old Spiking Stuff (from popular to unpopular)</h1>"""
    for (Some(result) <- step5._2)
      text ++= result.html
    text ++= """
  </body>
</html>"""
    resp.getWriter().println(text.result)
  }
  
  def get_self_url() : String =
    "show_hot_stuff"
  
  def previous_next_page_buttons(iteration : Int) : String =
  {
    val builder = new StringBuilder()
    builder ++= "<p>"
    if (iteration > ShowHotStuff.lowest_valid_iteration)
    	builder ++= "<a href=\"" + get_self_url + "?iteration=" + (iteration - 1).toString + "\">&lt;-Previous Day</a>..."
    builder ++= iteration.toString
    if (iteration < ShowHotStuff.highest_valid_iteration)
    	builder ++= "...<a href=\"" + get_self_url + "?iteration=" + (iteration + 1).toString + "\">Next Day-&gt;</a>"
    builder ++= "</p>"
    builder.result
  }
}

object ShowHotStuff
{
  def extract_html(json : JValue) : String =
  {
    (json \ "html").asInstanceOf[JString].s
  }
  
  def graph_script_html() : String =
    """
  <script type="text/javascript" src="http://d3js.org/d3.v3.js"></script>
  <script type="text/javascript" src="graph.js"></script>"""
    
  val highest_valid_iteration = 18703
  val lowest_valid_iteration = 18692
}

class ShowTop5 extends HttpServlet
{
  def memcache_key(iteration : Int, playcount_number : Int, topn : Int) : String =
    "stored_top_5_results(" + iteration.toString + ", " + playcount_number.toString + ", " + topn.toString + ")"
  
  def get_topn(iteration : Int, playcount_number : Int, bigquery : Bigquery, topn : Int) : Array[Long] =
  {
    FindHotStuff.get_top(bigquery, iteration, playcount_number, topn).toArray
  }
    
  override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
  {
    val text = new StringBuilder()
    val topn = 20
    text ++= """<!DOCTYPE html>
<html>
<head>
  <title>Some Top """ + topn.toString + """</title>
</head>
<body>
  <link href="graph.css" rel="stylesheet" type="text/css" />"""
    text ++= ShowHotStuff.graph_script_html()
    resp.setContentType("text/html")
    val iteration_param = req.getParameter("iteration")
    val iteration = if (iteration_param == null)
      18696
    else
      iteration_param.toInt
    val playcount_number_param = req.getParameter("wavelet")
    val playcount_number = if (playcount_number_param == null)
      0
    else
      playcount_number_param.toInt
    val datastore = DatastoreWrapper.connect_to_google_database
	val memcache = MemcacheServiceFactory.getMemcacheService()
	memcache.setErrorHandler(ErrorHandlers.getConsistentLogAndContinue(Level.INFO))
    var top_ids : Array[Long] = memcache.get(memcache_key(iteration, playcount_number, topn)).asInstanceOf[Array[Long]]
    if (top_ids == null)
    {
      val bigquery = BigqueryWrapper.connect(BigqueryWrapper.authorizeFromAppEngine)
      top_ids = get_topn(iteration, playcount_number, bigquery, topn)
      memcache.put(memcache_key(iteration, playcount_number, topn), top_ids)
    }
    import collection.JavaConverters._
    text ++= previous_next_page_buttons(iteration, playcount_number)
    text ++= up_down_playcount_buttons(iteration, playcount_number)
    text ++= """
  <h1>Top """ + topn.toString + " (hottest first)</h1>"
    text ++= (for (Some(html) <- ShowTop5.EmbedIDs(for (id <- top_ids) yield iteration, datastore, top_ids, 400, 400)) yield html).mkString("<p>", "</p>\n<p>", "</p>")
    text ++= """
</body>
</html>"""
    resp.getWriter().println(text.result)
  }
  def get_self_url() : String =
    "show_top_5"
  
  def up_down_playcount_buttons(iteration : Int, playcount_number : Int) : String =
  {
    val builder = new StringBuilder()
    builder ++= "<p>"
    if (playcount_number > 0)
    	builder ++= "<a href=\"" + get_self_url + "?iteration=" + iteration + "&wavelet=" + (playcount_number - 1).toString + "\">&lt;-Shorter Term</a>..."
    builder ++= playcount_number.toString
    if (playcount_number < 4)
    	builder ++= "...<a href=\"" + get_self_url + "?iteration=" + iteration.toString + "&wavelet=" + (playcount_number + 1).toString + "\">Longer Term-&gt;</a>"
    builder ++= "</p>"
    builder.result
  }
  def previous_next_page_buttons(iteration : Int, playcount_number : Int) : String =
  {
    val builder = new StringBuilder()
    builder ++= "<p>"
    if (iteration > ShowHotStuff.lowest_valid_iteration)
    	builder ++= "<a href=\"" + get_self_url + "?iteration=" + (iteration - 1).toString + "&wavelet=" + playcount_number.toString + "\">&lt;-Previous Day</a>..."
    builder ++= iteration.toString
    if (iteration < ShowHotStuff.highest_valid_iteration)
    	builder ++= "...<a href=\"" + get_self_url + "?iteration=" + (iteration + 1).toString + "&wavelet=" + playcount_number.toString + "\">Next Day-&gt;</a>"
    builder ++= "</p>"
    builder.result
  }
}

object ShowTop5
{
  def ShowTrackSteps(iterations : Iterable[Int], datastore : DatastoreService, ids : Array[Long], width : Int, height : Int) : Iterable[Option[ShowTrackStep5]] =
  {
    val requests = ids map (ShowTrackStep1(_))
    var count = 0
    def step2_func(step1 : ShowTrackStep1, iteration : Int) =
    {
      val result = ShowTrackStep2(step1, Some(iteration), datastore, "top_" + count.toString, width, height)
      count = count + 1
      result
    }
    val step2 = for ((step1, iteration) <- requests.zip(iterations))
      yield step2_func(step1, iteration)
    val step3 = ShowTrackStep3.show_track_step3(step2)
    val step4 = ShowTrackStep4.show_track_step4(step3, width, height)
    ShowTrackStep5.show_track_step5(step4)
  }
  def EmbedIDs(iterations : Iterable[Int], datastore : DatastoreService, ids : Array[Long], width : Int, height : Int) : Iterable[Option[String]] =
  {
    for (step <- ShowTrackSteps(iterations, datastore, ids, width, height))
      yield step match
      {
      case Some(step) => Some(step.html)
      case None => None
      }
  }
}

class ShowBigQueryResults extends HttpServlet
{
  def result_to_html(result : QueryResponse) : String =
  {
    val text = new StringBuilder()
    text ++= """
      <table>
        <tr>"""
    import collection.JavaConverters._
    for (field <- result.getSchema().getFields().asScala)
    {
      text ++= """
          <td>""" + field.getName() + """</td>"""
    }
    text ++= """
        </tr>
        """
    text ++= """
      </table>"""
    text.result
  }
  
  def responseToTable(response : QueryResponse, iteration : Int, datastore : DatastoreService) : String =
  {
    val text = new StringBuilder()
    import collection.JavaConverters._
    text ++= """
      <style type="text/css">
table.track_table {
	border-width: 1px;
	border-spacing: 2px;
	border-style: outset;
	border-color: gray;
	border-collapse: collapse;
}
table.track_table th {
	border-width: 1px;
	padding: 2px;
	border-style: inset;
	border-color: gray;
}
table.track_table td {
	border-width: 1px;
	padding: 2px;
	border-style: inset;
	border-color: gray;
}
</style>
    <table class="track_table">
      <tr>"""
    var top_ids : Array[Long] = null
    val schema = response.getSchema().getFields().asScala
    val rows = for {temp_rows <- (response.getRows() :: Nil)
    				row_id <- 0 until temp_rows.size()
    				row = temp_rows.get(row_id).getF() }
    	yield row
    var iterations : Array[Int] = null
    for ((field, field_id) <- schema.zipWithIndex)
    {
      text ++= "<td>" + field.getName() + "</td>"
      if (field.getName() == "track_id")
      {
	     top_ids = (for (row <- rows)
		    yield row.get(field_id).getV().asInstanceOf[String].toLong).toArray
      }
      if (field.getName() == "iteration")
      {
        iterations = (for (row <- rows)
        	yield row.get(field_id).getV().asInstanceOf[String].toInt).toArray
      }
    }
    val html_embeds = if (top_ids != null)
        ShowTop5.EmbedIDs(if (iterations != null) iterations else for (id <- top_ids) yield iteration, datastore, top_ids, 400, 100)
      else
        for (row <- rows) yield null
    text ++= """
      </tr>"""
    for ((row, html_embed) <- rows.zip(html_embeds))
    {
      text ++= "<tr>"
      for ((field, field_id) <- schema.zipWithIndex)
      {
        text ++= "<td>"
        if (field.getName() == "track_id")
        {
          text ++= row.get(field_id).getV().asInstanceOf[String]
          html_embed match
          {
            case Some(html) => text ++= html
            case None =>
          }
        }
        else
        {
          try
          {
        	text ++= row.get(field_id).getV().asInstanceOf[String]
          }
          catch
          {
            case _ : ClassCastException => text ++= "null"
          }
        }
        text ++= "</td>"
      }
      text ++= "</tr>"
    }
    text ++= """
    </table>"""
    text.result
  }
  
  def doRequest(req : HttpServletRequest, resp : HttpServletResponse)
  {
    val text = new StringBuilder()
    text ++= """<!DOCTYPE html>
<html>
<head>
  <title>Bigquery Querier</title>
</head>
<body>
  <link href="graph.css" rel="stylesheet" type="text/css" />"""
    text ++= ShowHotStuff.graph_script_html()
    resp.setContentType("text/html")
    
    var query = req.getParameter("query").trim
    if (query == null)
    {
      query = """SELECT track_id, iteration, playcount0, playcount1, playcount2, playcount3, playcount4
FROM [wavelets.track_playcount]
WHERE iteration = 18700
ORDER BY playcount0 DESC
LIMIT 20"""
    }
    
    text ++="""
    <form method="post" action="">
     <textarea name="query" cols="100" rows="10">
""" + query + """</textarea><br />
     <input type="submit" value="Submit" />
    </form>"""
    
	val iteration_regex = new Regex("iteration[ ]*=[ ]*(\\d+)")
    val iteration = iteration_regex.findFirstIn(query) match
    {
      case Some(iteration_string) =>
      {
        val iteration_regex(iteration_int) = iteration_string
        iteration_int.toInt
      }
      case None => AnteInfo.get_current_iteration
    }

    val playcount_number_param = req.getParameter("wavelet")
    val playcount_number = if (playcount_number_param == null)
      0
    else
      playcount_number_param.toInt
    val topn = 20
    val datastore = DatastoreWrapper.connect_to_google_database
	val memcache = MemcacheServiceFactory.getMemcacheService()
	memcache.setErrorHandler(ErrorHandlers.getConsistentLogAndContinue(Level.INFO))
    val bigquery = BigqueryWrapper.connect(BigqueryWrapper.authorizeFromAppEngine)
    var bigquery_result : QueryResponse = null
    try
    {
	  bigquery_result = FindHotStuff.get_bigquery_response(bigquery, query)/*memcache.get(query).asInstanceOf[QueryResponse]
      if (bigquery_result == null)
      {
        bigquery_result = FindHotStuff.get_bigquery_response(bigquery, query)
        memcache.put(query, bigquery_result)
      }*/
    }
    catch
    {
      case response : GoogleJsonResponseException =>
      {
        text ++= """
          <p>Had an error:</p>"""
import net.liftweb.json
        text ++= (json.parse(response.getContent()) \ "message").asInstanceOf[json.JString].s
      }
    }
    if (bigquery_result != null)
    {
      text ++= """
  <h1>Query Results</h1>"""
      text ++= responseToTable(bigquery_result, iteration, datastore)
    }
    text ++= """
  </body>
</html>"""
    resp.getWriter().println(text.result)
  }
  
  override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
  {
    doRequest(req, resp)
  }
  override def doPost(req : HttpServletRequest, resp : HttpServletResponse)
  {
    doRequest(req, resp)
  }
}

class ShowStackedGraph extends HttpServlet
{
  def doRequest(req : HttpServletRequest, resp : HttpServletResponse)
  {
    resp.setContentType("text/html")
    if (req.getParameter("track_id") == null)
    {
      resp.getWriter().println("Please provide a track_id")
      return
    }
    val track_id = req.getParameter("track_id").toLong
    val step1 = ShowTrackStep1(track_id)
    val datastore = DatastoreWrapper.connect_to_google_database
    val step2 = ShowTrackStep2(step1, None, datastore, "some_id", 800, 100)
    val step3 = ShowTrackStep3.show_track_step3(step2 :: Nil)
    val first_derivative = for (i <- 0 until (step2.playcounts.length - 1))
      yield (step2.playcounts(i + 1) - step2.playcounts(i))
    val bigquery = BigqueryWrapper.connect(BigqueryWrapper.authorizeFromAppEngine)
    val wavelet_response = FindHotStuff.get_bigquery_response(bigquery, """SELECT iteration, playcount0, playcount1, playcount2, playcount3, playcount4
FROM [wavelets.track_playcount]
WHERE track_id = """ + track_id.toString + """
ORDER BY iteration DESC
LIMIT """ + step2.playcounts.length)
    val step4 = ShowTrackStep4.show_track_step4(step3, 400, 400)
    
    val step5 = ShowTrackStep5.show_track_step5(step4)
  }
  override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
  {
    doRequest(req, resp)
  }
  override def doPost(req : HttpServletRequest, resp : HttpServletResponse)
  {
    doRequest(req, resp)
  }
}


class EmbedTest extends FlatSpec with Matchers
{
  val some_real_text = """{"version":1.0,"type":"rich","provider_name":"SoundCloud","provider_url":"http://soundcloud.com","height":400,"width":"100%","title":"Summer (Diplo \u0026 Grandtheft Remix) by Calvinharris","description":"Diplo \u0026 Grandtheft remix of Summer is OUT NOW!","thumbnail_url":"http://i1.sndcdn.com/artworks-000082913710-i1vx6h-t500x500.jpg","html":"\u003Ciframe width=\"100%\" height=\"400\" scrolling=\"no\" frameborder=\"no\" src=\"https://w.soundcloud.com/player/?visual=true\u0026url=https%3A%2F%2Fapi.soundcloud.com%2Ftracks%2F155226929\u0026show_artwork=true\"\u003E\u003C/iframe\u003E","author_name":"Calvinharris","author_url":"https://soundcloud.com/calvinharris"}"""
  "some real text" should "give me an embed html" in
  {
    val expected = "\u003Ciframe width=\"100%\" height=\"400\" scrolling=\"no\" frameborder=\"no\" src=\"https://w.soundcloud.com/player/?visual=true\u0026url=https%3A%2F%2Fapi.soundcloud.com%2Ftracks%2F155226929\u0026show_artwork=true\"\u003E\u003C/iframe\u003E"
    val converted = ShowHotStuff.extract_html(net.liftweb.json.parse(some_real_text))
    converted should be (expected)
  }
}
