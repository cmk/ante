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

void put_brick(
	const float *brick, int bx, int by, int bz,
	float *data, int nx, int ny, int nz, int x0, int y0, int z0, bool swapbytes
) {

	TIMER_START(t0);
	for (int z = 0; z<bz; z++) {
	for (int y = 0; y<by; y++) {
	for (int x = 0; x<bx; x++) {
		data[nx*ny*(z0+z) + nx*(y0+y) + (x0+x)] = brick[z*bx*by + y*bx + x];
	}
	}
	}

    if (swapbytes) {
        size_t i = 0;
        for (int z = 0; z<bz; z++) {
        for (int y = 0; y<by; y++) {
        for (int x = 0; x<bx; x++) {
            unsigned char *uptr = (unsigned char *) (&data[i]);
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

	int bx = 64;
	int by = 64;
	int bz = 64;
	int nx = 512;
	int ny = 512;
	int nz = 512;
	int lod = -1;
	int level = -1;
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
		else if (arg.compare("-lod") == 0) {
			argv++; assert(*argv);
			lod = atoi(arg.c_str());
		}
		else if (arg.compare("-level") == 0) {
			argv++; assert(*argv);
			level = atoi(arg.c_str());
		}
		else if (arg.compare("-swapbytes") == 0) {
			swapbytes = true;
		}
		else {
			myargv.push_back(*argv);
		}
		argv++;
	}

	assert(myargv.size() >= 2);
	string srcbase(myargv.front()); myargv.erase(myargv.begin());

	int n = myargv.size();
	if (lod == -1) lod = n;

	cout << "nx = " << nx << endl;
	cout << "ny = " << ny << endl;
	cout << "nz = " << nz << endl;
	cout << "bx = " << bx << endl;
	cout << "by = " << by << endl;
	cout << "bz = " << bz << endl;
	cout << "wavelet = " << wname << endl;
	cout << "mode = " << mode << endl;
	cout << "lod = " << lod << endl;
	cout << "level = " << level << endl;
	cout << "srcbase = " << srcbase << endl;


	assert(lod<=n+1);

	SignificanceMap **sigmaps = new SignificanceMap* [n+1];
	size_t *dst_arr_lens = new size_t[n+1];

	float *cdata;
	float *brick, *cvector; 

	cdata = new float[nx*ny*nz];
	brick = new float [bx*by*bz];

	vector <size_t> dims;
	dims.push_back(bx);
	if (by>1) dims.push_back(by);
	if (by>1 && bz>1) dims.push_back(bz);


	unsigned char *map = NULL;
	size_t mapbufsize = 0;

	Compressor::SetErrMsgFilePtr(stderr);
	Compressor *cmp = new Compressor(dims, wname, mode);
	if (level != -1) {
		vector <size_t> bdims;
		cmp->GetDimension(bdims, level);
		int nlevels = cmp->GetNumLevels();
		if (level > nlevels) level = nlevels;
		bx = bdims[0];
		by = bdims[1];
		bz = bdims[2];
		nx >>= (nlevels - level);
		ny >>= (nlevels - level);
		nz >>= (nlevels - level);
	}
	assert(nx%bx == 0);
	assert(ny%by == 0);
	assert(nz%bz == 0);

	vector <size_t> sigmapshape;
	cmp->GetSigMapShape(sigmapshape);


	size_t cvectorlen = 0;
	for (int i=0; i<n; i++) {

		dst_arr_lens[i] = atoi(myargv[i].c_str());

		cvectorlen += dst_arr_lens[i];
	}
	assert(cvectorlen <= nx*ny*nz);

    dst_arr_lens[n] = sigmapshape[0] - cvectorlen;
    cvectorlen = sigmapshape[0];
    sigmaps[n] = new SignificanceMap();


	cvector = new float [cvectorlen];


	for (int i=0; i<n+1; i++) {
		sigmaps[i] = new SignificanceMap(sigmapshape);
	}
		

	ostringstream oss;
	oss << srcbase << "_w=" << wname << "_m=" << mode;
	oss << "_bs=" << bx;

	string sigmapfile(oss.str() + ".sig");
	string cdatafile(oss.str());

	FILE *cdatafps[16];
	FILE *sigfps[16];
	for (int i=0; i<lod; i++) {
		ostringstream sigmapfile;
		ostringstream cdatafile;

		sigmapfile << oss.str() << ".sig" << "." << i;
		cdatafile << oss.str() << "." << i;

		cout << "cdatafile = " << cdatafile.str() << endl;
		cout << "sigmapfile = " << sigmapfile.str() << endl;

		cdatafps[i] = fopen(cdatafile.str().c_str(), "r");
		assert(cdatafps[i] != NULL);

		sigfps[i] = fopen(sigmapfile.str().c_str(), "r");
		assert(sigfps[i] != NULL);
	}

	string reconfile(oss.str() + ".recon");

	TIMER_START(t0);
	double time_compress = 0.0;
	double time_brick = 0.0;

	RecTime = 0.0;
	BrickTime = 0.0;



	for (int z=0; z<nz; z+= bz) {
		for (int y=0; y<ny; y+= by) {
			for (int x=0; x<nx; x+= bx) {
				//cout << "Reconstruct " << x << " " << y << " " << z << endl;

				float *cvectorptr = cvector;
				for (int i=0; i<lod; i++) {

					//
					// write the coefficients
					//
					int rc = fread(
						cvectorptr, sizeof(cvectorptr[0]),
						dst_arr_lens[i], cdatafps[i]
					);
					assert (rc == dst_arr_lens[i]);
					cvectorptr += dst_arr_lens[i];


					//
					// read the maps
					//
					if (i<n) {
						size_t maplen;
						rc = fread(&maplen, sizeof(maplen), 1, sigfps[i]);
						assert (rc == 1);

						if (maplen > mapbufsize) {
							if (map) delete [] map;
							map = new unsigned char [maplen];
							mapbufsize = maplen;
						}

						// read the map
						rc = fread(map, sizeof(map[0]), maplen, sigfps[i]);
						assert (rc == maplen);

						sigmaps[i]->SetMap(map);
					}
					else if (i==n) {
						sigmaps[n]->Clear();
						for (int j=0; j<n; j++) {
							sigmaps[n]->Append(*sigmaps[j]);
						}
						sigmaps[n]->Sort();
						sigmaps[n]->Invert();
					}
				}

				TIMER_START(t1);
				cmp->Reconstruct(cvector, brick, sigmaps, lod, level);
				TIMER_STOP(t1, RecTime);

				put_brick(brick,bx, by, bz, cdata, nx, ny, nz, x, y, z, swapbytes);

			}
		}
	}

	FILE *fp;
	fp = fopen(reconfile.c_str(), "w");
	assert(fp != NULL);
	int rc = fwrite(cdata, sizeof(cdata[0]), nx*ny*nz, fp);
	assert (rc == nx*ny*nz);
	fclose(fp);


	for (int i=0; i<lod; i++) {
		fclose(cdatafps[i]);
		fclose(sigfps[i]);
	}
	delete cmp;


	TIMER_STOP(t0, time_compress);

	cout << "wavelet = " << wname << endl;
	cout << "extension = " << mode << endl;
	cout << "nx = " << nx << endl;
	cout << "ny = " << ny << endl;
	cout << "nz = " << nz << endl;
	cout << "total decompress time = " << time_compress << endl;
	cout << "	reconstruction time = " << RecTime << endl;
	cout << "	brick time = " << BrickTime << endl;


	for (int i=0; i<n+1; i++) {
		delete sigmaps[i];
	}
	delete [] sigmaps;
	delete [] dst_arr_lens;

	delete [] cdata;
	delete [] cvector;
	delete [] brick;
	
	
}
