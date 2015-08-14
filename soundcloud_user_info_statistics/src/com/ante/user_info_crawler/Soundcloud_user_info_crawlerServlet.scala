package com.ante.user_info_crawler

import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import com.google.appengine.api.datastore.DatastoreServiceFactory
import com.google.appengine.api.datastore.Query
import com.google.appengine.api.datastore.Entity
import com.google.appengine.api.datastore.KeyFactory
import com.google.appengine.api.datastore.EntityNotFoundException
import com.google.appengine.api.datastore.Text
import com.google.appengine.api.memcache.MemcacheServiceFactory
import com.google.appengine.api.memcache.ErrorHandlers
import java.util.logging.Level
import com.google.appengine.api.taskqueue.QueueFactory
import com.google.appengine.api.taskqueue.TaskOptions

case class BriefSnapshot(
    id : Int,
	followers_count : Int,
	comment_count : Int,
	download_count : Int,
	playback_count : Long,
	favoritings_count : Int
)

class Soundcloud_user_info_crawlerServlet extends HttpServlet
{
	def iteration_for_data() : Int = AnteInfo.get_current_iteration - 1
  
	def get_data(iteration : Int) : Array[BriefSnapshot] =
	{
		val datastore = DatastoreServiceFactory.getDatastoreService()
		val query = new Query("statistics_snapshot")
		query.addFilter("iteration", Query.FilterOperator.EQUAL, iteration)
		val results = datastore.prepare(query)
		import scala.collection.JavaConverters._
		val snapshots = for (result : Entity <- results.asIterable().asScala)
		  yield BriefSnapshot(
		      (result.getKey().getId() >> 32).toInt,
		      result.getProperty("followers_count").asInstanceOf[Long].toInt,
		      result.getProperty("comment_count").asInstanceOf[Long].toInt,
		      result.getProperty("download_count").asInstanceOf[Long].toInt,
		      result.getProperty("playback_count").asInstanceOf[Long],
		      result.getProperty("favoritings_count").asInstanceOf[Long].toInt)
		snapshots.toArray
	}
	
	def data_to_json(data : Array[BriefSnapshot]) : String =
	{
		import net.liftweb.json._
		import net.liftweb.json.JsonDSL._
		compact(render(JsonDSL.seq2jvalue(data.map(s =>
		  (("followers_count" -> s.followers_count)
			~ ("comment_count" -> s.comment_count)
			~ ("download_count" -> s.download_count)
			~ ("playback_count" -> s.playback_count)
			~ ("favoritings_count" -> s.favoritings_count)
			~ ("id" -> s.id))))))
	}
	
	def build_correlations_website(iteration : Int) : String =
	{
	  val builder = new StringBuilder()
	  builder ++=
	  """<!DOCTYPE html>
<html>
  <head>
    <title>Hi there</title>
  </head>
<style>

.chart rect {
  fill: steelblue;
}

.axis text {
  font: 10px sans-serif;
}

.axis path,
.axis line {
  fill: none;
  stroke: #000;
  shape-rendering: crispEdges;
}

.x.axis path {
  display: none;
}
		    
.axis_label
{
	display : block;
}
.axis_box
{
	display : block;
}

</style>
  <body>
<svg class="chart"></svg>
"""
	    builder ++= """
<script type="text/javascript" src="http://d3js.org/d3.v3.js"></script>"""
	    builder ++= """
<script type="text/javascript" src="http://code.jquery.com/jquery-2.1.1.min.js"></script>"""
	    builder ++= """
<script type="text/javascript">
	var data = """ + data_to_json(get_data(iteration)) + """;
	
	function generate_graph(x_type, y_type)
	{
		var margin = {top: 20, right: 30, bottom: 30, left: 40},
		    width = 960 - margin.left - margin.right,
		    height = 500 - margin.top - margin.bottom;
		
		var data_to_x = x_type.data;
		var data_to_y = y_type.data;
		var x = d3.scale.log()
		    .domain([d3.max([1, d3.min(data, data_to_x)]), d3.max(data, data_to_x)])
		    .range([0, width]);
		var y = d3.scale.log()
		    .domain([d3.max([1, d3.min(data, data_to_y)]), d3.max(data, data_to_y)])
		    .range([height, 0]);
		
		d3.select(".generated_chart").remove();
		
		var chart = d3.select(".chart")
		    .attr("width", width + margin.left + margin.right)
		    .attr("height", height + margin.top + margin.bottom)
		  .append("g")
			.attr("class", "generated_chart")
			.attr("transform", "translate(" + margin.left + ", " + margin.top + ")");
		
		var xAxis = d3.svg.axis()
		    .scale(x)
		    .orient("bottom");
		
		var yAxis = d3.svg.axis()
		    .scale(y)
		    .orient("left");
		
		chart.append("g")
		    .attr("class", "x axis")
		    .attr("transform", "translate(0," + height + ")")
		    .call(xAxis)
		  .append("text")
			.text(x_type.name);
		
		chart.append("g")
		    .attr("class", "y axis")
		    .call(yAxis)
		  .append("text")
			.attr("y", -3)
		    .text(y_type.name);
		
		var bar = chart.selectAll("g")
		    .data(data)
		  .enter().append("g")
		    .attr("transform", function(d, i) { return "translate(" + x(data_to_x(d)) + ", " + y(data_to_y(d)) + ")"; });
		
		bar.append("a")
			.on("click", function(d)
			{
				var api_url = "http://api.soundcloud.com/users/" + d.id + ".json?client_id=5529052630cbb1a05dfb3ab18074554e";
				var dom_element = this;
				$.getJSON(api_url, function(user)
				{
					var url = "http://soundcloud.com/" + user.permalink;
					window.open(url);
					d3.select(dom_element).attr("xlink:href", url);
				});
				return false;
			})
			.attr("xlink:href", function(d)
			{
				return "#";
				//return "http://api.soundcloud.com/users/" + d.id + ".json?client_id=5529052630cbb1a05dfb3ab18074554e";
			})
			.append("rect")
			    .attr("width", 3)
			    .attr("height", 3);
	}
	var axises =
	[
		{ name : "Playback Count", data : function(d) { return d.playback_count; } },
		{ name : "Followers Count", data : function(d) { return d.followers_count; } },
		{ name : "Comment Count", data : function(d) { return d.comment_count; } },
		{ name : "Download Count", data : function(d) { return d.download_count; } },
		{ name : "Favoritings Count", data : function(d) { return d.favoritings_count; } }
	];
	
	var selected_y = 0;
	var selected_x = 1;
	
	function generate_default_graph()
	{
		generate_graph(axises[selected_x], axises[selected_y]);
	}
	
	var x_axis_button_container = d3.select("body")
		.append("div").text("X Axis");
	var x_axis_buttons = x_axis_button_container.selectAll("label").data(axises).enter()
		.append("label")
			.text(function(d) { return d.name; })
			.attr("class", "axis_label")
		.append("input")
			.attr("type", "radio")
			.attr("name", "x_axis")
			.attr("value", function(d, i) { return i; })
			.property("checked", function(d, i) { return i == selected_x; })
			.on("click", function(d, i)
			{
				selected_x = i;
				generate_default_graph();
			});
	
	var y_axis_button_container = d3.select("body")
		.append("div").text("Y Axis");
	var y_axis_buttons = y_axis_button_container.selectAll("label").data(axises).enter()
		.append("label")
			.text(function(d) { return d.name; })
			.attr("class", "axis_label")
		.append("input")
			.attr("type", "radio")
			.attr("name", "y_axis")
			.attr("value", function(d, i) { return i; })
			.property("checked", function(d, i) { return i == selected_y; })
			.on("click", function(d, i)
			{
				selected_y = i;
				generate_default_graph();
			});

	generate_default_graph();
	
</script>"""
		builder ++= """
  </body>
</html>
"""
		builder.result
	}
	
	def get_current_correlations_website() : String =
	{
		val iteration = iteration_for_data()
		val key : String = "correlations_website_" + iteration.toString
		val memcache = MemcacheServiceFactory.getMemcacheService()
		memcache.setErrorHandler(ErrorHandlers.getConsistentLogAndContinue(Level.INFO))
		var website = memcache.get(key).asInstanceOf[String]
		if (website == null)
		{
	      website = build_correlations_website(iteration)
	      memcache.put(key, website)
		}
		website
	}

	override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
	{
		resp.setContentType("text/html")
		resp.getWriter().println(get_current_correlations_website)
	}
}
