
#include <iostream>
#include <cassert>
#include <cmath>
#include "MatWaveWavedec.h"

using namespace ante_dwt;

MatWaveWavedec::MatWaveWavedec(
	const string &wname,
	const string &mode
) : MatWaveDwt(wname, mode) {

    if (MatWaveDwt::GetErrCode() != 0) return;

} 

MatWaveWavedec::~MatWaveWavedec() {
}

template <class T>
int wavedec_template(
	MatWaveWavedec *mww,
    const T *sigIn, size_t sigInLength, int n, 
    T *C, size_t CLength, size_t *L
) {
	if (n==0) {
		for (size_t i=0; i<sigInLength; i++) C[i] = sigIn[i];
		return(0);
	}

	int rc;
	const T *sigInPtr = sigIn;

	size_t len = sigInLength;
	size_t cALen = mww->MatWaveBase::approxlength(len);
	T *cptr;
	size_t tlen = 0;
	size_t L1d[3];
	for (int i=n; i>0; i--) {
		tlen += L[i];
		cptr = C + CLength - tlen - cALen;
		rc = mww->dwt(sigInPtr, len, cptr, L1d);
		if (rc<0) return(rc);

		len = cALen;
		cALen = mww->MatWaveBase::approxlength(cALen);
		sigInPtr = cptr;
		
	}

	return(0);
}

int MatWaveWavedec::_wavedec_setup(
    size_t sigInLength, int n,
    size_t *CLength, size_t *L
) {
	if (n<0 || n > wmaxlev(sigInLength)) {
		SetErrMsg("Invalid number of transforms : %d", n);
		return(-1);
	}

	computeL(sigInLength, n, L);
	*CLength = coefflength(L, n);

	return(0);
}

int MatWaveWavedec::wavedec(
    const double *sigIn, size_t sigInLength, int n, 
    double *C, size_t *L
) {
	size_t CLength;

	// 
	// Compute bookkeeping vector, L, and length of output vector, C
	//
	if (_wavedec_setup( sigInLength, n, &CLength, L)<0) return(-1);

	return wavedec_template(
		this, sigIn, sigInLength, n, C, CLength, L
	);
}

int MatWaveWavedec::wavedec(
    const float *sigIn, size_t sigInLength, int n, float *C, size_t *L
) {
	size_t CLength;

	if (_wavedec_setup( sigInLength, n, &CLength, L)<0) return(-1);

	return wavedec_template(
		this, sigIn, sigInLength, n, C, CLength, L
	);
}

template <class T>
int waverec_template(
	MatWaveWavedec *mww,
    const T *C, const size_t *L, int n,
	int l, bool normal,
    T *sigOut
) {
	if (n < 0) {
		MatWaveWavedec::SetErrMsg("Invalid number of transforms : %d", n);
		return(-1);
	}

	if (l<0 || l>n) l = n;
	int LLength = n + 2;

	if (l==0) {
		double scale = 1.0;
		if (normal) {
			for (int i = l; i<n; i++) scale /= sqrt(2.0);
		}
		for (size_t i=0; i<L[0]; i++) sigOut[i] = scale * C[i];
		return(0);
	}

	const T *cA = C;
	const T *cD = cA + L[0];
	size_t L1d[3] = {L[0], L[1], 0};
	for (int i=1; i<=l; i++) {
		L1d[2] = mww->approxlength(L[LLength-1], n-i);

		int rc = mww->idwt(cA, cD, L1d, sigOut);
		if (rc<0) return(rc);
		if (i==l) break;

		cA = sigOut;
		cD += L[i];
		L1d[0] = L1d[2];
		L1d[1] = L[i+1];
	}

	if (l != n && normal) {
		double scale = 1.0;
		for (int i = l; i<n; i++) scale /= sqrt(2.0);
		for (size_t i=0; i<L1d[2]; i++) sigOut[i] *= scale;
	}

	return(0);
}
    
int MatWaveWavedec::waverec(
    const double *C, const size_t *L, int n,
    double *sigOut
) {
	return waverec_template(
		this, C, L, n, n, false, sigOut
	);
}

int MatWaveWavedec::waverec(
    const float *C, const size_t *L, int n,
    float *sigOut
) {
	return waverec_template(
		this, C, L, n, n, false, sigOut
	);
}

int MatWaveWavedec::appcoef(
	const double *C, const size_t *L, int n, int l, bool normal, double *sigOut
) {
	return waverec_template(
		this, C, L, n, l, normal, sigOut
	);
}

int MatWaveWavedec::appcoef(
	const float *C, const size_t *L, int n, int l, bool normal, float *sigOut
) {
	return waverec_template(
		this, C, L, n, l, normal, sigOut
	);
}


void MatWaveWavedec::computeL(
	size_t sigInLen, int n, size_t *L
) const {
	L[n+1] = sigInLen;
	L[n] = sigInLen;
	for (int i=n; i>0; i--) {
		L[i-1] = MatWaveBase::approxlength(L[i]);
		L[i] = MatWaveBase::detaillength(L[i]);
	}
}

size_t MatWaveWavedec::coefflength(
	const size_t *L, int n
) const {
	size_t tlength = L[0];	// cA coefficients

	for (int i=1; i<=n; i++)  tlength += L[i];

	return(tlength);
}

size_t MatWaveWavedec::coefflength(
	size_t sigInLen, int n
) const {

	size_t *L = new size_t[n + 2];
	computeL(sigInLen, n, L);

	size_t tlength = coefflength(L, n);

	delete [] L;
	return(tlength);
}

size_t MatWaveWavedec::approxlength(
	size_t sigInLen, int n
) const {

	size_t cALen = sigInLen;

	for (int i=0; i<n; i++) {
		cALen = MatWaveBase::approxlength(cALen);
		if (cALen < 0) return (cALen);
	}
	return(cALen);
}

void MatWaveWavedec::approxlength(
	const size_t *L, int n, int l, size_t *len
) const {
	if (l > n) l = n;

	int LLength = n + 2;
	
	*len = approxlength(L[LLength-1], l);
}


