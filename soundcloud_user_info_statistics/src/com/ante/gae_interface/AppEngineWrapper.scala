package com.ante.gae_interface

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.http.javanet.NetHttpTransport
import java.io.File

object AppEngineWrapper
{
  val project = "soundcloud-user-info"
  var is_outside_appengine = false
  
  val json_factory = new JacksonFactory()
  val http_transport = new NetHttpTransport()
    
  def authorizeFromOutside(scopes : Iterable[String]) : GoogleCredential =
  {
	import scala.collection.JavaConverters._
	val env = System.getenv().asScala
	val email = env("DATASTORE_SERVICE_ACCOUNT")
	val key_file = env("DATASTORE_PRIVATE_KEY_FILE")
	new GoogleCredential.Builder().setTransport(http_transport)
	    .setJsonFactory(json_factory)
	    .setServiceAccountId(email)
	    .setServiceAccountScopes(scopes.asJavaCollection)
	    .setServiceAccountPrivateKeyFromP12File(new File(key_file))
	    .build();
  }
}