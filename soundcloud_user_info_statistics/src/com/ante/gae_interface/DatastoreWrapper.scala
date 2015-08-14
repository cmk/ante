package com.ante.gae_interface

import com.google.appengine.api.datastore.DatastoreService
import com.google.appengine.api.datastore.DatastoreServiceFactory
import com.google.appengine.api.datastore.KeyFactory
import com.google.api.services.datastore.client.Datastore
import com.google.api.services.datastore.client.DatastoreFactory
import com.google.api.services.datastore.client.DatastoreOptions
import com.google.api.services.datastore.DatastoreV1.KindExpression
import com.google.api.services.datastore.DatastoreV1.CompositeFilter
import com.google.api.services.datastore.DatastoreV1.PropertyFilter
import com.google.api.services.datastore.DatastoreV1.PropertyReference
import com.google.api.services.datastore.DatastoreV1.Value
import com.google.api.services.datastore.DatastoreV1.Key
import com.google.api.services.datastore.DatastoreV1.Property
import com.google.api.services.datastore.DatastoreV1.Filter
import com.google.api.services.datastore.DatastoreV1.RunQueryRequest
import scala.collection.JavaConverters

object DatastoreWrapper
{
	def connect_to_google_database() : DatastoreService =
		DatastoreServiceFactory.getDatastoreService()
	
	def connect_from_outside() : Datastore =
	{
      val scopes = "https://www.googleapis.com/auth/datastore" :: "https://www.googleapis.com/auth/userinfo.email" :: Nil
      DatastoreFactory.get().create(new DatastoreOptions.Builder().credential(AppEngineWrapper.authorizeFromOutside(scopes)).dataset(AppEngineWrapper.project).build());
	}
		
	def getEntitiesForUser(datastore : DatastoreService, user_id : Int, table : String) : Iterable[com.google.appengine.api.datastore.Entity] =
	{
	  import com.google.appengine.api.datastore.Entity
      import com.google.appengine.api.datastore.Query
	  val query = new Query(table)
	  query.addFilter(Entity.KEY_RESERVED_PROPERTY, Query.FilterOperator.GREATER_THAN_OR_EQUAL, KeyFactory.createKey(table, user_id.toLong << 32))
	  query.addFilter(Entity.KEY_RESERVED_PROPERTY, Query.FilterOperator.LESS_THAN, KeyFactory.createKey(table, (user_id + 1).toLong << 32))
	  val results = datastore.prepare(query)
	  import scala.collection.JavaConverters._
	  results.asIterable().asScala
	}
	def getEntitiesForUser(datastore : Datastore, user_id : Int, table : String) : Iterable[com.google.api.services.datastore.DatastoreV1.Entity] =
	{
		implicit def stringToKind(kind : String) = KindExpression.newBuilder().setName(kind)
	    import com.google.api.services.datastore.DatastoreV1.Query
		val query = Query.newBuilder().addKind(table)
		val property = PropertyReference.newBuilder().setName(com.google.appengine.api.datastore.Entity.KEY_RESERVED_PROPERTY)
		implicit def id_to_value(id : Long) : Value =
		{
		  val key = Key.newBuilder().addPathElement(0, Key.PathElement.newBuilder().setId(id).setKind(table))
		  Value.newBuilder().setKeyValue(key).build()
		}
		val min_filter : PropertyFilter = PropertyFilter.newBuilder().setProperty(property).setOperator(PropertyFilter.Operator.GREATER_THAN_OR_EQUAL).setValue(user_id.toLong << 32).build()
		val max_filter : PropertyFilter = PropertyFilter.newBuilder().setProperty(property).setOperator(PropertyFilter.Operator.LESS_THAN).setValue((user_id + 1).toLong << 32).build()
		val filter = CompositeFilter.newBuilder().addFilter(Filter.newBuilder().setPropertyFilter(min_filter)).addFilter(Filter.newBuilder().setPropertyFilter(max_filter)).setOperator(CompositeFilter.Operator.AND)
		query.setFilter(Filter.newBuilder().setCompositeFilter(filter))
		val results = datastore.runQuery(RunQueryRequest.newBuilder().setQuery(query).build())
		val batch = results.getBatch()
    	import JavaConverters._
    	batch.getEntityResultList().asScala map (_.getEntity())
	}
	
	def put(datastore : DatastoreService, entities : Iterable[com.google.appengine.api.datastore.Entity]) =
	{
	  import scala.collection.JavaConverters._
	  datastore.put(entities.asJava)
	}
}
