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

using namespace ante_dwt;


double DecTime = 0.0;
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
	float *brick, int bx, int by, int bz, bool swapbytes
) {

	TIMER_START(t0);

	for (int z = 0; z<bz; z++) {
	for (int y = 0; y<by; y++) {
	for (int x = 0; x<bx; x++) {
		brick[z*bx*by + y*bx + x] = data[nx*ny*(z0+z) + nx*(y0+y) + (x0+x)];
	}
	}
	}

	if (swapbytes) {
		size_t i = 0;
		for (int z = 0; z<bz; z++) {
		for (int y = 0; y<by; y++) {
		for (int x = 0; x<bx; x++) {
			unsigned char *uptr = (unsigned char *) (&brick[i]);
			unsigned char c = uptr[0];
			uptr[0] = uptr[3];
			uptr[3] = c;

			c = uptr[1];
			uptr[1] = uptr[2];
			uptr[2] = c;

			i++;
		}
		}
		}
	}

	TIMER_STOP(t0, BrickTime);
}

main(int argc, char **argv) {

	Compressor::SetErrMsgFilePtr(stderr);

	int bx = 64;
	int by = 64;
	int bz = 64;
	int nx = 512;
	int ny = 512;
	int nz = 512;
	string wname("bior3.3");
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
		else if (arg.compare("-wname") == 0) {
			argv++; assert(*argv);
			wname.assign(*argv);
		}
		else if (arg.compare("-mode") == 0) {
			argv++; assert(*argv);
			mode.assign(*argv);
		}
		else if (arg.compare("-swapbytes") == 0) {
			swapbytes = true;
		}
		else {
			myargv.push_back(*argv);
		}
		argv++;
	}

	assert(myargv.size() >= 3);
	string srcfile(myargv.front()); myargv.erase(myargv.begin());
	string dstbase(myargv.front()); myargv.erase(myargv.begin());

	cout << "nx = " << nx << endl;
	cout << "ny = " << ny << endl;
	cout << "nz = " << nz << endl;
	cout << "bx = " << bx << endl;
	cout << "by = " << by << endl;
	cout << "bz = " << bz << endl;
	cout << "wavelet = " << wname << endl;
	cout << "mode = " << mode << endl;
	cout << "srcfile = " << srcfile << endl;
	cout << "dstbase = " << dstbase << endl;
	

	int n = myargv.size();
	SignificanceMap **sigmaps = new SignificanceMap* [n+1];
	vector <size_t> dst_arr_lens;

	float *data;
	float *brick, *cvector; 

	assert(nx%bx == 0);
	assert(ny%by == 0);
	assert(nz%bz == 0);

	data = new float[nx*ny*nz];
	brick = new float [bx*by*bz];

	vector <size_t> dims;
	dims.push_back(bx);
	if (by>1) dims.push_back(by);
	if (by>1 && bz>1) dims.push_back(bz);


	size_t cvectorlen = 0;
	for (int i=0; i<n; i++) {
		sigmaps[i] = new SignificanceMap();
		dst_arr_lens.push_back(atoi(myargv[i].c_str()));
		cvectorlen += dst_arr_lens[i];
	}
	assert(cvectorlen <= bx*by*bz);

	Compressor *cmp = new Compressor(dims, wname, mode);
	if (Compressor::GetErrCode() != 0) exit(1);
	//cmp->KeepAppOnOff() = false;

	// Add final sigmap
	//
	vector <size_t> sigmapshape;
	cmp->GetSigMapShape(sigmapshape);
	assert(sigmapshape.size() == 1);
	dst_arr_lens.push_back(sigmapshape[0] - cvectorlen);
	cvectorlen = sigmapshape[0];
	sigmaps[n] = new SignificanceMap();

	cvector = new float [cvectorlen];

	
		
	FILE *fp;
	fp = fopen(srcfile.c_str(), "r");
	assert(fp != NULL);

	int rc = fread(data, sizeof(data[0]), nx*ny*nz, fp);
	fclose(fp);
	assert (rc == nx*ny*nz);

	ostringstream oss;
	oss << dstbase << "_w=" << wname << "_m=" << mode;
	oss << "_bs=" << bx;
	string logfile(oss.str() + ".log");
	cout << "logfile = " << logfile << endl;

	FILE *cdatafps[16];
	FILE *sigfps[16];
	for (int i=0; i<n+1; i++) {
		ostringstream sigmapfile;
		ostringstream cdatafile;

		sigmapfile << oss.str() << ".sig" << "." << i;
		cdatafile << oss.str() << "." << i;

		cout << "cdatafile = " << cdatafile.str() << endl;
		cout << "sigmapfile = " << sigmapfile.str() << endl;

		cdatafps[i] = fopen(cdatafile.str().c_str(), "w");
		assert(cdatafps[i] != NULL);

		sigfps[i] = fopen(sigmapfile.str().c_str(), "w");
		assert(sigfps[i] != NULL);
	}
		
	TIMER_START(t0);
	double time_compress = 0.0;
	double time_brick = 0.0;

	DecTime = 0.0;
	BrickTime = 0.0;


	for (int z=0; z<nz; z+= bz) {
		for (int y=0; y<ny; y+= by) {
			for (int x=0; x<nx; x+= bx) {
				//cout << "Compress " << x << " " << y << " " << z << endl;
				fetch_brick(data, nx, ny, nz, x, y, z, brick, bx, by, bz, swapbytes);

				cerr << "Processing " << z << " " << y << " " << x << endl;

				TIMER_START(t1);
				cmp->Decompose(brick, cvector, dst_arr_lens, sigmaps, n+1);
				if (Compressor::GetErrCode() != 0) exit(1);
				TIMER_STOP(t1, DecTime);


				float *cvectorptr = cvector;
				for (int i=0; i<n+1; i++) {
				
					//
					// write the coefficients
					//
					int rc = fwrite(
						cvectorptr, sizeof(cvectorptr[0]), 
						dst_arr_lens[i], cdatafps[i]
					);
					assert (rc == dst_arr_lens[i]);
					cvectorptr += dst_arr_lens[i];

					//
					// write the maps
					//
					const unsigned char *map;
					size_t maplen;
					sigmaps[i]->GetMap(&map, &maplen);

					rc = fwrite(
						(const void *) &maplen, sizeof(maplen), 1, sigfps[i]
					);
					assert (rc == 1);

					// write the map
					rc = fwrite(map, sizeof(map[0]), maplen, sigfps[i]);
					assert (rc == maplen);
				}
			}
		}
	}

	for (int i=0; i<n+1; i++) {
		fclose(cdatafps[i]);
		fclose(sigfps[i]);
	}
	delete cmp;


	TIMER_STOP(t0, time_compress);

	ofstream fout(logfile.c_str());
	fout << "nx = " << nx << endl;
	fout << "ny = " << ny << endl;
	fout << "nz = " << nz << endl;
	fout << "bx = " << bx << endl;
	fout << "by = " << by << endl;
	fout << "bz = " << bz << endl;
	fout << "wavelet = " << wname << endl;
	fout << "mode = " << mode << endl;
	fout << "num coefficients per brick: ";
	for (int i=0;i<n+1; i++) {
		fout << dst_arr_lens[i] << " ";
	}
	fout << endl;
	fout << "total compress time = " << time_compress << endl;
	fout << "	decomposition time = " << DecTime << endl;
	fout << "	brick time = " << BrickTime << endl;


	for (int i=0; i<n+1; i++) {
		delete sigmaps[i];
	}
	delete [] sigmaps;

	delete [] data;
	delete [] cvector;
	delete [] brick;
	
	fout.close();
	

	
}
