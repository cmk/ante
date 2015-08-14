package com.ante.soundcloud_pagerank

import com.google.api.services.datastore.client.DatastoreFactory
import com.google.api.services.datastore.client.DatastoreHelper
import com.google.api.services.datastore.client.Datastore
import com.google.appengine.tools.remoteapi.RemoteApiOptions
import com.google.api.services.datastore.DatastoreV1.LookupRequest
import com.google.api.services.datastore.DatastoreV1.Key
import com.google.api.services.datastore.DatastoreV1.LookupResponse
import com.google.api.services.datastore.DatastoreV1.Query
import com.google.api.services.datastore.DatastoreV1.KindExpression
import com.google.api.services.datastore.DatastoreV1.RunQueryRequest
import scala.collection.JavaConversions
import com.google.api.services.datastore.DatastoreV1.Entity
import com.ante.user_info_crawler.AnteInfo
import com.google.api.services.datastore.DatastoreV1.Property
import com.google.api.services.datastore.DatastoreV1.Value
import com.google.api.services.datastore.DatastoreV1.CommitRequest
import com.google.api.services.datastore.DatastoreV1.BeginTransactionRequest
import java.net.URL
import java.net.HttpURLConnection
import java.io.DataOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import scala.collection.JavaConverters
import java.io.File
import com.google.api.client.http.javanet.NetHttpTransport
import java.util.Arrays
import com.google.api.services.bigquery.model.Dataset
import com.google.api.services.bigquery.model.TableRow
import com.google.api.services.bigquery.model.TableDataInsertAllRequest
import com.google.api.services.bigquery.model.Table
import com.google.api.services.bigquery.model.TableReference
import com.google.api.services.bigquery.model.TableFieldSchema
import com.google.api.services.bigquery.model.TableSchema
import com.google.api.services.datastore.DatastoreV1.RunQueryResponse
import scala.collection.mutable.ArrayBuilder
import com.google.api.services.bigquery.Bigquery
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.datastore.DatastoreV1.QueryResultBatch
import com.ante.gae_interface.AppEngineWrapper
import com.ante.gae_interface.BigqueryWrapper
import com.ante.user_info_crawler.TrackSnapshot

object PagerankMain
{
  val filename = "graph_out"
  val json_factory = new JacksonFactory()
  val http_transport = new NetHttpTransport()
  
  def print_graph(datastore : Datastore) : Int =
  {
    implicit def stringToKind(kind : String) = KindExpression.newBuilder().setName(kind)
    val output = new java.io.PrintWriter(new java.io.File(filename))
    val query = Query.newBuilder().addKind("favorites").setLimit(1000) // it will crash for a value of 2000
    var num_users_so_far = 0
    var printed_so_far = 0
    while (true)
    {
    	def get_values() : (QueryResultBatch, Traversable[Entity]) =
    	{
    	  val result = datastore.runQuery(RunQueryRequest.newBuilder().mergeQuery(query.build()).build())
    	  val batch = result.getBatch()
    	  import JavaConverters._
    	  (batch, batch.getEntityResultList().asScala map (_.getEntity()))
    	}
    	var result : (QueryResultBatch, Traversable[Entity]) = null
	    try
	    {
	      result = get_values()
	    }
	    catch
	    {
	      case _ : Throwable => result = get_values()
	    }
	    val (batch, result_entities) = result
	    if (result_entities.isEmpty)
	    {
	      output.close()
	      return num_users_so_far
	    }
	    for (entity <- result_entities)
	    {
	      val id = entity.getKey().getPathElement(0).getId()
	      val favorite_users = for (i <- 0 until entity.getPropertyCount();
	    		  	property = entity.getProperty(i);
	    		  	if (property.getName() == "user_ids"))
	    	yield JavaConversions.asScalaBuffer(property.getValue().getListValueList())
	      for (other_id_value  <- favorite_users(0))
	      {
	        val other_id = other_id_value.getIntegerValue()
	        if (id != other_id)
	        {
	          output.write(id.toString + "," + other_id.toString + ",1\n")
	          printed_so_far += 1
	        }
	      }
	      num_users_so_far += 1
	    }
	    println("Got " + num_users_so_far.toString + " users for " + printed_so_far.toString + " edges.")
	    query.setStartCursor(batch.getEndCursor())
    }
    throw new IllegalStateException("This should never happen")
  }
  
  def run_pagerank(num_to_keep : Int)
  {
    val abs_filename = new java.io.File(filename).getCanonicalPath()
    val process = scala.sys.process.Process("./bin/example_apps/pagerank_ante" :: "file" :: abs_filename :: "filetype" :: "edgelist" :: "toprint" :: num_to_keep.toString :: Nil, new java.io.File("../graphchi"))
    if (process.! != 0)
    {
      throw new Exception("pagerank had an error")
    }
  }
  
  def read_pageranks() : Map[Int, Float] =
  {
    val source = scala.io.Source.fromFile(filename + "_pageranks")
    val builder = ArrayBuilder.make[(Int, Float)]()
    for (line <- source.getLines)
    {
      val cols = line.split(",").map(_.trim)
      builder += (cols(0).toInt -> cols(1).toFloat)
    }
    source.close
    builder.result.toMap
  }
  
  def convert_to_csv(pageranks : Traversable[(Int, Float)]) : String =
    "user_id,pagerank\r\n" +
    		(pageranks map (v => v._1 + "," + v._2)).mkString("\r\n")
  
  def getTable(dataset_id : String, table_id : String) : Table =
  {
	  val reference = new TableReference().setProjectId(AppEngineWrapper.project).setDatasetId(dataset_id).setTableId(table_id)
	  val fields = new TableFieldSchema().setName("user_id").setType("INTEGER") ::
		  			new TableFieldSchema().setName("pagerank").setType("FLOAT") ::
		  			new TableFieldSchema().setName("iteration").setType("INTEGER") ::
		  			Nil
	  import JavaConverters._
	  val schema = new TableSchema().setFields(fields.asJava)
	  new Table().setTableReference(reference).setSchema(schema)
  }
  
  def upload_pageranks_to_bigquery(pageranks : Traversable[(Int, Float)], iteration : Int)
  {
	val bigquery = BigqueryWrapper.connect(BigqueryWrapper.authorizeFromOutside)
	val dataset_id = "timeseries"
	val table_id = "pageranks"
	def row_for_pagerank(user_id : Int, pagerank : Float) =
	  new TableRow().
	  	set("user_id", user_id).
	  	set("pagerank", pagerank).
	  	set("iteration", iteration)
	val rows = (for ((id, pagerank) <- pageranks)
	  yield new TableDataInsertAllRequest.Rows().setJson(row_for_pagerank(id, pagerank)).setInsertId(id.toString)).toArray
	
	BigqueryWrapper.createTable(AppEngineWrapper.project, dataset_id, getTable(dataset_id, table_id), bigquery)
	
	val batch_size = 10000
	var total_uploaded = 0
	for (grouped <- rows.grouped(batch_size))
	{
	  val response = BigqueryWrapper.insert(grouped, dataset_id, table_id, bigquery)
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
	  total_uploaded += grouped.length
	  println("Uploaded " + total_uploaded.toString + " pageranks")
	}
  }
  
  def store_pageranks(pageranks : Iterable[(Int, Float)], datastore : Datastore, iteration : Int)
  {
    def entity_for_pagerank(id : Int, pagerank : Float) : Entity =
    {
		val key = Key.newBuilder().addPathElement(
		    Key.PathElement.newBuilder()
		    .setKind("pagerank").setId((id.toLong << 32) | iteration.toLong));
		val entity = Entity.newBuilder()
		entity.setKey(key)
		entity.addProperty(Property.newBuilder()
            .setName("iteration")
            .setValue(Value.newBuilder().setIntegerValue(iteration)));
		entity.addProperty(Property.newBuilder()
		    .setName("pagerank")
		    .setValue(Value.newBuilder()
		        .setIndexed(false)
		        .setDoubleValue(pagerank)))
		entity.build()
    }
    var num_stored = 0
    def upload(pageranks : Iterable[(Int, Float)])
    {
	    val request = CommitRequest.newBuilder()
	    request.setMode(CommitRequest.Mode.NON_TRANSACTIONAL)
	    val entities = for (pagerank <- pageranks)
	      request.getMutationBuilder().addUpsert(entity_for_pagerank(pagerank._1, pagerank._2))
	    datastore.commit(request.build())
	    num_stored += pageranks.size
	    println("Stored " + num_stored + " pageranks.")
    }
    
    // can only do five hundred at a time. can't find documentation
    // for this number, but if I do more than that I get an error
    // message
	for (five_hundred <- pageranks.grouped(500))
	{
	  try
	  {
	    upload(five_hundred)
	  }
	  catch
	  {
	    // the datastore is not entirely reliable. for less than 1% of the
	    // calls it seems to fail randomly. which is a bit annoying if
	    // you're uploading a lot of data and don't want to stop when half of
	    // it is already stored
	    case ex : Throwable =>
	    {
	      try
	      {
	        ex.printStackTrace()
	        // try a second time
	        upload(five_hundred)
	      }
	      catch
	      {
	        // don't try a third time. just continue
	        case ex : Throwable => ex.printStackTrace()
	      }
	    }
	  }
	}
  }
  
  def entityToSnapshot(entity : Entity) : TrackSnapshot =
  {
      val id = entity.getKey().getPathElement(0).getId()
      val comment_count = for (i <- 0 until entity.getPropertyCount();
    		  	property = entity.getProperty(i);
    		  	if (property.getName() == "comment_count"))
        yield property.getValue().getIntegerValue()
      val download_count = for (i <- 0 until entity.getPropertyCount();
    		  	property = entity.getProperty(i);
    		  	if (property.getName() == "download_count"))
        yield property.getValue().getIntegerValue()
      val playback_count = for (i <- 0 until entity.getPropertyCount();
    		  	property = entity.getProperty(i);
    		  	if (property.getName() == "playback_count"))
        yield property.getValue().getIntegerValue()
      val favoritings_count = for (i <- 0 until entity.getPropertyCount();
    		  	property = entity.getProperty(i);
    		  	if (property.getName() == "favoritings_count"))
        yield property.getValue().getIntegerValue()
      
      val user_id = (id >> 32).toInt
      val iteration = id.toInt
      TrackSnapshot(user_id, iteration, comment_count(0).toInt, download_count(0).toInt, playback_count(0).toInt, favoritings_count(0).toInt)
  }
  
  def getTrackStatistics(datastore : Datastore) : Array[TrackSnapshot] =
  {
    implicit def stringToKind(kind : String) = KindExpression.newBuilder().setName(kind)
    val query = Query.newBuilder().addKind("track_statistics_snapshot").setLimit(2000)
    val result_builder = ArrayBuilder.make[TrackSnapshot]
    var num_tracks_so_far = 0
    while (true)
    {
    	def get_values() : (QueryResultBatch, Traversable[Entity]) =
    	{
    	  val result = datastore.runQuery(RunQueryRequest.newBuilder().mergeQuery(query.build()).build())
    	  val batch = result.getBatch()
    	  import JavaConverters._
    	  (batch, batch.getEntityResultList().asScala map (_.getEntity()))
    	}
    	var result : (QueryResultBatch, Traversable[Entity]) = null
	    try
	    {
	      result = get_values()
	    }
	    catch
	    {
	      case _ : Throwable => result = get_values()
	    }
	    val (batch, result_entities) = result
	    if (result_entities.isEmpty)
	    {
	      return result_builder.result
	    }
	    for (entity <- result_entities)
	    {
	      result_builder += entityToSnapshot(entity)
	      num_tracks_so_far += 1
	    }
	    println("Got " + num_tracks_so_far.toString + " track snapshots")
	    query.setStartCursor(batch.getEndCursor())
    }
    throw new IllegalStateException("This should never happen")
  }
  
  def storeSnapshotsInBigquery(snapshots : Iterable[TrackSnapshot])
  {
	val dataset_id = "timeseries"
	
	val bigquery = BigqueryWrapper.connect(BigqueryWrapper.authorizeFromOutside)
	val project = AppEngineWrapper.project
	BigqueryWrapper.createTable(project, dataset_id, TrackSnapshot.tableForTracks(project, dataset_id), bigquery)
	for (group <- snapshots.grouped(10000))
	{
		val rows = group map TrackSnapshot.rowForTrack
		def insertable_row(row : TableRow) : TableDataInsertAllRequest.Rows =
		{
		  new TableDataInsertAllRequest.Rows().setJson(row)
		}
		val insertable_rows : Array[TableDataInsertAllRequest.Rows] = (rows map insertable_row).toArray
		BigqueryWrapper.insert(insertable_rows, dataset_id, TrackSnapshot.table_id, bigquery)
	}
  }
  
  def transferDataToBigquery(datastore : Datastore)
  {
	storeSnapshotsInBigquery(getTrackStatistics(datastore))
  }
  
  def main(args: Array[String]): Unit =
  {
    val iteration = com.ante.user_info_crawler.AnteInfo.get_current_iteration
    val datastore : Datastore = DatastoreFactory.get().create(DatastoreHelper.getOptionsfromEnv()
          .dataset(AppEngineWrapper.project).build());
    //transferDataToBigquery(datastore)
    val num_users = print_graph(datastore)
    run_pagerank(num_users)
    val pageranks = read_pageranks()
    upload_pageranks_to_bigquery(pageranks, iteration)
    //store_pageranks(pageranks, datastore, iteration)
  }
}
