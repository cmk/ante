#include <iostream>
#include <string>
#include <cmath>
#include <cstdio>
#include <vector>
#include "driver_support.h"

#include "MatWaveDwt.h"
#include "WaveFiltCoif.h"
#include "MyBase.h"

using namespace ante_dwt;

void test1d(const string &mode, const string &wavename, int nx) {

	double *a = new double [nx];
	double *aprime = new double [nx];

//	init1d_sin(a, nx);
	init1d_ramp(a, nx);
//	init1d_mirror(a, nx);
//	init1d_constant(a, nx);

	MatWaveDwt mw(wavename, mode);
	std::cout << mw.wavelet()->GetHighDecomFilCoef() << std::endl;
	std::cout << mw.wavelet()->GetLowDecomFilCoef() << std::endl;
	for (int i=0;i<mw.wavelet()->GetLength();i++) {

		std::cout << "lp " << mw.wavelet()->GetLowDecomFilCoef()[i] << "hp: " << mw.wavelet()->GetHighDecomFilCoef()[i] << std::endl;
		


	}


	size_t len = mw.coefflength(nx);
	double *C = new double[len];
	size_t L[3];

	if (mw.dwt(a, nx, C, L) < 0) return;
	if (mw.idwt(C, L, aprime) < 0) return;

	report_error("test1d", a, aprime, nx);
	L[0] = L[1] = L[2] = 8;
	for (int j=0; j<8; j++) {
	for (int k=0;k<L[0]*L[1]*L[2] ; k++) {
		int i = j*L[0]*L[1]*L[2] + k;
	//	std::cout << i/ L[1] << " " << i% L[1] << " C " << C[i] << std::endl;
	}
	std::cout  << std::endl;
	}



	delete [] C;
	delete [] a;
	delete [] aprime;

}



int main(int argc, char **argv) {

	VetsUtil::MyBase::SetErrMsgFilePtr(stderr);
//	doit(argc, argv);
//	testSlice_GPU("symh","bior3.1", nx, ny, nz);
	int nx = 16*16*16;
	test1d("symh","bior3.1", nx);
}
