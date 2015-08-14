package com.ante.soundcloud_interface

object SoundcloudUrls
{
	// client_id for my test app, registered to my username
	// and simply titled "soundcloud_crawler"
	private def get_client_id() : String = "5529052630cbb1a05dfb3ab18074554e"
	private def get_client_id_argument() : String = "client_id=" + get_client_id
	
    // largest limit and offset according to soundcloud api:
    // http://developers.soundcloud.com/docs/api/guide#pagination
    // "The maximum value is 200 for limit and 8000 for offset."
	val max_limit = 200
	val largest_offset = 8000

	def get_user_url(user_id : Int) : String =
		"http://api.soundcloud.com/users/" + user_id.toString + ".json?" + get_client_id_argument
		
	def get_track_url(track_id : Long) : String =
		"http://api.soundcloud.com/tracks/" + track_id.toString + ".json?" + get_client_id_argument
  
	def get_followings_url(user_id : Int, limit : Int, offset : Int) : String =
		"http://api.soundcloud.com/users/" + user_id.toString +
		"/followings.json?" + get_client_id_argument +
		"&limit=" + limit.toString +
		"&offset=" + offset.toString
      
	def get_tracks_url(user_id : Int, limit : Int, offset : Int) : String =
		"http://api.soundcloud.com/users/" + user_id.toString +
		"/tracks.json?" + get_client_id_argument +
		"&limit=" + limit.toString +
		"&offset=" + offset.toString
		
	def get_latest_tracks_url(limit : Int, offset : Int) : String =
		"http://api.soundcloud.com/tracks.json?" + get_client_id_argument +
		"&limit=" + limit.toString +
		"&offset=" + offset.toString
	
	def get_favorites_url(user_id : Int, limit : Int, offset : Int) : String =
		"http://api.soundcloud.com/users/" + user_id.toString +
		"/favorites.json?" + get_client_id_argument +
		"&limit=" + limit.toString +
		"&offset=" + offset.toString
}

