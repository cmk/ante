#include "postgres_wrapper.hpp"
#include "postgresql/libpq-fe.h"
#include "Debug/assert.hpp"

PostgresConnection::PostgresConnection(const char * connection_string)
	: connection(PQconnectdb(connection_string))
{
	if (PQstatus(connection) != CONNECTION_OK)
	{
		std::string error_message = PQerrorMessage(connection);
		PQfinish(connection);
		DE_THROW(db_connection_error("Connection to database failed: " + std::move(error_message)));
	}
}
PostgresConnection::~PostgresConnection()
{
	PQfinish(connection);
}

std::string PostgresConnection::EscapeString(const std::string & str) const
{
	std::string result(str.size() * 2 + 1, '\0');
	int error;
	size_t size = PQescapeStringConn(connection, &*result.begin(), str.c_str(), str.size(), &error);
	if (error) DE_THROW(std::runtime_error("Error while escaping " + str));
	result.resize(size);
	return result;
}

void PostgresConnection::ExecCommand(const char * command)
{
	PostgresResult result = PQexec(connection, command);
	if (PQresultStatus(result.result) != PGRES_COMMAND_OK)
	{
		std::string error_message = PQresultErrorMessage(result.result);
		DE_THROW(exec_error("Error\n" + error_message + "\nin command\n" + command));
	}
}
PostgresResult PostgresConnection::ExecQuery(const char * command)
{
	PostgresResult result = PQexec(connection, command);
	if (PQresultStatus(result.result) != PGRES_TUPLES_OK)
	{
		std::string error_message = PQresultErrorMessage(result.result);
		DE_THROW(exec_error("Error\n" + error_message + "\nin query\n" + command));
	}
	return result;
}



PostgresResult::PostgresResult(PGresult * result)
	: result(result)
{
}
PostgresResult::~PostgresResult()
{
	if (result) PQclear(result);
}
int PostgresResult::GetNTuples() const
{
	return PQntuples(result);
}
char * PostgresResult::GetValue(int row, int column) const
{
	return PQgetvalue(result, row, column);
}


