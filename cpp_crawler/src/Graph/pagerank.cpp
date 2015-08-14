#include "pagerank.hpp"
#include <fstream>
#include <cstdlib>
#include <unistd.h>
#include "Util/scope_exit.hpp"
#include "graphchi_basic_includes.hpp"
#include "util/toplist.hpp"
#include "Debug/assert.hpp"

static std::vector<std::pair<int, float> > read_pagerank_from_disk(const std::string & filename)
{
	static_cast<void>(&graphchi::graphchi_init);
	graphchi::metrics m("read_pagerank");
	graphchi::stripedio iomgr(m);
	size_t num_vertices = graphchi::get_num_vertices(filename);
	graphchi::vertex_data_store<float> vertexdata(filename, num_vertices, &iomgr);

	std::vector<std::pair<int, float>> pageranks;
	pageranks.reserve(num_vertices / 10);
	size_t step = 1024 * 1024;
	float min_rank = 1e-3;
	for (size_t i = 0; i < num_vertices; i += step)
	{
		size_t end = std::min(num_vertices, i + step);
		vertexdata.load(i, end - 1);
		for(size_t j = i; j < end; ++j)
		{
			float rank = *vertexdata.vertex_data_ptr(j);
			if (rank > min_rank) pageranks.emplace_back(j, rank);
		}
	}
	pageranks.shrink_to_fit();
	return pageranks;
}

static void run_pagerank(const std::string & filename)
{
	if (chdir("./libs/graphchi_compiled") != 0)
	{
		DE_THROW(std::runtime_error("failed to change to the graphchi directory"));
	}
	auto change_back = AtScopeExit([&]
	{
		if (chdir("../../") != 0)
		{
			DE_THROW(std::runtime_error("failed to change back from the graphchi directory"));
		}
	});
	if (system(("./bin/example_apps/pagerank_functional niters 5 file " + filename + " filetype edgelist").c_str()) != 0)
	{
		DE_THROW(std::runtime_error("failed to run the graphchi pagerank executable"));
	}
}

bool write_soundcloud_edgelist(PostgresConnection & database, const std::string & filename)
{
	PostgresResult query = database.ExecQuery("SELECT * FROM following;");
	int edge_count = query.GetNTuples();
	if (edge_count == 0)
		return false;

	std::ofstream file(filename);
	for (int i = 0; i < edge_count; ++i)
	{
		char * follower = query.GetValue(i, 0);
		char * following = query.GetValue(i, 1);
		file << follower << "," << following << ",1\n";
	}
	return true;
}

std::vector<std::pair<int, float> > soundcloud_pagerank(PostgresConnection & database)
{
	std::string temp_filename = "/tmp/soundcloud_followings";
	if (!write_soundcloud_edgelist(database, temp_filename))
		return {};
	run_pagerank(temp_filename);
	return read_pagerank_from_disk(temp_filename);
}

