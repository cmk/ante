package com.ante.hot_tracks

import com.google.api.services.bigquery.Bigquery
import com.ante.gae_interface.AppEngineWrapper
import com.google.api.services.bigquery.model.QueryRequest
import com.ante.gae_interface.BigqueryWrapper
import com.ante.gae_interface.DatastoreWrapper
import com.google.api.services.datastore.client.Datastore
import com.google.api.services.datastore.DatastoreV1.Entity
import com.google.api.services.datastore.DatastoreV1.Key
import com.google.api.services.datastore.DatastoreV1.Property
import com.google.api.services.datastore.DatastoreV1.Value
import com.google.api.services.bigquery.model.QueryResponse

class FindHotStuff
{
}

object FindHotStuff
{
  def get_top_stuff_query(iteration : Int, number : Int) : String =
  {
    """SELECT track_id, iteration, playcount0, playcount1, playcount2, playcount3, playcount4
FROM [wavelets.track_playcount]
WHERE iteration = """ + iteration.toString + """
ORDER BY playcount""" + number.toString + """ DESC"""
  }
  
  def get_old_hot_stuff_query(iteration : Int, popularity : Double) : String =
  {
"""SELECT track_id, iteration, playcount0, playcount1, playcount2, playcount3, playcount4, (playcount0 / playcount4) AS ratio
FROM [wavelets.track_playcount]
WHERE iteration = """ + iteration.toString + """
  AND playcount4 IS NOT null
  AND playcount4 != 0
  AND playcount4 < -""" + popularity.toString + """
ORDER BY ratio ASC"""
  }
  
  def get_bigquery_response(bigquery : Bigquery, query : String) : QueryResponse =
  {
    bigquery.jobs().query(AppEngineWrapper.project, new QueryRequest().setQuery(query).setTimeoutMs(60000)).execute()
  }
  def get_bigquery_ids(bigquery : Bigquery, query : String) : Iterable[Long] =
  {
    val result = get_bigquery_response(bigquery, query)
    val rows = result.getRows()
    for {i <- 0 until rows.size()
         first_row = rows.get(i).getF()
         track_id = first_row.get(0)}
	    yield track_id.getV().asInstanceOf[String].toLong
  }
  
  def get_top_stuff(bigquery : Bigquery, iteration : Int) : Iterable[Long] =
  {
    for {i <- (0 :: 1 :: 2 :: 3 :: 4 :: Nil)}
    	yield get_top(bigquery, iteration, i, 1).head
  }
  
  def get_top(bigquery : Bigquery, iteration : Int, wavelet : Int, ntop : Int) : Iterable[Long] =
  {
    get_bigquery_ids(bigquery, get_top_stuff_query(iteration, wavelet) + "\nLIMIT " + ntop.toString)
  }
  
  def get_old_hot_stuff(bigquery : Bigquery, iteration : Int) : Iterable[Long] =
  {
    for {i <- (10000.0 :: 1000.0 :: 100.0 :: 10.0 :: 1.0 :: 0.1 :: Nil)
      		query = get_old_hot_stuff_query(iteration, i) + "\nLIMIT 1"}
      yield get_bigquery_ids(bigquery, query).head
  }
  
  def build_entity(bigquery : Bigquery, iteration : Int) : Entity =
  {
    val top_stuff = get_top_stuff(bigquery, iteration)
    val old_stuff = get_old_hot_stuff(bigquery, iteration)
    val key = Key.newBuilder.addPathElement(Key.PathElement.newBuilder().setKind("hot_stuff").setId(iteration))
    val entity = Entity.newBuilder()
    entity.setKey(key)
    def list_to_value(list : Iterable[Long]) : Value =
    {
      val value = Value.newBuilder()
      for (id <- list)
        value.addListValue(Value.newBuilder().setIntegerValue(id))
      value.build()
    }
    entity.addProperty(Property.newBuilder.setName("top").setValue(list_to_value(top_stuff)))
    entity.addProperty(Property.newBuilder.setName("old").setValue(list_to_value(old_stuff)))
    entity.build()
  }
}
