#include <iostream>
#include <string>
#include <cmath>
#include <cstdio>
#include <vector>

using namespace std;

#include "SignificanceMap.h"

const int NX = 64;
const int NY = 64;
const int NZ = 64;




main(int argc, char **argv) {

	int	rc;
	SignificanceMap smap(NX, NY, NZ, 1);

	for (size_t i=512; i>0; i--) {
		rc = smap.Set(i);
		assert(rc >= 0);
	}

	const unsigned char *emap;
	size_t emap_size;

	smap.GetMap(&emap, &emap_size);

	FILE	*fp;

	fp = fopen("sig.out", "w");
	assert(fp!=NULL);
	fwrite(emap, 1, emap_size, fp);
	fclose(fp);

	cout << "NX = " << NX << endl;
	cout << "NY = " << NY << endl;
	cout << "NZ = " << NZ << endl;
	cout << "emap_size " << emap_size << endl;
}
