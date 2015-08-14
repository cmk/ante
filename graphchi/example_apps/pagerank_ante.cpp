/**
 * @file
 * @author  Aapo Kyrola <akyrola@cs.cmu.edu>
 * @version 1.0
 *
 * @section LICENSE
 *
 * Copyright [2012] [Aapo Kyrola, Guy Blelloch, Carlos Guestrin / Carnegie Mellon University]
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 
 *
 * @section DESCRIPTION
 *
 * "Functional" version of pagerank, which is quite a bit more efficient, because
 * it does not construct the vertex-objects but directly processes the edges.
 *
 * This program can be run either in the semi-synchronous mode (faster, but
 * less clearly defined semantics), or synchronously. Synchronous version needs
 * double the amount of I/O because it needs to store both previous and 
 * current values. Use command line parameter mode with semisync or sync.
 */

#define RANDOMRESETPROB 0.15
#define GRAPHCHI_DISABLE_COMPRESSION

#include <string>
#include <fstream>
#include <cmath>
#include <vector>

#include "graphchi_basic_includes.hpp"
#include "api/functional/functional_api.hpp"
#include "graphchi_basic_includes.hpp"
#include "util/toplist.hpp"

using namespace graphchi;

struct pagerank_kernel : public functional_kernel<float, float> {
    
    /* Initial value - on first iteration */
    float initial_value(graphchi_context &info, vertex_info& myvertex) override
    {
        return 1.0;
    }
    
    /* Called before first "gather" */
    float reset() override
    {
        return 0.0;
    }
    
    // Note: Unweighted version, edge value should also be passed
    // "Gather"
    float op_neighborval(graphchi_context &info, vertex_info& myvertex, vid_t nbid, float nbval) override
    {
        return nbval;
    }
    
    // "Sum"
    float plus(float curval, float toadd) override
    {
        return curval + toadd;
    }
    
    // "Apply"
    float compute_vertexvalue(graphchi_context &ginfo, vertex_info& myvertex, float nbvalsum) override
    {
        assert(ginfo.nvertices > 0);
        return RANDOMRESETPROB / ginfo.nvertices + (1 - RANDOMRESETPROB) * nbvalsum;
    }
    
    // "Scatter
    float value_to_neighbor(graphchi_context &info, vertex_info& myvertex, vid_t nbid, float myval) override
    {
        assert(myvertex.outdegree > 0);
        return myval / myvertex.outdegree; 
    }
    
}; 

template<typename It>
static void read_pagerank_from_disk(const std::string & filename, It out)
{
	graphchi::metrics m("read_pagerank");
	graphchi::stripedio iomgr(m);
	size_t num_vertices = graphchi::get_num_vertices(filename);
	graphchi::vertex_data_store<float> vertexdata(filename, num_vertices, &iomgr);

	size_t step = 1024 * 1024;
	for (size_t i = 0; i < num_vertices; i += step)
	{
		size_t end = std::min(num_vertices, i + step);
		vertexdata.load(i, end - 1);
		for(size_t j = i; j < end; ++j)
		{
			*out++ = std::make_pair(j, *vertexdata.vertex_data_ptr(j));
		}
	}
}

int main(int argc, const char ** argv)
{
    graphchi_init(argc, argv);
    metrics m("pagerank");
    
    std::string filename = get_option_string("file");
    int niters = get_option_int("niters", 20);
    bool onlyprint = get_option_int("onlyprint", 0);
    int ntoprint = get_option_int("toprint", 200000);
    
    if (onlyprint == 0)
    {
        run_functional_unweighted_synchronous<pagerank_kernel>(filename, niters, m);
    }

	std::vector<std::pair<int, float>> pageranks;
	read_pagerank_from_disk(filename, std::back_inserter(pageranks));
	std::sort(pageranks.begin(), pageranks.end(), [](std::pair<int, float> first, std::pair<int, float> second)
	{
		return first.second > second.second;
	});

	std::ofstream out_file(filename + "_pageranks");
	for (auto it = pageranks.begin(), end = std::min(pageranks.end(), it + ntoprint); it != end; ++it)
	{
		out_file << it->first << "," << it->second << '\n';
	}
}

