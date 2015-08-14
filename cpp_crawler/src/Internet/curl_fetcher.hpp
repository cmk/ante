#pragma once

#include <string>
#include <stdexcept>

typedef void CURL;

struct web_connection_error : std::runtime_error
{
	web_connection_error(const std::string & msg)
		: std::runtime_error(msg)
	{
	}
};

struct CurlConnection
{
	CurlConnection();
	~CurlConnection();

	std::string FetchWebsite(const std::string & url);

private:
	CURL * curl;
};

