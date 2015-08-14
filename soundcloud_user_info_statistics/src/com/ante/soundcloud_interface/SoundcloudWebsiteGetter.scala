package com.ante.soundcloud_interface

import java.util.concurrent.Future
import com.google.appengine.api.urlfetch
import com.ante.gae_interface.UrlFetcherWrapper.fetchWebsite
import net.liftweb.json
import scala.collection.mutable.ArrayBuilder
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.JObject
import net.liftweb.json.JField
import net.liftweb.json.JArray
import net.liftweb.json.JString

object SoundcloudWebsiteGetter
{
  	object SoundcloudErrorType extends Enumeration
  	{
  	  type SoundcloudErrorType = Value
  	  val NotAnError,
  	  	SoundcloudSaysTryAgain,
  	  	OtherDidntGetJson,
  	  	ObjectDeleted, // if a track or user has been deleted
  	  	InternalServerError,
  	  	ServiceUnavailable = Value
  	}
  	
  	case class SoundcloudError(errorType : SoundcloudErrorType.SoundcloudErrorType, text : String)
  
	def checkForKnownSoundcloudErrors(as_json : Option[JValue], text : String) : Either[SoundcloudError, JValue] =
	{
  	  if (as_json.isEmpty)
  	  {
  	      if (text.contains("Please reload the page or try again in a moment."))
  	        return Left(SoundcloudError(SoundcloudErrorType.SoundcloudSaysTryAgain, text))
  	      else
  	        return Left(SoundcloudError(SoundcloudErrorType.OtherDidntGetJson, text))
  	  }
  	  as_json.get match
      {
      case JObject(List(JField("errors", error_array : JArray))) =>
      {
        if (error_array.values.size != 1)
          throw new Exception("Can't handle the case of more than one error yet:\n" + text)
        val message = error_array.children(0) match
        {
          case JObject(List(JField("error_message", JString(msg)))) => msg
          case _ => throw new Exception("Expected object:\n" + text)
        }
        if (message.startsWith("404"))
          Left(SoundcloudError(SoundcloudErrorType.ObjectDeleted, message))
        else if (message.startsWith("500"))
          Left(SoundcloudError(SoundcloudErrorType.InternalServerError, message))
        else if (message.startsWith("503"))
          Left(SoundcloudError(SoundcloudErrorType.ServiceUnavailable, message))
        else
          throw new Exception("Unknown error message:\n" + message)
      }
      case _ => Right(as_json.get)
      }
	}
  
  
	def websiteText(future_response : Future[urlfetch.HTTPResponse]) : String =
	{
	  val response = future_response.get()
	  new String(response.getContent())
	}
	
	def jsonWebsiteContent(response : Future[urlfetch.HTTPResponse]) : Either[SoundcloudError, JValue] =
	{
	  val content = websiteText(response)
	  val as_json = json.parseOpt(content)
	  checkForKnownSoundcloudErrors(as_json, content)
	}
	
	def responseToUser(response : Future[urlfetch.HTTPResponse]) : Either[SoundcloudError, SoundcloudUser] =
	  jsonWebsiteContent(response) match
	  {
	    case Left(error) => Left(error)
	    case Right(json) => Right(SoundcloudUser.from_json(json))
	  }
	
	def responseToTrack(response : Future[urlfetch.HTTPResponse]) : Either[SoundcloudError, SoundcloudTrack] =
	  jsonWebsiteContent(response) match
	  {
	    case Left(error) => Left(error)
	    case Right(json) => Right(SoundcloudTrack.from_json(json))
	  }
	  
	def responseToTracks(response : Future[urlfetch.HTTPResponse]) : Either[SoundcloudError, Array[SoundcloudTrack]] =
	  jsonWebsiteContent(response) match
	  {
	    case Left(error) => Left(error)
	    case Right(json) => Right(json.children.toArray map SoundcloudTrack.from_json)
	  }
	  
	def getAllTracksForUser(url_func : (Int, Int, Int) => String, count_func : SoundcloudUser => Int)(id : Int)
		: Option[(SoundcloudUser, Array[SoundcloudTrack])] =
	{
	  val user_response = fetchWebsite(SoundcloudUrls.get_user_url(id))
	  import SoundcloudUrls.{max_limit, largest_offset}
	  def get_website(id : Int, limit : Int, index : Int)() =
	  {
	    fetchWebsite(url_func(id, limit, index))
	  }
	  val get_first_website = get_website(id, max_limit, 0)_
	  def get_tracks_ignore_errors(response : Future[urlfetch.HTTPResponse], try_again : () => Future[urlfetch.HTTPResponse]) : Array[SoundcloudTrack] =
	  {
		  responseToTracks(response) match
		  {
		    case Left(error) =>
		    {
		      def second_attempt() : Array[SoundcloudTrack] =
		      {
		    	Thread.sleep(1000)
		        responseToTracks(try_again()) match
		        {
		          case Left(error) => error.errorType match
		          {
		            case SoundcloudErrorType.ObjectDeleted => Array[SoundcloudTrack]()
		            case _ => throw new Exception("Soundcloud repeatedly returned errors for user " + id.toString + ":\n" + error.text)
		          }
		          case Right(tracks) => tracks
		        }
		      }
		      error.errorType match
		      {
		        case SoundcloudErrorType.SoundcloudSaysTryAgain => second_attempt()
		        case SoundcloudErrorType.OtherDidntGetJson => throw new Exception("Soundcloud didn't give me a valid json file for user " + id.toString + ":\n" + error.text)
		        case SoundcloudErrorType.ObjectDeleted => return Array[SoundcloudTrack]()
		        case SoundcloudErrorType.InternalServerError => second_attempt()
		        case SoundcloudErrorType.ServiceUnavailable => second_attempt()
		      }
		    }
		    case Right(tracks) => tracks
		  }
	  }
	  val track_response = get_first_website()
	  var user = responseToUser(user_response)
	  val tracks = get_tracks_ignore_errors(track_response, get_first_website)
	  def try_user_again()
	  {
	    Thread.sleep(1000)
	    user = responseToUser(fetchWebsite(SoundcloudUrls.get_user_url(id)))
	  }
	  if (user.isLeft)
	  {
	    val error = user.left.get
	    error.errorType match
		{
		  case SoundcloudErrorType.SoundcloudSaysTryAgain => try_user_again()
		  case SoundcloudErrorType.OtherDidntGetJson => throw new Exception("Soundcloud didn't give me a valid json file for user " + id.toString + ":\n" + error.text)
		  case SoundcloudErrorType.ObjectDeleted => return None
		  case SoundcloudErrorType.InternalServerError => try_user_again()
		  case SoundcloudErrorType.ServiceUnavailable => try_user_again()
		}
	    if (user.isLeft)
	    {
	      val error = user.left.get
	      error.errorType match
	      {
	        case SoundcloudErrorType.ObjectDeleted => return None
	        case _ => throw new Exception("Repeated error while getting user " + id.toString + ":\n" + error.text)
	      }
	    }
	  }
	  val success = user.right.get
	  val num_tracks_to_get = count_func(success)
	  if (num_tracks_to_get <= SoundcloudUrls.max_limit)
	  {
	    Some((success, tracks))
	  }
	  else
	  {
	    val further_responses = for (offset <- max_limit to Math.min(num_tracks_to_get, (largest_offset + 1)) by max_limit)
	      yield (get_website(id, max_limit, offset)(), get_website(id, max_limit, offset)_)
	      
	    val further_tracks = new ArrayBuilder.ofRef[SoundcloudTrack]()
	    for (response <- further_responses)
	      further_tracks ++= get_tracks_ignore_errors(response._1, response._2)
	    // turn into a set once to delete duplicates. this can happen if people add
	    // tracks while I'm getting the information. in that case the track can be
	    // both in the first batch and the second batch
	    val all_tracks = (tracks ++ further_tracks.result).toSet.toArray
	    Some((success, all_tracks))
	  }
	}
}


import org.scalatest._

class SoundcloudErrorSpec extends FlatSpec with Matchers
{
	import SoundcloudWebsiteGetter._
	"A 404 error" should "be recognized as a 404 error" in
	{
		val error_msg = """{"errors":[{"error_message":"404 - Not Found"}]}"""
		val as_json = json.parseOpt(error_msg)
		checkForKnownSoundcloudErrors(as_json, error_msg) should be (Left(SoundcloudError(SoundcloudErrorType.ObjectDeleted, "404 - Not Found")))
	}
}
