#include <iostream>
#include <fstream>
#include <sstream>
#include <string>
#include <cmath>
#include <cstdio>
#include <cassert>
#include <time.h>
#include <vector>

#include "Compressor.h"


double DecTime = 0.0;
double RecTime = 0.0;
double BrickTime = 0.0;

const int MAX_LEV = 16;
int NumLevels = 0;

double  inline gettime() {
    struct timespec ts;
    double  t;

    ts.tv_sec = ts.tv_nsec = 0;

    clock_gettime(CLOCK_REALTIME, &ts);


    t = (double) ts.tv_sec + (double) ts.tv_nsec*1.0e-9;

    return(t);
}

#define TIMER_START(T0)     double (T0) = gettime();
#define TIMER_STOP(T0, T1)  (T1) += gettime() - (T0);


void fetch_brick(
	const float *data, int nx, int ny, int nz, int x0, int y0, int z0, 
	float *brick, int bx, int by, int bz
) {

	TIMER_START(t0);

	for (int z = 0; z<bz; z++) {
	for (int y = 0; y<by; y++) {
	for (int x = 0; x<bx; x++) {
		brick[z*bx*by + y*bx + x] = data[nx*ny*(z0+z) + nx*(y0+y) + (x0+x)];
	}
	}
	}

	TIMER_STOP(t0, BrickTime);
}
void put_brick(
    const float *brick, int bx, int by, int bz,
    float *data, int nx, int ny, int nz, int x0, int y0, int z0
) {

    TIMER_START(t0);
    for (int z = 0; z<bz; z++) {
    for (int y = 0; y<by; y++) {
    for (int x = 0; x<bx; x++) {
        data[nx*ny*(z0+z) + nx*(y0+y) + (x0+x)] = brick[z*bx*by + y*bx + x];
    }
    }
    }

    TIMER_STOP(t0, BrickTime);
}


void compute_error(
	const float *data, const float *cdata, int nx, int ny, int nz,
	double &l1, double &l2, double &lmax, double &rms
) {
	l1 = 0.0;
	l2 = 0.0;
	lmax = 0.0;
	rms = 0.0;
	for (int k=0; k<nz; k++) {
		for (int j=0; j<ny; j++) {
			for (int i=0; i<nx; i++) {
				double delta = fabs (data[k*nx*ny + j*nx + i] - cdata[k*nx*ny + j*nx + i]);
				l1 += delta;
				l2 += delta * delta;
				lmax = delta > lmax ? delta : lmax;
			}
		}
	}
	l2 = sqrt(l2);
	rms = l2 / (nx*ny*nz);
}

void swap_bytes(float *data, size_t n) {

	for (size_t i=0; i<n; i++) {
		unsigned char *uptr = (unsigned char *) (&data[i]);
		unsigned char c = uptr[0];
		uptr[0] = uptr[3];
		uptr[3] = c;

		c = uptr[1];
		uptr[1] = uptr[2];
		uptr[2] = c;

	}
}

main(int argc, char **argv) {

	int bx = 64;
	int by = 64;
	int bz = 64;
	int nx = 512;
	int ny = 512;
	int nz = 512;
	int cratio = 10;
	string wavelet("bior3.3");
	string mode("symh");
	bool swapbytes = false;

	argv++;
	vector <string> myargv;
	while (*argv) {
		int rc;
		string arg(*argv);

		if (arg.compare("-dim") == 0) {
			argv++; assert(*argv);
			rc = sscanf(*argv, "%dx%dx%d", &nx, &ny, &nz);
			assert(rc==3);
		}
		else if (arg.compare("-bs") == 0) {
			argv++; assert(*argv);
			rc = sscanf(*argv, "%dx%dx%d", &bx, &by, &bz);
			assert(rc==3);
		}
		else if (arg.compare("-wavelet") == 0) {
			argv++; assert(*argv);
			wavelet.assign(*argv);
		}
		else if (arg.compare("-mode") == 0) {
			argv++; assert(*argv);
			mode.assign(*argv);
		}
		else if (arg.compare("-cratio") == 0) {
			argv++; assert(*argv);
			cratio = atoi(*argv);
		}
		else if (arg.compare("-swapbytes") == 0) {
			swapbytes = true;
		}
		else {
			myargv.push_back(*argv);
		}
		argv++;
	}

	assert(myargv.size() == 2);
	string srcfile(myargv.front()); myargv.erase(myargv.begin());
	string dstbase(myargv.front()); myargv.erase(myargv.begin());

	
	float *data, *cdata;
	float *brick, *cvector; 
	int cvectorlen = (bx*by*bz) / cratio;
	assert(cvectorlen <= bx*by*bz);

	assert(nx%bx == 0);
	assert(ny%by == 0);
	assert(nz%bz == 0);

	data = new float[nx*ny*nz];
	cdata = new float[nx*ny*nz];
	brick = new float [bx*by*bz];

	vector <size_t> dims;
	dims.push_back(bx);
	if (by>1) dims.push_back(by);
	if (by>1 && bz>1) dims.push_back(bz);


	SignificanceMap *sigmap = new SignificanceMap();


	Compressor *cmp = new Compressor(dims, wavelet, mode);

	cvector = new float [cvectorlen];

	FILE *fp;
	fp = fopen(srcfile.c_str(), "r");
	assert(fp != NULL);

	int rc = fread(data, sizeof(data[0]), nx*ny*nz, fp);
	fclose(fp);
	if (swapbytes) swap_bytes(data, nx*ny*nz);
	assert (rc == nx*ny*nz);

	ostringstream oss;
	oss << dstbase << "_w=" << wavelet << "_m=" << mode;
	oss << "_bs=" << bx << "x" << by << "x" << bz;
	oss << "_cr=" << cratio;
	oss << ".raw";
	string dstfile(oss.str());

	fp = fopen(dstfile.c_str(), "w");
	assert(fp != NULL);
		
	TIMER_START(t0);
	double time_compress = 0.0;
	double time_brick = 0.0;

	DecTime = 0.0;
	RecTime = 0.0;
	BrickTime = 0.0;

	cout << "nx = " << nx << endl;
	cout << "ny = " << ny << endl;
	cout << "nz = " << nz << endl;
	cout << "bx = " << bx << endl;
	cout << "by = " << by << endl;
	cout << "bz = " << bz << endl;
	cout << "wavelet = " << wavelet << endl;
	cout << "mode = " << mode << endl;
	cout << "srcfile = " << srcfile << endl;
	cout << "dstfile = " << dstfile << endl;


	for (int z=0; z<nz; z+= bz) {
		for (int y=0; y<ny; y+= by) {
			for (int x=0; x<nx; x+= bx) {

				fetch_brick(data, nx, ny, nz, x, y, z, brick, bx, by, bz);

				TIMER_START(t1);
				cmp->Compress(brick, cvector, cvectorlen, sigmap);
				TIMER_STOP(t1, DecTime);

				TIMER_START(t2);
				cmp->Decompress(cvector, brick, sigmap);
				TIMER_STOP(t2, RecTime);

				put_brick(brick, bx, by, bz, cdata, nx, ny, nz, x, y, z);

			}
		}
	}

	double l1, l2, lmax, rms;
	compute_error(data, cdata, nx, ny, nz, l1, l2, lmax, rms);

	cout << "L1 = " << l1 << endl;
	cout << "L2 = " << l2 << endl;
	cout << "LMax = " << lmax << endl;
	cout << "RMS = " << rms << endl;

	if (swapbytes) swap_bytes(cdata, nx*ny*nz);
	rc = fwrite(cdata, sizeof(cdata[0]), nx*ny*nz, fp);
	fclose(fp);



	delete cmp;


	TIMER_STOP(t0, time_compress);

	cout << "total compress time = " << time_compress << endl;
	cout << "	compress time = " << DecTime << endl;
	cout << "	reconstruct time = " << RecTime << endl;
	cout << "	brick time = " << BrickTime << endl;


	delete [] data;
	delete [] cvector;
	delete [] brick;
	
}
