package com.ante.user_info_crawler

import com.ante.soundcloud_interface.SoundcloudUser
import com.ante.soundcloud_interface.SoundcloudTrack
import com.google.appengine.api.datastore.KeyFactory
import com.google.appengine.api.datastore.Entity
import com.google.api.services.bigquery.model.TableRow
import com.google.api.services.bigquery.model.Table
import com.google.api.services.bigquery.model.TableReference
import com.google.api.services.bigquery.model.TableFieldSchema
import scala.collection.JavaConverters
import com.google.api.services.bigquery.model.TableSchema
import com.google.api.services.datastore.DatastoreV1.Property
import com.google.appengine.api.datastore.DatastoreService
import com.ante.gae_interface.DatastoreWrapper
import com.google.api.services.datastore.client.Datastore
import com.google.appengine.api.datastore.EntityNotFoundException
import com.ante.wavelet.StaticWaveletTransform
import org.scalatest.FlatSpec
import org.scalatest.Matchers


case class UserStatisticsSnapshot(
	id : Int,
	iteration : Int,
	followers_count : Int,
	followings_count : Int,
	playlist_count : Int,
	public_favorites_count : Int,
	track_count : Int,
	
	comment_count : Int,
	download_count : Int,
	playback_count : Long,
	favoritings_count : Int
)
{
  def this(user : SoundcloudUser, tracks : Iterable[SoundcloudTrack], iteration : Int) =
    this(user.id, iteration,
    	user.followers_count,
        user.followings_count,
        user.playlist_count,
        user.public_favorites_count,
        user.track_count,
        tracks.map(_.comment_count.getOrElse(0)).sum,
        tracks.map(_.download_count.getOrElse(0)).sum,
        tracks.map(_.playback_count.getOrElse(0).toLong).sum,
        tracks.map(_.favoritings_count.getOrElse(0)).sum)
}

object UserStatisticsSnapshot
{
  val entity_kind = "statistics_snapshot"
  
	def keyForSnapshot(snapshot : UserStatisticsSnapshot) : com.google.appengine.api.datastore.Key =
	  KeyFactory.createKey(entity_kind, (snapshot.id.toLong << 32l) | snapshot.iteration.toLong)
	  
	def entityForSnapshot(snapshot : UserStatisticsSnapshot) : Entity =
	{
		val entity = new Entity(keyForSnapshot(snapshot))
		// this one temporarily turned off because it isn't really
		// needed because it's already contained in the key for the
		// entity. and turns out that google charges me for this one.
		// using the key is a bit more annoying, but it's theoretically
		// equivalent
		//add_indexed_property("id", id)
		entity.setProperty("iteration", snapshot.iteration)
		import snapshot._
		// todo: java has ways of doing this automatically
		entity.setUnindexedProperty("followers_count", followers_count)
		entity.setUnindexedProperty("followings_count", followings_count)
		entity.setUnindexedProperty("playlist_count", playlist_count)
		entity.setUnindexedProperty("public_favorites_count", public_favorites_count)
		entity.setUnindexedProperty("track_count", track_count)
		
		entity.setUnindexedProperty("comment_count", comment_count)
		entity.setUnindexedProperty("download_count", download_count)
		entity.setUnindexedProperty("playback_count", playback_count)
		entity.setUnindexedProperty("favoritings_count", favoritings_count)
		entity
	}
	
	def snapshotForEntity(entity : Entity) : UserStatisticsSnapshot =
	{
		val key = entity.getKey().getId()
		val id = (key >> 32).toInt
		val iteration = key.toInt

		UserStatisticsSnapshot(id, iteration,
			entity.getProperty("followers_count").asInstanceOf[Long].toInt,
			entity.getProperty("followings_count").asInstanceOf[Long].toInt,
			entity.getProperty("playlist_count").asInstanceOf[Long].toInt,
			entity.getProperty("public_favorites_count").asInstanceOf[Long].toInt,
			entity.getProperty("track_count").asInstanceOf[Long].toInt,
			
			entity.getProperty("comment_count").asInstanceOf[Long].toInt,
			entity.getProperty("download_count").asInstanceOf[Long].toInt,
			entity.getProperty("playback_count").asInstanceOf[Long],
			entity.getProperty("favoritings_count").asInstanceOf[Long].toInt)
	}
}

case class TrackSnapshot(
	id : Int,
	iteration : Int,
	comment_count : Int,
	download_count : Int,
	playback_count : Int,
	favoritings_count : Int)
{
  def this(track : SoundcloudTrack, iteration : Int) =
	    this(track.id, iteration,
	        track.comment_count.getOrElse(0),
	        track.download_count.getOrElse(0),
	        track.playback_count.getOrElse(0),
	        track.favoritings_count.getOrElse(0))
}

object TrackSnapshot
{
  val playcount_table = "playcount"
  val entity_kind = "track_statistics_snapshot"
  
	def keyForTrack(track : TrackSnapshot) : com.google.appengine.api.datastore.Key =
	  KeyFactory.createKey(entity_kind, (track.id.toLong << 32l) | track.iteration.toLong)
	  
	def entityForTrack(track : TrackSnapshot) : Entity =
	{
		val entity = new Entity(keyForTrack(track))
		// this one temporarily turned off because it isn't really
		// needed because it's already contained in the key for the
		// entity. and turns out that google charges me for this one.
		// using the key is a bit more annoying, but it's theoretically
		// equivalent
		//add_indexed_property("id", id)
		entity.setProperty("iteration", track.iteration)
		import track._
		// todo: java has ways of doing this automatically
		entity.setUnindexedProperty("comment_count", comment_count)
		entity.setUnindexedProperty("download_count", download_count)
		entity.setUnindexedProperty("playback_count", playback_count)
		entity.setUnindexedProperty("favoritings_count", favoritings_count)
		entity
	}
	
	def trackForEntity(entity : Entity) : TrackSnapshot =
	{
		val key = entity.getKey().getId()
		val id = (key >> 32).toInt
		val iteration = key.toInt

		TrackSnapshot(id, iteration,
			entity.getProperty("comment_count").asInstanceOf[Long].toInt,
			entity.getProperty("download_count").asInstanceOf[Long].toInt,
			entity.getProperty("playback_count").asInstanceOf[Long].toInt,
			entity.getProperty("favoritings_count").asInstanceOf[Long].toInt)
	}
	def trackForEntity(entity : com.google.api.services.datastore.DatastoreV1.Entity) : TrackSnapshot =
	{
		val key = entity.getKey().getPathElement(0).getId()
		val id = (key >> 32).toInt
		val iteration = key.toInt
		
		def get_property(name : String) : Property =
		{
			for (i <- 0 until entity.getPropertyCount())
			{
			  val property = entity.getProperty(i)
		      if (property.getName() == name)
		        return property
			}
			return null
		}
		TrackSnapshot(id, iteration,
			get_property("comment_count").getValue().getIntegerValue().toInt,
			get_property("download_count").getValue().getIntegerValue().toInt,
			get_property("playback_count").getValue().getIntegerValue().toInt,
			get_property("favoritings_count").getValue().getIntegerValue().toInt)
	}
	
  def rowForTrack(track : TrackSnapshot) : TableRow =
  {
    import track._
	new TableRow().
		set("user_id", id).
	  	set("iteration", iteration).
	  	set("comment_count", comment_count).
	  	set("download_count", download_count).
	  	set("playback_count", playback_count).
	  	set("favoritings_count", favoritings_count)
  }
  
  val table_id = "track_snapshots"
  def tableForTracks(project_id : String, dataset_id : String) : Table =
  {
	  val reference = new TableReference().setProjectId(project_id).setDatasetId(dataset_id).setTableId(table_id)
	  val fields = new TableFieldSchema().setName("user_id").setType("INTEGER") ::
		  			new TableFieldSchema().setName("iteration").setType("INTEGER") ::
		  			new TableFieldSchema().setName("comment_count").setType("INTEGER") ::
		  			new TableFieldSchema().setName("download_count").setType("INTEGER") ::
		  			new TableFieldSchema().setName("playback_count").setType("INTEGER") ::
		  			new TableFieldSchema().setName("favoritings_count").setType("INTEGER") :: Nil
	  import JavaConverters._
	  new Table().setTableReference(reference).setSchema(new TableSchema().setFields(fields.asJava))
  }

  	def getStatisticsForTrack(datastore : DatastoreService, track_id : Int) : Array[TrackSnapshot] =
  	{
		val entities = DatastoreWrapper.getEntitiesForUser(datastore, track_id, entity_kind)
		(for {entity <- entities
			snapshot = TrackSnapshot.trackForEntity(entity)
			if (snapshot.playback_count != 0)}
				yield snapshot).toArray
  	}
  	
  	  def get_playcounts_for_track(datastore : DatastoreService, track_id : Long) : Map[Int, Long] =
	  {
  	    try
  	    {
  	    	val entity = datastore.get(KeyFactory.createKey(TrackSnapshot.playcount_table, track_id))
  	    	import collection.JavaConverters._
			(for {property <- entity.getProperties().asScala
			  	  iteration = property._1.toInt
			  	  playcount = property._2.asInstanceOf[Long]
			      if (playcount != 0)}
				yield (iteration -> playcount)).toMap
  	    }
  	    catch
  	    {
  	      case _ : EntityNotFoundException => Map()
  	    }
	  }
  	
  	def getStatisticsForTrack(datastore : Datastore, track_id : Int) : Array[TrackSnapshot] =
  	{
  	  val entities = DatastoreWrapper.getEntitiesForUser(datastore, track_id, entity_kind)
  	  (for {entity <- entities
			snapshot = TrackSnapshot.trackForEntity(entity)
			if (snapshot.playback_count != 0)}
		yield snapshot).toArray
  	}
  	
  def get_playcounts_for_track(datastore : Datastore, track_id : Int) : Map[Int, Long] =
  {
	import com.google.api.services.datastore.DatastoreV1.KindExpression
	import com.google.api.services.datastore.DatastoreV1.LookupRequest
	import com.google.api.services.datastore.DatastoreV1.Key
	implicit def stringToKind(kind : String) = KindExpression.newBuilder().setName(kind)
	import com.google.api.services.datastore.DatastoreV1.Query
	val table = TrackSnapshot.playcount_table
	val request = LookupRequest.newBuilder().addKey(Key.newBuilder().addPathElement(0, Key.PathElement.newBuilder().setId(track_id.toLong).setKind(table)))
	val results = datastore.lookup(request.build())
	if (results.getFoundCount() == 0)
	  return Map()
	val entity = results.getFound(0).getEntity();
	
	(for {i <- 0 until entity.getPropertyCount()
		  property = entity.getProperty(i)
		  iteration = property.getName().toInt
		  playcount = property.getValue.getIntegerValue()
		  if (playcount != 0)}
		yield (iteration -> playcount)).toMap
  }
  def cast_to_long(something : Object) : Long =
  {
    try
    {
      something.asInstanceOf[Long]
    }
    catch
    {
      case _ : ClassCastException => something.asInstanceOf[Int]
    }
  }
  def get_playcounts_for_track(entity : Entity) : Map[Int, Long] =
  {
	import JavaConverters._
    val properties = entity.getProperties.asScala
	(for {entry <- properties
		  iteration = entry._1.toInt
		  playcount = cast_to_long(entry._2)
		  if (playcount != 0)}
		yield (iteration -> playcount)).toMap
  }
  def playcounts_to_array(playcounts : Map[Int, Long]) : Array[Float] =
  {
    val sorted = playcounts.toArray.sorted
    val num_values = sorted.last._1 - sorted.head._1 + 1
    StaticWaveletTransform.build_array(sorted map (pair => new StaticWaveletTransform.WaveletSource(pair._1, pair._2)), sorted.last._1, num_values)
  }
  	
}


class ToArraySpec extends FlatSpec with Matchers
{
    val map = Map[Int, Long](18721 -> 17135, 18722 -> 33383, 18723 -> 56888, 18724 -> 76650)
	import TrackSnapshot._
	"a map with four values" should "give me four values back" in
	{
      val array = playcounts_to_array(map)
      array should be (Array(17135, 33383, 56888, 76650))
	}
}
