#include <iostream>
#include <string>
#include <cmath>
#include <cstdio>
#include <vector>
#include "driver_support.h"

#include "MatWaveWavedec.h"

using namespace ante_dwt;

void test1d(const string &mode, const string &wavename, int nx) {

    double *a = new double [nx];
    double *aprime = new double [nx];

//	init1d_sin(a, nx);
//	init1d_ramp(a, nx);
//  init1d_mirror(a, nx);
  init1d_constant(a, nx);

	MatWaveWavedec mw(wavename, mode);

	int nlevels = mw.wmaxlev(nx);
	
	size_t clen = mw.coefflength(nx, nlevels);
	double *C = new double[clen];
	size_t llen = nlevels+2;
	size_t *L = new size_t[llen];

	mw.wavedec(a, nx, nlevels, C, L);
	mw.waverec(C, L, nlevels, aprime);


//	report_error("test1d", a, aprime, nx);
report_error("test1d", a, aprime, mw.approxlength(nx, nlevels-0));

	delete [] C;
	delete [] L;
	delete [] a;
	delete [] aprime;
}


int main(int argc, char **argv) {
	doit(argc, argv);
}
