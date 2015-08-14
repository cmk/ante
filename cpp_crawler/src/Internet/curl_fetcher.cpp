#include "curl_fetcher.hpp"
#include <curl/curl.h>
#include "Debug/assert.hpp"

namespace
{
struct GetWebsiteState
{
	std::string content;
};

static size_t WriteCallback(char * ptr, size_t size, size_t nmemb, GetWebsiteState * state)
{
	size_t num_bytes = size * nmemb;
	state->content.append(ptr, num_bytes);
	return num_bytes;
}
}

CurlConnection::CurlConnection()
	: curl(curl_easy_init())
{
	curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
	curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, &WriteCallback);
	curl_easy_setopt(curl, CURLOPT_TIMEOUT, 60L);
}
CurlConnection::~CurlConnection()
{
	curl_easy_cleanup(curl);
}

std::string CurlConnection::FetchWebsite(const std::string & url)
{
	curl_easy_setopt(curl, CURLOPT_URL, url.c_str());
	GetWebsiteState state;
	curl_easy_setopt(curl, CURLOPT_WRITEDATA, &state);
	CURLcode result = curl_easy_perform(curl);
	if (result == CURLE_RECV_ERROR)
	{
		throw web_connection_error("Connection error:\n" + std::string(curl_easy_strerror(result)));
	}
	else if (result != CURLE_OK)
	{
		DE_THROW(web_connection_error("Connection error:\n" + std::string(curl_easy_strerror(result))));
	}
	return std::move(state.content);
}

