#include <iostream>

#include "rapidjson/document.h"
#include <fstream>
#include <stdexcept>
#include <string>
#include <map>
#include <sstream>
#include <memory>
#include <vector>
#include <Debug/assert.hpp>
#include <chrono>
#include <thread>
#include "DB/postgres_wrapper.hpp"
#include "Graph/pagerank.hpp"
#include "Internet/curl_fetcher.hpp"
#include <algorithm>
#include <mutex>
#include <condition_variable>
#include <set>

struct SimpleMeasurer
{
	SimpleMeasurer(const char * name)
		: name(name), before(std::chrono::high_resolution_clock::now())
	{
	}
	~SimpleMeasurer()
	{
		auto duration = std::chrono::high_resolution_clock::now() - before;
		std::cout << std::this_thread::get_id() << ", " << name << ": " << std::chrono::duration_cast<std::chrono::milliseconds>(duration).count() << "ms" << std::endl;
	}

private:
	const char * name;
	std::chrono::high_resolution_clock::time_point before;
};

std::string read_text(std::ifstream & file)
{
	std::string result;
	file.seekg(0, std::ifstream::end);
	result.resize(file.tellg());
	file.seekg(0, std::ifstream::beg);
	file.read(&result[0], result.size());
	return result;
}

std::string read_file(const std::string & filename)
{
	std::ifstream file(filename);
	if (!file) DE_THROW(std::runtime_error("Couldn't open file " + filename));
	return read_text(file);
}

struct SoundcloudUser
{
	SoundcloudUser(int id, std::string permalink)
		: id(id), permalink(std::move(permalink))
	{
	}

	int id;

	int followers_count = 0;
	int followings_count = 0;
	int playlist_count = 0;
	int public_favorites_count = 0;
	int track_count = 0;

	std::string permalink;
	std::string username;
	std::string description;
	std::string avatar_url;

	std::string city;
	std::string country;

	std::string first_name;
	std::string last_name;
	std::string discogs_name;
	std::string myspace_name;

	std::string plan;

	std::string website;
	std::string website_title;
};

template<typename T>
bool is_json_type(const rapidjson::Value & object);
template<>
bool is_json_type<int>(const rapidjson::Value & object)
{
	return object.IsInt();
}
template<>
bool is_json_type<std::string>(const rapidjson::Value & object)
{
	return object.IsString();
}

template<typename T>
T get_from_json(const rapidjson::Value & object);

template<>
int get_from_json(const rapidjson::Value & object)
{
	if (is_json_type<int>(object)) return object.GetInt();
	else DE_THROW(std::runtime_error("type mismatch, expected int"));
}
template<>
std::string get_from_json(const rapidjson::Value & object)
{
	if (is_json_type<std::string>(object)) return object.GetString();
	else DE_THROW(std::runtime_error("type mismatch, expected string"));
}

template<typename T>
T get_json_member(const rapidjson::Value & object, const char * child_name)
{
	if (!object.HasMember(child_name)) DE_THROW(std::runtime_error("Couldn't find the member " + std::string(child_name)));
	return get_from_json<T>(object[child_name]);
}

template<typename T>
void set_if_member(const rapidjson::Value & object, const char * child_name, T & to_set)
{
	const rapidjson::Value & member = object[child_name];
	if (!is_json_type<T>(member)) return;
	to_set = get_from_json<T>(member);
}

SoundcloudUser user_from_json(const rapidjson::Value & user)
{
	SoundcloudUser result(get_json_member<int>(user, "id"), get_json_member<std::string>(user, "permalink"));
#define SET_MEMBER(name) set_if_member(user, #name, result.name)
	SET_MEMBER(avatar_url);
	SET_MEMBER(username);
	SET_MEMBER(city);
	SET_MEMBER(country);
	SET_MEMBER(description);
	SET_MEMBER(first_name);
	SET_MEMBER(last_name);
	SET_MEMBER(discogs_name);
	SET_MEMBER(myspace_name);
	SET_MEMBER(plan);
	SET_MEMBER(website);
	SET_MEMBER(website_title);
	SET_MEMBER(followers_count);
	SET_MEMBER(followings_count);
	SET_MEMBER(playlist_count);
	SET_MEMBER(public_favorites_count);
	SET_MEMBER(track_count);
#undef SET_MEMBER
	return result;
}

SoundcloudUser read_user_from_json_text(std::string json_text)
{
	rapidjson::Document document;
	document.ParseInsitu<0>(const_cast<char *>(json_text.c_str()));
	if (!document.IsObject()) DE_THROW(std::runtime_error("expected an object as the top level json oject"));
	return user_from_json(document);
}

SoundcloudUser read_user_from_disk(const std::string & filename)
{
	return read_user_from_json_text(read_file(filename));
}

bool is_in_db(PostgresConnection & connection, const SoundcloudUser & user)
{
	std::string query = "SELECT id from users WHERE id = " + std::to_string(user.id) + ";";
	return connection.ExecQuery(query.c_str()).GetNTuples() == 1;
}
std::map<std::string, std::string> db_key_values(PostgresConnection & connection, const SoundcloudUser & user)
{
	std::map<std::string, std::string> to_insert;
	to_insert["last_updated"] = "now()";
#define ADD_STRING_TO_MAP(name) if (!user.name.empty()) to_insert[#name] = "'" + connection.EscapeString(user.name) + "'"
#define ADD_INT_TO_MAP(name) to_insert[#name] = std::to_string(user.name);
	ADD_INT_TO_MAP(id);
	ADD_STRING_TO_MAP(permalink);
	ADD_STRING_TO_MAP(avatar_url);
	ADD_STRING_TO_MAP(city);
	ADD_STRING_TO_MAP(country);
	ADD_STRING_TO_MAP(description);
	ADD_STRING_TO_MAP(first_name);
	ADD_STRING_TO_MAP(last_name);
	ADD_STRING_TO_MAP(discogs_name);
	ADD_STRING_TO_MAP(myspace_name);
	ADD_STRING_TO_MAP(plan);
	ADD_STRING_TO_MAP(username);
	ADD_STRING_TO_MAP(website);
	ADD_STRING_TO_MAP(website_title);
	ADD_INT_TO_MAP(followers_count);
	ADD_INT_TO_MAP(followings_count);
	ADD_INT_TO_MAP(playlist_count);
	ADD_INT_TO_MAP(public_favorites_count);
	ADD_INT_TO_MAP(track_count);
#undef ADD_INT_TO_MAP
#undef ADD_STRING_TO_MAP
	return to_insert;
}

void build_update_or_insert_string(PostgresConnection & connection, const SoundcloudUser & user, std::stringstream & command)
{
	std::map<std::string, std::string> to_insert = db_key_values(connection, user);
	command << "UPDATE users SET ";
	const char * separator = "";
	for (auto && pair : to_insert)
	{
		if (pair.first == "id") continue;
		command << separator << pair.first << " = " << pair.second;
		separator = ", ";
	}
	command << " WHERE id = " << user.id;
	command << "; INSERT INTO users (";
	separator = "";
	for (auto && pair : to_insert)
	{
		command << separator << pair.first;
		separator = ", ";
	}
	command << ") SELECT ";
	separator = "";
	for (auto && pair : to_insert)
	{
		command << separator << pair.second;
		separator = ", ";
	}
	command << " WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = " << user.id;
	command << ");";
}

void update_or_insert(PostgresConnection & connection, const SoundcloudUser & user)
{
	std::stringstream command;
	build_update_or_insert_string(connection, user, command);
	connection.ExecCommand(command.str().c_str());
}

struct ScopedDisableAutocommit
{
	ScopedDisableAutocommit(PostgresConnection & connection)
		: connection(connection)
	{
		connection.ExecCommand("BEGIN");
	}
	~ScopedDisableAutocommit()
	{
		connection.ExecCommand("COMMIT");
	}

private:
	PostgresConnection & connection;
};

struct FollowerUpdateQueue
{
	FollowerUpdateQueue()
		: update_thread([this]{ queue_thread_func(); })
	{
	}
	~FollowerUpdateQueue()
	{
		finished = true;
		update_thread.join();
	}

	void add_user(SoundcloudUser user)
	{
		std::vector<SoundcloudUser> users;
		users.push_back(std::move(user));
		std::unique_lock<std::mutex> lock(mutex);
		available_cv.wait(lock, [&]{ return !is_full(); });
		users_to_update_or_insert.push_back(std::move(users));
		lock.unlock();
		data_cv.notify_one();
	}

	void add_users_and_followings(std::vector<SoundcloudUser> users, std::pair<int, std::vector<int> > followings)
	{
		std::unique_lock<std::mutex> lock(mutex);
		available_cv.wait(lock, [&]{ return !is_full(); });
		users_to_update_or_insert.push_back(std::move(users));
		followings_to_insert.push_back(std::move(followings));
		lock.unlock();
		data_cv.notify_one();
	}

private:
	bool finished = false;
	std::mutex mutex;
	std::condition_variable data_cv;
	std::condition_variable available_cv;
	typedef std::vector<std::vector<SoundcloudUser> > user_queue;
	typedef std::vector<std::pair<int, std::vector<int> > > follower_queue;
	user_queue users_to_update_or_insert;
	follower_queue followings_to_insert;

	struct ThreadContext
	{
		user_queue users;
		follower_queue followings;

		void set_local_values(FollowerUpdateQueue & queue)
		{
			users.clear();
			followings.clear();
			std::unique_lock<std::mutex> lock(queue.mutex);
			queue.data_cv.wait(lock, [&queue]{ return queue.can_update(); });
			users.swap(queue.users_to_update_or_insert);
			followings.swap(queue.followings_to_insert);
			lock.unlock();
			queue.available_cv.notify_all();
		}
	};
	std::thread update_thread;

	void queue_thread_func()
	{
		PostgresConnection connection;
		ThreadContext context;
		while (!finished)
		{
			update_once(connection, context);
		}
		if (can_update())
		{
			update_once(connection, context);
		}
	}

	bool can_update() const
	{
		return !users_to_update_or_insert.empty() || !followings_to_insert.empty();;
	}

	bool is_full() const
	{
		return followings_to_insert.size() > 20;
	}

	void update_once(PostgresConnection & connection, ThreadContext & context)
	{
		context.set_local_values(*this);
		update_users(connection, context.users);
		insert_following(connection, context.followings);
	}

	struct DetermineWhatToUpdate
	{
		std::vector<SoundcloudUser> remove_existing_users(PostgresConnection & connection, std::vector<SoundcloudUser> users)
		{
			update_existing_users(connection, users);
			users.erase(std::remove_if(users.begin(), users.end(), [&](const SoundcloudUser & user)
			{
				return existing_users.find(user.id) != existing_users.end();
			}), users.end());
			return users;
		}

	private:
		void update_existing_users(PostgresConnection & connection, const std::vector<SoundcloudUser> & users)
		{
			auto is_new = [this](const SoundcloudUser & user)
			{
				return existing_users.find(user.id) == existing_users.end();
			};

			if (std::none_of(users.begin(), users.end(), is_new))
				return;
			std::stringstream query;
			query << "SELECT users.id FROM users WHERE users.id IN (";
			const char * separator = "";
			for (const SoundcloudUser & user : users)
			{
				if (!is_new(user))
					continue;
				query << separator << user.id;
				separator = ", ";
			}
			query << ");";
			PostgresResult result = connection.ExecQuery(query.str().c_str());
			for (int i = 0, end = result.GetNTuples(); i < end; ++i)
			{
				existing_users.insert(std::atoi(result.GetValue(i, 0)));
			}
		}
		std::set<int> existing_users;
	};

	DetermineWhatToUpdate what_to_update;

	static void insert_users(PostgresConnection & connection, const std::vector<SoundcloudUser> & users)
	{
		if (users.empty())
			return;

		ScopedDisableAutocommit disable_auto_commit(connection);
		for (const SoundcloudUser & user : users)
		{
			std::map<std::string, std::string> insert = db_key_values(connection, user);
			std::stringstream command;
			command << "INSERT INTO users (";
			const char * separator = "";
			for (auto && pair : insert)
			{
				command << separator << pair.first;
				separator = ", ";
			}
			command << ") VALUES (";
			separator = "";
			for (auto && pair : insert)
			{
				command << separator << pair.second;
				separator = ", ";
			}
			command << ");";
			connection.ExecCommand(command.str().c_str());
		}
	}

	void update_users(PostgresConnection & connection, user_queue & users)
	{
		if (users.empty())
			return;
		SimpleMeasurer measure("update_followings");
		std::vector<SoundcloudUser> flattened;
		flattened.reserve(std::accumulate(users.begin(), users.end(), size_t(), [](size_t lhs, std::vector<SoundcloudUser> & rhs){ return lhs + rhs.size(); }));
		for (std::vector<SoundcloudUser> & user_list : users)
		{
			flattened.insert(flattened.end(), std::make_move_iterator(user_list.begin()), std::make_move_iterator(user_list.end()));
		}
		std::sort(flattened.begin(), flattened.end(), [](const SoundcloudUser & lhs, const SoundcloudUser & rhs){ return lhs.id < rhs.id; });
		flattened.erase(std::unique(flattened.begin(), flattened.end(), [](const SoundcloudUser & lhs, const SoundcloudUser & rhs){ return lhs.id == rhs.id; }), flattened.end());
		flattened = what_to_update.remove_existing_users(connection, std::move(flattened));
		insert_users(connection, flattened);
		/*std::stringstream command;
		command << "BEGIN;";
		for (const SoundcloudUser & user : flattened)
		{
			build_update_or_insert_string(connection, user, command);
		}
		command << "COMMIT;";
		connection.ExecCommand(command.str().c_str());*/
	}

	static void insert_following(PostgresConnection & connection, const follower_queue & followings)
	{
		if (followings.empty())
			return;
		SimpleMeasurer measure("insert_followings");
		std::stringstream command;
		command << "INSERT INTO following (follower, following) VALUES ";
		const char * separator = "";
		for (auto && pair : followings)
		{
			for (int following : pair.second)
			{
				command << separator << "(" << pair.first << ", " << following << ")";
				separator = ", ";
			}
		}
		command << ";";
		connection.ExecCommand(command.str().c_str());
	}
};

static const std::string & get_client_id()
{
	static std::string result = "5529052630cbb1a05dfb3ab18074554e";
	return result;
}

std::string get_user_url(int user_id)
{
	return "http://api.soundcloud.com/users/" + std::to_string(user_id) + ".json?client_id=" + get_client_id();
}
std::string get_followings_url(int user_id, int limit, int offset)
{
	std::stringstream url_builder;
	url_builder << "http://api.soundcloud.com/users/" << user_id;
	url_builder << "/followings.json?client_id=" + get_client_id();
	url_builder << "&limit=" << limit;
	url_builder << "&offset=" << offset;
	return url_builder.str();
}

enum KnownSoundcloudErrors
{
	NotAnError,
	UserDeleted,
	InternalServerError,
	ServiceUnavailable,
	SoundcloudSaysTryAgain,
	OtherDidntGetJson
};

struct soundcloud_error : std::runtime_error
{
	soundcloud_error(KnownSoundcloudErrors error_enum, const std::string & msg)
		: std::runtime_error(msg), error_enum(error_enum)
	{
	}

	KnownSoundcloudErrors error_enum;
};

std::pair<KnownSoundcloudErrors, const char *> check_for_soundcloud_errors(const rapidjson::Document & document, const std::string & text)
{
	if (document.HasParseError())
	{
		if (text.find("Please reload the page or try again in a moment.") != std::string::npos)
		{
			return { SoundcloudSaysTryAgain, text.c_str() };
		}
		else return { OtherDidntGetJson, text.c_str() };
	}
	if (!document.IsObject()) return { NotAnError, "" };
	if (!document.HasMember("errors")) return { NotAnError, "" };
	const rapidjson::Value & array = document["errors"];
	if (!array.IsArray()) DE_THROW(std::runtime_error("Expected array"));
	if (array.Size() != 1) DE_THROW(std::runtime_error("Can't handle the case of more than one error yet."));
	size_t index = 0;
	const rapidjson::Value & error_object = array[index];
	if (!error_object.IsObject()) DE_THROW(std::runtime_error("Expected object"));
	const rapidjson::Value & string = error_object["error_message"];
	if (!string.IsString()) DE_THROW(std::runtime_error("Expected string"));
	if (string.GetStringLength() >= 3 && std::equal(string.GetString(), string.GetString() + 3, "404"))
	{
		return { UserDeleted, string.GetString() };
	}
	else if (string.GetStringLength() >= 3 && std::equal(string.GetString(), string.GetString() + 3, "500"))
	{
		return { InternalServerError, string.GetString() };
	}
	else if (string.GetStringLength() >= 3 && std::equal(string.GetString(), string.GetString() + 3, "503"))
	{
		return { ServiceUnavailable, string.GetString() };
	}
	DE_THROW(std::runtime_error("Unknown error message:\n" + std::string(string.GetString())));
}

void log_error(const std::string & message)
{
	std::cerr << message << std::endl;
}

SoundcloudUser fetch_user_from_soundcloud(CurlConnection & connection, int user_id)
{
	SimpleMeasurer measure("fetch_user");
	std::string content = connection.FetchWebsite(get_user_url(user_id));
	rapidjson::Document document;
	document.ParseInsitu<0>(const_cast<char *>(content.c_str()));

	auto error = check_for_soundcloud_errors(document, content);
	switch(error.first)
	{
	case NotAnError:
		return user_from_json(document);
	case UserDeleted:
	case SoundcloudSaysTryAgain:
		throw soundcloud_error(error.first, error.second);
	default:
		DE_THROW(soundcloud_error(error.first, error.second));
	}
}

void followings_from_json(const rapidjson::Value & followings, std::vector<SoundcloudUser> & out)
{
	if (!followings.IsArray()) DE_THROW(std::runtime_error("Expected json array"));
	for (size_t i = 0, end = followings.Size(); i < end; ++i)
	{
		const rapidjson::Value & user = followings[i];
		if (!user.IsObject()) DE_THROW(std::runtime_error("Expected json object"));
		out.push_back(user_from_json(user));
	}
}

void read_followings_from_json_text(std::string json_text, std::vector<SoundcloudUser> & out)
{
	rapidjson::Document document;
	document.ParseInsitu<0>(const_cast<char *>(json_text.c_str()));

	auto error = check_for_soundcloud_errors(document, json_text);
	switch(error.first)
	{
	case NotAnError:
		return followings_from_json(document, out);
	case UserDeleted:
	case SoundcloudSaysTryAgain:
		throw soundcloud_error(error.first, error.second);
	default:
		DE_THROW(soundcloud_error(error.first, error.second));
	}
}

std::vector<SoundcloudUser> fetch_followings(CurlConnection & curl_connection, const SoundcloudUser & user)
{
	SimpleMeasurer measure("fetch_followings");
	std::vector<SoundcloudUser> result;
	result.reserve(user.followings_count);
	// largest limit and offset according to soundcloud api:
	// http://developers.soundcloud.com/docs/api/guide#pagination
	// "The maximum value is 200 for limit and 8000 for offset."
	constexpr int limit = 200;
	constexpr int largest_offset = 8000;
	for (int i = 0, loop_end = std::min(largest_offset + 1, user.followings_count); i < loop_end; i += limit)
	{
		read_followings_from_json_text(curl_connection.FetchWebsite(get_followings_url(user.id, limit, i)), result);
	}
	// sort by id
	std::sort(result.begin(), result.end(), [](const SoundcloudUser & lhs, const SoundcloudUser & rhs)
	{
		return lhs.id < rhs.id;
	});
	// remove duplicate ids. this happens sometimes if soundclouds database
	// changes while I'm getting values. for example a user might be at
	// position 199, then the person I'm looking at follows somebody new and
	// all of a sudden that user is at position 200, so I will get the same
	// user again in my next request
	result.erase(std::unique(result.begin(), result.end(), [](const SoundcloudUser & lhs, const SoundcloudUser & rhs)
	{
		return lhs.id == rhs.id;
	}), result.end());
	return result;
}

void mark_as_deleted(PostgresConnection & connection, int user_id)
{
	std::string command = "UPDATE users SET deleted = true WHERE id = " + std::to_string(user_id) + ";";
	connection.ExecCommand(command.c_str());
}

void update_followings(FollowerUpdateQueue & queue, CurlConnection & curl_connection, const SoundcloudUser & user)
{
	std::vector<SoundcloudUser> followings = fetch_followings(curl_connection, user);
	std::vector<int> following_ids;
	following_ids.reserve(followings.size());
	for (const SoundcloudUser & user : followings)
	{
		following_ids.push_back(user.id);
	}
	queue.add_users_and_followings(std::move(followings), { user.id, std::move(following_ids) });
}

std::vector<int> query_result_ids(const PostgresResult & result)
{
	int num_rows = result.GetNTuples();
	std::vector<int> ids;
	ids.reserve(num_rows);
	for (int i = 0; i < num_rows; ++i)
	{
		char * value = result.GetValue(i, 0);
		ids.push_back(atoi(value));
	}
	return ids;
}

static std::string get_to_update_default_filter()
{
	return
	"users.deleted = false"
	" AND users.followings_count >= 5" // there is some inconsistency in soundcloud's
								// database about the number of people that
								// someone is following. for example 'nervomusic'
								// shows up as following one person but is actually
								// not following anyone
								// so just ignore users that are following very few
								// people
	;
}
static std::string get_to_update_new_user_filter()
{
	return "NOT EXISTS (SELECT 1 FROM following WHERE users.id = following.follower)";
}

std::vector<int> get_users_to_update_by_pagerank(PostgresConnection & sql_connection)
{
	std::string query =
	"SELECT "
		"users.id"
	" FROM "
		"users,"
		"pageranks"
	" WHERE "
		"users.id = pageranks.id AND "
			+ get_to_update_default_filter() +
		" AND pageranks.pagerank > 0.01 "
		" AND pageranks.date = current_date AND "
			+ get_to_update_new_user_filter() +
	" ORDER BY "
		"pageranks.pagerank DESC;";
	return query_result_ids(sql_connection.ExecQuery(query.c_str()));
}

std::vector<int> get_users_to_update_by_num_followers(PostgresConnection & sql_connection)
{
	std::string query =
	"SELECT "
		"users.id"
	" FROM "
		"users"
	" WHERE "
		"users.followers_count >= 1000 AND " // 1000 is kinda chosen arbitrarily. we should probably lower this step by step
			+ get_to_update_default_filter() + " AND "
			+ get_to_update_new_user_filter() +
	" ORDER BY "
		"users.followers_count DESC;";
	return query_result_ids(sql_connection.ExecQuery(query.c_str()));
}

std::vector<int> compute_users_to_update(PostgresConnection & sql_connection)
{
	SimpleMeasurer measure("compute_users_to_update");
	std::vector<int> by_pagerank = get_users_to_update_by_pagerank(sql_connection);
	if (!by_pagerank.empty()) return by_pagerank;
	return get_users_to_update_by_num_followers(sql_connection);
}

struct IgnoreOneError
{
	void RememberSuccess()
	{
		succeeded_once = true;
	}
	void PossiblyThrowError()
	{
		if (succeeded_once) succeeded_once = false;
		else DE_THROW();
	}

private:
	bool succeeded_once = false;
};

struct UpdaterThread
{
	UpdaterThread(std::vector<int> to_update, FollowerUpdateQueue & queue)
		: to_update(std::move(to_update)), queue(queue)
	{
	}
	void operator()()
	{
		PostgresConnection sql_connection;
		CurlConnection curl_connection;
		IgnoreOneError on_error;
		for (int user_id : to_update)
		{
			try
			{
				SoundcloudUser user = fetch_user_from_soundcloud(curl_connection, user_id);
				queue.add_user(user);
				update_followings(queue, curl_connection, user);
				on_error.RememberSuccess();
			}
			catch(soundcloud_error & error)
			{
				if (error.error_enum == UserDeleted)
				{
					mark_as_deleted(sql_connection, user_id);
					continue;
				}
				log_error("Error with user " + std::to_string(user_id) + ":\n" + error.what());
				on_error.PossiblyThrowError();
				switch(error.error_enum)
				{
				case NotAnError:
					DE_THROW(std::logic_error("Received an exception that claims to not be an error. Something is wrong."));
				case InternalServerError:
				case SoundcloudSaysTryAgain:
					// sleep for two seconds to give soundcloud a chance
					// to recover then try the next iteration
					std::this_thread::sleep_for(std::chrono::seconds(2));
					break;
				case ServiceUnavailable:
					// sleep a bit longer to give soundcloud a chance to
					// recover then try the next iteration
					std::this_thread::sleep_for(std::chrono::seconds(10));
					break;
				case OtherDidntGetJson:
					log_error("Soundcloud didn't give me a valid json file:\n\n" + std::string(error.what()));
					std::this_thread::sleep_for(std::chrono::seconds(5));
					break;
				case UserDeleted:
					// already handled in the if above
					break;
				}
			}
			catch(web_connection_error & error)
			{
				log_error("Connection error:\n" + std::string(error.what()));
				on_error.PossiblyThrowError();
				// sleep a bit and try again
				std::this_thread::sleep_for(std::chrono::seconds(10));
			}
		}
	}

private:
	std::vector<int> to_update;
	FollowerUpdateQueue & queue;
};

void store_pagerank(PostgresConnection & connection, const std::vector<std::pair<int, float>> & pagerank)
{
	std::stringstream command;
	command << "INSERT INTO pageranks (id, pagerank) VALUES ";
	const char * separator = "";
	for (auto && pair : pagerank)
	{
		command << separator << "(" << pair.first << ", " << pair.second <<")";
		separator = ", ";
	}
	command << ";";
	connection.ExecCommand(command.str().c_str());
}

bool did_run_pagerank_today(PostgresConnection & connection)
{
	std::string query = "SELECT EXISTS(SELECT * FROM pageranks WHERE pageranks.date = current_date);";
	PostgresResult result = connection.ExecQuery(query.c_str());
	if (result.GetNTuples() == 0)
		return false;
	else
		return result.GetValue(0, 0)[0] == 't';
}

void update_pagerank(PostgresConnection & connection)
{
	if (did_run_pagerank_today(connection))
		return;
	std::vector<std::pair<int, float>> pagerank = soundcloud_pagerank(connection);
	store_pagerank(connection, pagerank);
}

#ifndef DISABLE_GTEST
#	include <gtest/gtest.h>
#endif

int main(int argc, char * argv[])
{
#ifndef DISABLE_GTEST
	::testing::InitGoogleTest(&argc, argv);
	DE_VERIFY(!RUN_ALL_TESTS());
#endif
	int num_threads = 2;
	for (int i = 1; i < argc - 1; ++i)
	{
		if (strcmp(argv[i], "-numthreads") == 0)
		{
			num_threads = std::max(1, atoi(argv[i + 1]));
		}
	}
	PostgresConnection connection;
	update_pagerank(connection);
	FollowerUpdateQueue update_queue;
	for (std::vector<int> to_update = compute_users_to_update(connection); !to_update.empty(); to_update = compute_users_to_update(connection))
	{
		std::vector<std::thread> update_threads;
		size_t each_thread = to_update.size() / num_threads;
		for (int i = 0; i < num_threads; ++i)
		{
			update_threads.emplace_back(UpdaterThread(std::vector<int>(to_update.begin() + i * each_thread, to_update.begin() + (i + 1) * each_thread), update_queue));
		}
		if (to_update.size() % num_threads)
		{
			UpdaterThread(std::vector<int>(to_update.begin() + num_threads * each_thread, to_update.end()), update_queue)();
		}
		for (std::thread & thread : update_threads)
		{
			thread.join();
		}
	}
}

