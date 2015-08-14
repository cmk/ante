
#ifndef	_driver_support_h_
#define _driver_support_h_

#include <string>
using namespace std;

void init1d_sin(double *a, int nx);
void init1d_ramp(double *a, int nx);
void init1d_mirror(double *a, int nx);
void init1d_constant(double *a, int nx); 



void report_error(
	const string msg, const double *a, const double *aprime, int nx
);

void doit(int argc, char **argv);


#endif
