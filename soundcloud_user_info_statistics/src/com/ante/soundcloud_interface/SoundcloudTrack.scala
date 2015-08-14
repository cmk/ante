package com.ante.soundcloud_interface

import net.liftweb.json
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import com.ante.gae_interface.UrlFetcherWrapper

case class SoundcloudTrack(
	id : Int,
	permalink : String,
	permalink_url : String,
	
	title : String,
	// https://developers.soundcloud.com/docs/api/reference#artwork_url
	artwork_url : String,
	description : String,
	
	user_id : Int,
	// "public" or "private"
	sharing : String,
	// only exists if sharing is private
	shared_to_count : Option[Int],
	// "all", "me", or "none"
	embeddable_by : String,
	purchase_url : String,
	purchase_title : String,
	
	duration : Int,
	genre : String,
	// https://developers.soundcloud.com/docs/api/reference#tag_list
	tag_list : String,
    /*
     * “original”
     * “remix”
     * “live”
     * “recording”
     * “spoken”
     * “podcast”
     * “demo”
     * “in progress”
     * “stem”
     * “loop”
     * “sound effect”
     * “sample”
     * “other”
     */
	track_type : String,
	bpm : Option[Int],
	
	label_id : Option[Int],
	label_name : String,

	created_at : String,
	release_day : Option[Int],
	release_month : Option[Int],
	release_year : Option[Int],
	
	// it seems like the values for this can be null in json, which is then equivalent to false.
	// meaning there is true, false and null. or maybe just true and null
	// not sure how to handle that, so I make it an Option[Boolean]
	// but when we store it on our side it is only true or false
	streamable : Option[Boolean],
	// only exists if streamable is true
	stream_url : Option[String],
	downloadable : Boolean,
	// only exists if downloadble is true
	download_url : Option[String],
	download_count : Option[Int],
	/*
	 * “processing”
     * “failed”
     * “finished”
	 */
	state : String,
    /*
     * “no-rights-reserved”
     * “all-rights-reserved”
     * “cc-by”
     * “cc-by-nc”
     * “cc-by-nd”
     * “cc-by-sa”
     * “cc-by-nc-nd”
     * “cc-by-nc-sa”
     */
	license : String,
	
	waveform_url : String,
	video_url : String,
	
	commentable : Boolean,
	// only exists if commentable is true
	comment_count : Option[Int],
	
	isrc : String,
	key_signature : String,

	playback_count : Option[Int],
	favoritings_count : Option[Int],
	
	original_format : String,
	original_content_size : Option[Int]
)
{
}

object SoundcloudTrack
{
	def from_json(json_value : json.JValue) : SoundcloudTrack =
	{
		implicit val formats = json.DefaultFormats
		json_value.extract[SoundcloudTrack]
	}
	
	def from_json_text(json_text : String) : SoundcloudTrack =
		from_json(json.parse(json_text))
}

class TrackIdToSoundcloud extends HttpServlet
{
  override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
  {
    val id = SoundcloudUser.idFromParameter(req.getParameter("id"))
    val track_text = UrlFetcherWrapper.fetchWebsiteTextSync(SoundcloudUrls.get_track_url(id.toInt))
    val track = SoundcloudTrack.from_json_text(track_text)
    resp.sendRedirect(track.permalink_url)
  }
}
