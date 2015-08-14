#include <iostream>
#include <string>
#include <cmath>
#include <cstdio>
#include <vector>
#include "driver_support.h"

#include "MatWaveDwt.h"
#include "WaveFiltCoif.h"

const int DefaultNX = 65;
const int DefaultNY = 128;
const int DefaultNZ = 96;

double  marschner_lobb(
    double  x,
    double  y,
    double  z,
    double  alpha,
    double  fm
) {
    double  pr;
    double  r;
    double  v;

    r = sqrt (x*x + y*y);
    pr = cos(2*M_PI*fm*cos(M_PI*r/2.0));

    v = (1.0 - sin(M_PI*z/2.0) + (alpha * (1.0 + pr))) / (2*(1+alpha));
    return(v);
}

void init1d_sin(double *a, int nx) {
	for (int i=0; i<nx; i++) {
		a[i] = sin(2*M_PI*(i+1)/nx);
	}
}
void init1d_ramp(double *a, int nx) {
	for (int i=0; i<nx; i++) {
		a[i] = i+1;
	}
}

void init1d_mirror(double *a, int nx) {
	for (int i=0; i<nx/2; i++) {
		a[i] = a[nx-1-i] = i+1;
	}
	if (nx%2) a[nx/2] = nx/2+1;
}

void init1d_constant(double *a, int nx) {
	for (int i=0; i<nx; i++) {
		a[i] = 1;
	}
}


void report_error(
	const string msg, const double *a, const double *aprime, int nx
) {

	double l1 = 0.0;
	double lmax = 0.0;
	for (int i=0; i<nx; i++) {
		double delta = fabs (a[i] - aprime[i]);
		l1 += delta;
		lmax = delta > lmax ? delta : lmax;
	}

	cout << msg << " L1 : " << l1 << endl; 
	cout << msg << " Lmax : " << lmax << endl;

}

void test1d(const string &mode, const string &wavename, int nx);
void test2d(const string &mode, const string &wavename, int nx, int ny);
void test3d(const string &mode, const string &wavename, int nx, int ny, int nz);


void doit(int argc, char **argv) {

	int nx = DefaultNX;
	int ny = DefaultNY;
	int nz = DefaultNZ;

	argv++;
	if (*argv) {
		nx = atoi(*argv);
		argv++;
	}
	if (*argv) {
		ny = atoi(*argv);
		argv++;
	}
	if (*argv) {
		nz = atoi(*argv);
		argv++;
	}


	vector<string> wnames;
	vector<string> modes;


#define	SMALL
#ifdef	SMALL
//	wnames.push_back("bior3.1");
//	wnames.push_back("bior2.2");
//	wnames.push_back("bior2.6");
//	wnames.push_back("bior2.8");
//	wnames.push_back("bior1.3");
//	wnames.push_back("bior3.1");
	//wnames.push_back("haar");
	//wnames.push_back("bior1.5");
	//wnames.push_back("bior2.2");
	//wnames.push_back("bior2.4");
	//wnames.push_back("bior2.6");
	//wnames.push_back("bior2.8");
	//wnames.push_back("bior3.1");
	wnames.push_back("bior3.3");
	//wnames.push_back("bior3.5");
	//wnames.push_back("bior3.7");
	//wnames.push_back("bior3.9");
#else

	wnames.push_back("haar");
//	wnames.push_back("db1");
	wnames.push_back("db2");
	wnames.push_back("db3");
	wnames.push_back("db4");
	wnames.push_back("db5");
	wnames.push_back("db6");
	wnames.push_back("db7");
	wnames.push_back("db8");
	wnames.push_back("db9");
	wnames.push_back("db10");
	wnames.push_back("coif1");
	wnames.push_back("coif2");
	wnames.push_back("coif3");
	wnames.push_back("coif4");
	wnames.push_back("bior1.1");
	wnames.push_back("bior1.3");
	wnames.push_back("bior1.5");
	wnames.push_back("bior3.1");
	wnames.push_back("bior3.3");
	wnames.push_back("bior3.5");
	wnames.push_back("bior3.7");
	wnames.push_back("bior3.9");
	wnames.push_back("bior2.2");
	wnames.push_back("bior2.4");
	wnames.push_back("bior2.6");
	wnames.push_back("bior2.8");
#endif
#ifdef	SMALL
//	modes.push_back("zpd");
//	modes.push_back("symh");
	modes.push_back("symh");
//	modes.push_back("sp0");
//	modes.push_back("per");
#else
//	modes.push_back("per");
	modes.push_back("zpd");
	modes.push_back("sp0");
	modes.push_back("sp1");
	modes.push_back("symh");
	modes.push_back("symw");
#endif

	for (int j=0; j<wnames.size(); j++) {
		for (int i=0; i<modes.size(); i++) {
			cout << endl << wnames[j] << " " << modes[i] << endl;
			test1d(modes[i], wnames[j], nx);
			//test2d(modes[i], wnames[j], nx, ny);
			//test3d(modes[i], wnames[j], nx, ny, nz);
		}	
	}
}
