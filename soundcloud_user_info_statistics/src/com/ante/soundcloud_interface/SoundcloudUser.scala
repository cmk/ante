package com.ante.soundcloud_interface

import net.liftweb.json
import com.google.appengine.api.datastore.DatastoreService
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import com.ante.gae_interface.UrlFetcherWrapper

case class SoundcloudUser(
  id : Int,
  permalink : String,

  followers_count : Int,
  followings_count : Int,
  playlist_count : Int,
  public_favorites_count : Int,
  track_count : Int,

  username : String,
  description : String,
  avatar_url : String,

  city : String,
  country : String,

  first_name : String,
  last_name : String,

  discogs_name : String,
  myspace_name : String,

  plan : String,

  website : String,
  website_title : String
) extends Ordered[SoundcloudUser]
{
  override def compare(that : SoundcloudUser) : Int =
    id - that.id
}

object SoundcloudUser
{
	def from_json(json_value : json.JValue) : SoundcloudUser =
	{
		implicit val formats = json.DefaultFormats
		json_value.extract[SoundcloudUser]
	}
	
	def from_json_text(json_text : String) : SoundcloudUser =
		from_json(json.parse(json_text))
		
	def store_user_deleted(id : Int, datastore : DatastoreService)
	{
	  // todo: implement. this gets called when we tried to get a user
	  // and that user doesn't exist anymore on soundclouds servers
	}
	
	def idFromParameter(param : String) : Int =
	{
	  val id = param.toLong
	  if (id > (2l << 32l))
	  {
	    (id >> 32).toInt
	  }
	  else
	    id.toInt
	}
}

class UserIdToSoundcloud extends HttpServlet
{
  override def doGet(req : HttpServletRequest, resp : HttpServletResponse)
  {
    val id = SoundcloudUser.idFromParameter(req.getParameter("id"))
    val user_text = UrlFetcherWrapper.fetchWebsiteTextSync(SoundcloudUrls.get_user_url(id.toInt))
    val user = SoundcloudUser.from_json_text(user_text)
    val url = "http://soundcloud.com/" + user.permalink
    resp.sendRedirect(url)
  }
}
