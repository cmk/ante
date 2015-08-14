package com.ante.user_info_crawler

object AnteInfo
{
	val num_hours_per_iteration : Int = 21
	// one bucket per minute
	private val base_num_buckets : Int = num_hours_per_iteration * 60 
	
	def get_current_iteration() : Int =
		(System.currentTimeMillis() / (num_hours_per_iteration * 3600000)).toInt
		
	private def bucket_from_base_bucket(base_bucket : Int, num_external_buckets : Int) : Int =
		base_bucket / (base_num_buckets / num_external_buckets)
	
	// here's the idea: when I first set this up I was simply hashing all IDs
	// into one bucket per hour. so for example ID 1 would run near the beginning
	// of an iteration, and ID 20 would run near the end. but I realized that
	// that makes it difficult to change how often the cron job runs. if I want
	// to run the cron job every 30 minutes instead of once per hour, everything
	// would get reshuffled: ID 1 would still run near the beginning of an
	// iteration, but ID 20 would run in the middle now. which means that changes
	// to the cron job would introduce noise into the measurement.
	// so instead I now hash every ID down to a minute within an iteration, and
	// then if I want to run the cron job once per hour, I pick all IDs that
	// happen to fall within that hour. that way I can change the frequency
	// of the cron job at will without introducing noise. it's still difficult
	// to change the length of an iteration though.
	def get_bucket_for_id(id : Int, num_external_buckets : Int) : Int =
		bucket_from_base_bucket(id % base_num_buckets, num_external_buckets)
	
	def get_current_bucket(num_external_buckets : Int) : Int =
		bucket_from_base_bucket(((System.currentTimeMillis() / 60000) % base_num_buckets).toInt, num_external_buckets)
}

import org.scalatest._

class AnteInfoSpec extends FlatSpec with Matchers
{
	import AnteInfo._
	"ID 1 and 10" should "be in the same bucket when there are few buckets" in
	{
		val num_buckets = num_hours_per_iteration
		get_bucket_for_id(1, num_buckets) should be (get_bucket_for_id(10, num_buckets))
	}
	it should "not be in the same bucket when there are many buckets" in
	{
		val num_buckets = 10 * num_hours_per_iteration
		get_bucket_for_id(1, num_buckets) should not be (get_bucket_for_id(10, num_buckets))
	}
	"ID 1 and 100" should "not be in the same bucket when there are few buckets" in
	{
		val num_buckets = num_hours_per_iteration
		get_bucket_for_id(1, num_buckets) should not be (get_bucket_for_id(100, num_buckets))
	}
}
