package com.ante.gae_interface

import com.google.appengine.api.urlfetch
import java.util.concurrent.Future
import com.google.appengine.api.urlfetch.HTTPRequest
import com.google.appengine.api.urlfetch.HTTPMethod
import scala.io.Source
import java.net.URL

object UrlFetcherWrapper
{
	def fetchWebsite(url : String) : Future[urlfetch.HTTPResponse] =
	{
	  val fetcher = urlfetch.URLFetchServiceFactory.getURLFetchService()
	  import com.google.appengine.api.urlfetch.FetchOptions.Builder._
	  val request = new HTTPRequest(new java.net.URL(url), HTTPMethod.GET, withDeadline(10))
	  fetcher.fetchAsync(request)
	}
	def fetchWebsiteTextSync(url : String) : String =
	{
	  if (AppEngineWrapper.is_outside_appengine)
	    fetchWebsiteSyncOutside(url)
	  else
	    new String(fetchWebsiteSyncAppEngine(url).getContent())
	}
	
	private def fetchWebsiteSyncOutside(url : String) : String =
	{
	  val timeout = 60000
	  val conn = (new URL(url)).openConnection()
	  conn.setConnectTimeout(timeout)
	  conn.setReadTimeout(timeout)
	  val inputStream = conn.getInputStream()
	  try
	  {
	    Source.fromInputStream(inputStream).mkString
	  }
	  finally
	  {
	    inputStream.close()
	  }
	}
	
	private def fetchWebsiteSyncAppEngine(url : String) : urlfetch.HTTPResponse =
	{
	  val fetcher = urlfetch.URLFetchServiceFactory.getURLFetchService()
	  import com.google.appengine.api.urlfetch.FetchOptions.Builder._
	  val request = new HTTPRequest(new java.net.URL(url), HTTPMethod.GET, withDeadline(10))
	  fetcher.fetch(request)
	}
}
