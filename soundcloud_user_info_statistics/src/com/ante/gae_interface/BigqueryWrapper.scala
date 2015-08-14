package com.ante.gae_interface

import com.google.api.services.bigquery.model.Table
import com.google.api.services.bigquery.Bigquery
import com.google.api.services.bigquery.model.Dataset
import com.google.api.client.googleapis.extensions.appengine.auth.oauth2.AppIdentityCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import java.io.File
import com.google.api.services.bigquery.model.TableDataInsertAllRequest
import scala.collection.JavaConversions
import scala.collection.JavaConverters
import com.google.api.services.bigquery.model.TableDataInsertAllResponse

object BigqueryWrapper
{
  def authorizeFromAppEngine() : AppIdentityCredential =
  {
   	import scala.collection.JavaConverters._
    new AppIdentityCredential(("https://www.googleapis.com/auth/bigquery" :: Nil).asJavaCollection)
  }
  
  def authorizeFromOutside() : GoogleCredential =
  {
    AppEngineWrapper.authorizeFromOutside("https://www.googleapis.com/auth/bigquery" :: Nil)
  }
  
  def connect(credential : HttpRequestInitializer) : Bigquery =
  {
	new Bigquery.Builder(AppEngineWrapper.http_transport, AppEngineWrapper.json_factory, credential).setApplicationName(AppEngineWrapper.project).setHttpRequestInitializer(credential).build()
  }
  
  def createTable(project_id : String, dataset_id : String, table : Table, bigquery : Bigquery)
  {
	try
	{
	  bigquery.datasets().insert(project_id, new Dataset().setId(project_id + ":" + dataset_id)).execute()
	}
	catch
	{
	  case _ : Throwable => 
	}
	try
	{
	  bigquery.tables().insert(project_id, dataset_id, table).execute()
	}
	catch
	{
	  case _ : Throwable =>
	}
  }
  
  def insert(rows : Seq[TableDataInsertAllRequest.Rows], dataset_id : String, table_id : String, bigquery : Bigquery) : TableDataInsertAllResponse =
  {
	val content = new TableDataInsertAllRequest().setRows(JavaConversions.seqAsJavaList(rows));
	bigquery.tabledata().insertAll(AppEngineWrapper.project, dataset_id, table_id, content).execute();
  }
}