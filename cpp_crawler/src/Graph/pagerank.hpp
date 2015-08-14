#pragma once

#include "DB/postgres_wrapper.hpp"
#include <vector>
#include <utility>

std::vector<std::pair<int, float> > soundcloud_pagerank(PostgresConnection & database);
