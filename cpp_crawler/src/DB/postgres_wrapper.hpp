#pragma once

#include <string>
#include <stdexcept>

// forwrad declarations from libpq-fe.h
typedef struct pg_conn PGconn;
typedef struct pg_result PGresult;

struct PostgresResult;

struct db_connection_error : std::runtime_error
{
	db_connection_error(const std::string & msg)
		: std::runtime_error(msg)
	{
	}
};

struct exec_error : std::runtime_error
{
	exec_error(const std::string & msg)
		: std::runtime_error(msg)
	{
	}
};

struct PostgresConnection
{
	static constexpr const char * default_connection_string =
			"host=soundcloud.ccnef1pomtpa.us-east-1.rds.amazonaws.com "
			"dbname=soundcloud "
			"user=soundcloud_user "
			"password=3r78h13evwwd0sf8g43ggt";
	PostgresConnection(const char * connection_string = default_connection_string);
	~PostgresConnection();

	std::string EscapeString(const std::string & str) const;

	void ExecCommand(const char * command);
	PostgresResult ExecQuery(const char * command);

private:
	PGconn * connection;
};

struct PostgresResult
{
	friend struct PostgresConnection;

	PostgresResult(PGresult * result);
	PostgresResult(PostgresResult && other)
		: result(other.result)
	{
		other.result = nullptr;
	}
	PostgresResult & operator=(PostgresResult && other)
	{
		std::swap(result, other.result);
		return *this;
	}
	~PostgresResult();

	int GetNTuples() const;
	char * GetValue(int row, int column) const;

private:
	PGresult * result;
};



