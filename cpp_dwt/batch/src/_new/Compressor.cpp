
#include <cstring>
#include <algorithm>
#include <iostream>
#include <cmath>
#include "Compressor.h"

using namespace ante_dwt;
using namespace std;

Compressor::Compressor(
	vector <size_t> dims, const string &wavename, 
	const string &mode
) : MatWaveWavedec(wavename, mode) {

    if (MatWaveWavedec::GetErrCode() != 0) return;

	if (dims.size() > 3) {
		SetErrMsg("Maximum of 3 dimensions currently supported");
		return;
	}

	_dims.clear();
	_indexvec.clear();
	_keepapp = true;

	for (int i=0; i<dims.size(); i++) {
		_dims.push_back(dims[i]);
	}

    _nx = _ny = _nz = 1;
	if (_dims.size() >= 1) _nx = _dims[0];
	if (_dims.size() >= 2) _ny = _dims[1];
	if (_dims.size() >= 3) _nz = _dims[2];

	//
	// Create an appropriate filter bank allocate memory for storing
	// wavelet coefficients
	//
	if (_dims.size() == 3) {

		_nlevels = min(
			min(wmaxlev(_nx), wmaxlev(_ny)), wmaxlev(_nz)
		);

		size_t clen = coefflength3(_nx, _ny, _nz, _nlevels);
		_C = new double[clen]; 
		if (! _C) {
			SetErrMsg("Memory allocation failed");
			return;
		}
		_CLen = clen;

		_L = new size_t[(21*_nlevels)+6];
		if (! _L) {
			SetErrMsg("Memory allocation failed");
			return;
		}
		_LLen = (21*_nlevels)+6;

		// Compute the bookkeeping vector, L. 
		//
		// N.B. L will be recomputed by wavedec(), but not 
		// waverec();
		//
		computeL3(_nx, _ny, _nz, _nlevels, _L);
	}
	else if (_dims.size() == 2) {
		_nlevels = min(wmaxlev(_nx), wmaxlev(_ny));

		size_t clen = coefflength2(_nx, _ny, _nlevels);
		_C = new double[clen]; 
		if (! _C) {
			SetErrMsg("Memory allocation failed");
			return;
		}
		_CLen = clen;

		_L = new size_t[(6*_nlevels)+4];
		if (! _L) {
			SetErrMsg("Memory allocation failed");
			return;
		}
		_LLen = (6*_nlevels)+4;
		computeL2(_nx, _ny, _nlevels, _L);
	}
	else {
		_nlevels = wmaxlev(_nx);

		size_t clen = coefflength(_nx, _nlevels);
		_C = new double[clen]; 
		if (! _C) {
			SetErrMsg("Memory allocation failed");
			return;
		}
		_CLen = clen;

		_L = new size_t[_nlevels+2];
		if (! _L) {
			SetErrMsg("Memory allocation failed");
			return;
		}
		_LLen = _nlevels+2;
		computeL(_nx, _nlevels, _L);
	}
	_indexvec.reserve(_CLen);

}

Compressor::~Compressor() {

	if (_C) delete [] _C;
	if (_L) delete [] _L;
}


//
// Comparision functions for the C++ Std Lib sort function
//
inline bool my_compare_f(const void * x1, const void * x2) {
	return(fabsf(* (float *) x1) > fabsf(* (float *) x2));
}

inline bool my_compare_d(const void * x1, const void * x2) {
	return(fabs(* (double *) x1) > fabs(* (double *) x2));
}


namespace {

template <class T>
int compress_template(
	Compressor *cmp,
	const T *src_arr, 
	T *dst_arr, 
	size_t dst_arr_len,
	T *C,
	size_t clen,
	size_t *L,
	SignificanceMap *sigmap,
	const vector <size_t> &dims,
	size_t nlevels,
	vector <void *> indexvec,
	bool my_compare(const void *, const void *)
) {

	
	if ((dims.size() < 1)  || (dims.size() > 3)) {
		Compressor::SetErrMsg("Invalid array shape");
		return(-1);
	}
		
	if (dst_arr_len > clen) {
		Compressor::SetErrMsg(
			"Invalid array destination array length : %lu",
			dst_arr_len
		);
		return(-1);
	}
		

	//
	// Forward wavelet transform the array and compute the number of 
	// approximation coefficients (numkeep).
	//
	size_t numkeep = 0;
	int rc = 0;
	if (dims.size() == 3) {
		if (cmp->KeepAppOnOff()) numkeep = L[0]*L[1]*L[2];
		rc = cmp->wavedec3(src_arr, dims[0], dims[1], dims[2], nlevels, C, L);
	}
	else if (dims.size() == 2) {
		if (cmp->KeepAppOnOff()) numkeep = L[0]*L[1];
		rc = cmp->wavedec2(src_arr, dims[0], dims[1], nlevels, C, L);
	}
	else if (dims.size() == 1) {
		if (cmp->KeepAppOnOff()) numkeep = L[0];
		rc = cmp->wavedec( src_arr, dims[0], nlevels, C, L);
	}
	if (rc < 0) return(-1);

	assert(dst_arr_len >= numkeep);

	rc = sigmap->Reshape(clen); if (rc<0) return(-1);
	
	sigmap->Clear();

	// Data has been transformed. Now we need to sort it and find
	// the threshold value. Note: we don't actually move the data. We
	// sort an index array that references the data array.

	for (size_t i = 0; i<dst_arr_len; i++) dst_arr[i] = 0.0;

	if (numkeep) {
		// If numkeep>0, copy approximation coeffs. verbatim
		//
		for (size_t idx = 0; idx<numkeep; idx++) {
			rc = sigmap->Set(idx);
			if (rc<0) return(-1);
			dst_arr[idx] = C[idx];
		}
		if (numkeep == dst_arr_len) return(0);
		dst_arr += numkeep;
		dst_arr_len -= numkeep;
	}

	indexvec.clear();
	for (size_t i=numkeep; i<clen; i++) indexvec.push_back(&C[i]);
    sort(indexvec.begin(), indexvec.end(), my_compare);

    // find the threshold value
	const T *cptr =  (T *) indexvec[dst_arr_len-1];
    double threshold = fabs((double) *cptr);

	// Copy coefficients that are larger than the threshold to
	// the destination array. Record their location in the significance
	// map.
	//

	for (size_t idx = numkeep, i = 0; idx<clen && i<dst_arr_len; idx++) {
		if (fabs(C[idx]) >= threshold) {
			rc = sigmap->Set(idx);
			if (rc<0) return(-1);
			dst_arr[i++] = C[idx];
		}
	}
			
	return(0);
}
};

int Compressor::Compress(
	const float *src_arr, 
	float *dst_arr, 
	size_t dst_arr_len,
	SignificanceMap *sigmap
) {

	return compress_template(
		this, src_arr, dst_arr, dst_arr_len, (float *) _C, _CLen,
		_L, sigmap, _dims, _nlevels, _indexvec, my_compare_f
	);
}

int Compressor::Compress(
	const double *src_arr, 
	double *dst_arr, 
	size_t dst_arr_len,
	SignificanceMap *sigmap
) {

	return compress_template(
		this, src_arr, dst_arr, dst_arr_len, (double *) _C, _CLen,
		_L, sigmap, _dims, _nlevels, _indexvec, my_compare_d
	);
}


namespace {
template <class T>
int decompress_template(
	Compressor *cmp,
	const T *src_arr, 
	T *dst_arr, 
	T *C,
	size_t clen,
	const size_t *L,
	int nlevels,
	SignificanceMap *sigmap,
	const vector <size_t> &dims
) {
	if ((dims.size() < 1)  || (dims.size() > 3)) {
		Compressor::SetErrMsg("Invalid array shape");
		return(-1);
	}

	for (size_t i = 0; i<clen; i++) {
		C[i] = 0.0;
	}
	//
	// Restore the non-zero wavelet coefficients
	//
	sigmap->GetNextEntryRestart();

	size_t nsig = sigmap->GetNumSignificant();
	for(size_t i=0; i<nsig; i++) {
		size_t idx;

		if (! sigmap->GetNextEntry(&idx)) {
			Compressor::SetErrMsg("Invalid significance map");
			return(-1);
		}

		C[idx] = src_arr[i];
	}
	

	int rc = 0;
	if (dims.size() == 3) {
		rc = cmp->waverec3(C, L, nlevels, dst_arr);
	}
	else if (dims.size() == 2) {
		rc = cmp->waverec2(C, L, nlevels, dst_arr);
	}
	else if (dims.size() == 1) {
		rc = cmp->waverec(C, L, nlevels, dst_arr);
	}

	return(rc);
}
};

int Compressor::Decompress(
	const float *src_arr, 
	float *dst_arr, 
	SignificanceMap *sigmap
) {


	return decompress_template(
		this, src_arr, dst_arr, (float *) _C, _CLen, _L, 
		_nlevels, sigmap, _dims
	);
} 

int Compressor::Decompress(
	const double *src_arr, 
	double *dst_arr, 
	SignificanceMap *sigmap
) {

	return decompress_template(
		this, src_arr, dst_arr, (double *) _C, _CLen, _L, 
		_nlevels, sigmap, _dims
	);
} 

namespace {
template <class T>
int decompose_template(
	Compressor *cmp,
	const T *src_arr, 
	T *dst_arr, 
	const vector <size_t> &dst_arr_lens,
	T *C,
	size_t clen,
	size_t *L,
	SignificanceMap **sigmaps,
	int n,
	const vector <size_t> &dims,
	size_t nlevels,
	vector <void *> indexvec,
	bool my_compare(const void *, const void *)
) {
	if (n != dst_arr_lens.size()) {
		Compressor::SetErrMsg("Invalid parameter");
		return(-1);
	}

	vector <size_t> my_dst_arr_lens = dst_arr_lens;

	size_t tlen = 0; // total # of coefficients to retain
	for (int i=0; i<my_dst_arr_lens.size(); i++) {
		tlen += my_dst_arr_lens[i];
	}
	if (tlen > clen) {
		Compressor::SetErrMsg("Invalid decomposition");
		return(-1);
	}
		
	if ((dims.size() < 1)  || (dims.size() > 3)) {
		Compressor::SetErrMsg("Invalid array shape");
		return(-1);
	}

	//
	// Forward wavelet transform the array and compute the number of 
	// approximation coefficients (numkeep).
	//
	size_t numkeep = 0;
	int rc = 0;
	if (dims.size() == 3) {
		if (cmp->KeepAppOnOff()) numkeep = L[0]*L[1]*L[2];
		rc = cmp->wavedec3(src_arr, dims[0], dims[1], dims[2], nlevels, C, L);
	}
	else if (dims.size() == 2) {
		if (cmp->KeepAppOnOff()) numkeep = L[0]*L[1];
		rc = cmp->wavedec2(src_arr, dims[0], dims[1], nlevels, C, L);
	}
	else if (dims.size() == 1) {
		if (cmp->KeepAppOnOff()) numkeep = L[0];
		rc = cmp->wavedec( src_arr, dims[0], nlevels, C, L);
	}
	if (rc<0) return(-1);

	if (my_dst_arr_lens[0] < numkeep) {
		Compressor::SetErrMsg("Invalid decomposition - not enougth coefficients");
		return(-1);
	} 

	for (int i=0; i<n; i++) {
		rc = sigmaps[i]->Reshape(clen);
		if (rc<0) return(-1);
		sigmaps[i]->Clear();
	}

	// Data has been transformed. Now we need to sort it and find
	// the threshold value. Note: we don't actually move the data. We
	// sort an index array that references the data array.

	for (size_t i = 0; i<tlen; i++) dst_arr[i] = 0.0;

	if (numkeep) {
		// If numkeep>0, copy approximation coeffs. verbatim
		//
		for (size_t idx = 0; idx<numkeep; idx++) {
			rc = sigmaps[0]->Set(idx);
			if (rc<0) return(-1);
			dst_arr[idx] = C[idx];
		}
		if (numkeep == tlen) return(0);
		dst_arr += numkeep;
		my_dst_arr_lens[0] -= numkeep;
	}

	indexvec.clear();
	for (size_t i=numkeep; i<clen; i++)  indexvec.push_back(&C[i]); 
    sort(indexvec.begin(), indexvec.end(), my_compare);

	
	vector <double> thresholds;	// thresholds ordered from large to smallest
	vector <size_t> dst_counts;
	size_t j = 0;
	for (int i=0; i<n; i++) {

		const T *cptr;
		j += my_dst_arr_lens[i];
		if (j==0) {;	// Hack to keep index in array bounds. 
			cptr =  (T *) indexvec[j];
		}
		else {
			cptr =  (T *) indexvec[j-1];
		}

		thresholds.push_back(fabs((double) *cptr));
		dst_counts.push_back(0);
	}

	for (size_t idx = numkeep; idx<clen; idx++) { 
		T *dst_arr_ptr = dst_arr;
		for (int i=0; i<n; i++) {
			if (dst_counts[i] < my_dst_arr_lens[i] && fabs(C[idx]) >= thresholds[i]) {
				rc = sigmaps[i]->Set(idx);
				if (rc<0) return(-1);
				dst_arr_ptr[dst_counts[i]] = C[idx];
				dst_counts[i] += 1;
				break;	// Only assign to one bin
			}
			dst_arr_ptr += my_dst_arr_lens[i];
		}
	}

	for (int i=0; i<n; i++) assert(dst_counts[i] == my_dst_arr_lens[i]);

#ifdef	DEAD

	// This way orders the coefficients from largest to smallest
	//
	size_t count = 0;
	size_t idx = 0;
	for (int j=0; j<n; j++) {
		SignificanceMap *sigmap = sigmaps[j];
		for (size_t i=0; i<my_dst_arr_lens[j]; i++) {
			idx = indexvec[count];
			sigmap->Set(idx);
			dst_arr[count] = C[idx];
			count++;
		}
	}
#endif
			
	return(0);
}


template <class T>
int reconstruct_template(
	Compressor *cmp,
	const T *src_arr, 
	T *dst_arr, 
	T *C,
	size_t clen,
	const size_t *L,
	int nlevels,
	int l,
	SignificanceMap **sigmaps,
	int n,
	const vector <size_t> &dims
) {
	if ((dims.size() < 1)  || (dims.size() > 3)) {
		Compressor::SetErrMsg("Invalid array shape");
		return(-1);
	}

	for (size_t count = 0; count<clen; count++) {
		C[count] = 0.0;
	}

	size_t count = 0;
	size_t idx;
	for (int j=0; j<n; j++) {
		SignificanceMap *sigmap = sigmaps[j];
		sigmap->GetNextEntryRestart();

		size_t nsig = sigmap->GetNumSignificant();
		for(size_t i=0; i<nsig; i++) {

			if (! sigmap->GetNextEntry(&idx)) {
				Compressor::SetErrMsg("Invalid significance map");
				return(-1);
			}

			C[idx] = src_arr[count];
			count++;
		}
	}


	int rc = 0;
	if (dims.size() == 3) {
		//cmp->waverec3(C, L, nlevels, dst_arr);
		rc = cmp->appcoef3(C, L, nlevels, l, true, dst_arr);
	}
	else if (dims.size() == 2) {
		//cmp->waverec2(C, L, nlevels, dst_arr);
		rc = cmp->appcoef2(C, L, nlevels, l, true, dst_arr);
	}
	else if (dims.size() == 1) {
		//cmp->waverec(C, L, nlevels, dst_arr);
		rc = cmp->appcoef(C, L, nlevels, l, true, dst_arr);
	}
	if (rc < 0) return(-1);

	return(0);
}

};

int Compressor::Decompose( 
	const float *src_arr, float *dst_arr, const vector <size_t> &dst_arr_lens,
	SignificanceMap **sigmaps, int n
) {
	return decompose_template(
		this, src_arr, dst_arr, dst_arr_lens, (float *) _C, _CLen,
		_L, sigmaps, n, _dims, _nlevels, _indexvec, my_compare_f
	);
}

int Compressor::Decompose( 
	const double *src_arr, double *dst_arr, const vector <size_t> &dst_arr_lens,
	SignificanceMap **sigmaps, int n
) {
	return decompose_template(
		this, src_arr, dst_arr, dst_arr_lens, (double *) _C, _CLen,
		_L, sigmaps, n, _dims, _nlevels, _indexvec, my_compare_d
	);
}

int Compressor::Reconstruct(
	const float *src_arr, float *dst_arr, 
	SignificanceMap **sigmaps, int n, int l
) {
	if (l==-1) l = GetNumLevels();
	return reconstruct_template(
		this, src_arr, dst_arr, (float *) _C, _CLen, _L, _nlevels, l, sigmaps, 
		n, _dims
	);
}

int Compressor::Reconstruct(
	const double *src_arr, double *dst_arr, 
	SignificanceMap **sigmaps, int n, int l
) {
	if (l==-1) l = GetNumLevels();
	return reconstruct_template(
		this, src_arr, dst_arr, (double *) _C, _CLen, _L, _nlevels, l, sigmaps, 
		n, _dims
	);
}
 



#ifdef	DEAD
bool Compressor::IsCompressible(
	vector <size_t> dims, const string &wavename, const string &mode
) {

	if (dims.size() < 1 || dims.size() > 3) {
		return(false);
	}

	size_t nx;
	size_t ny;
	size_t nz;

    nx = ny = nz = 1;
	if (dims.size() >= 1) nx = dims[0];
	if (dims.size() >= 2) ny = dims[1];
	if (dims.size() >= 3) nz = dims[2];


	int nlevels;
	if (dims.size() == 3) {

		nlevels = min(min(wmaxlev(nx), wmaxlev(ny)), wmaxlev(nz));
	}
	else if (dims.size() == 2) {
		nlevels = min(wmaxlev(nx), wmaxlev(ny));
	}
	else {
		nlevels = wmaxlev(nx);
	}

	return (nlevels > 0);
}
#endif

void Compressor::GetDimension(vector <size_t> &dims, int l) const {

	if (l<0 || l>_nlevels) l = _nlevels;

	dims.clear();
	for (int i=0; i<_dims.size(); i++) {
		size_t len = approxlength(_dims[i], _nlevels - l);
		dims.push_back(len);
	}
}

size_t Compressor::GetMinCompression() const {
	if (! _keepapp) return(1);

	if (_dims.size() == 3) {
		return(_L[0]*_L[1]*_L[2]);
	}
	else if (_dims.size() == 2) {
		return(_L[0]*_L[1]);
	}
	else if (_dims.size() == 1) {
		return(_L[0]);
	}
	return(0);
}
