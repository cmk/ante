#include <iostream>
#include <string>
#include <cmath>
#include <cstdio>
#include <vector>

using namespace std;

#include "SignificanceMap.h"

main(int argc, char **argv) {

	int	rc;
	assert(argc == 5);
	
	size_t nx = atoi(argv[1]);
	size_t ny = atoi(argv[2]);
	size_t nz = atoi(argv[3]);
	size_t emap_size = atoi(argv[4]);

	
	unsigned char *emap = new unsigned char[emap_size];
	FILE * fp = fopen("sig.out", "r");
	assert(fp!=NULL);
	rc = fread(emap, 1, emap_size, fp);
	assert(rc == emap_size);
	fclose(fp);

	SignificanceMap smap(emap, nx, ny, nz, 1);

	smap.GetNextEntryRestart();
	size_t idx;
	while (smap.GetNextEntry(&idx)) {
		cout << idx << endl;
	}

}
