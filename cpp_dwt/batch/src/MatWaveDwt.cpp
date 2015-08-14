
#include <iostream>
#include <cassert>
#include <cmath>
#include "MatWaveDwt.h"

using namespace ante_dwt;

namespace {

#ifdef DEBUG
template <class T>
void printmatrix1d(const char *msg, const T *mat, size_t nx) {


	fprintf(stdout, "%s\n", msg);
		for (size_t i=0; i<nx; i++) {
			fprintf(stdout, "%-10f", (float) mat[i]);
		}
		fprintf(stdout, "\n");
}



#else
#define printmatrix1d(a,b,c)
#endif

double *buf_alloc(
	double *buf, size_t *buf_size, size_t len
) {
	if (*buf_size < len) {
		if (buf) delete [] buf;
		buf = new double[len];
		*buf_size = len;
	}
	if (buf == NULL) {
		MatWaveDwt::SetErrMsg(
			"Memory allocation of %lu bytes failed", 
			(size_t) len * sizeof(double)
		);
		return(NULL);
	}
	return(buf);
}


/*-------------------------------------------
 * Signal Extending
 *-----------------------------------------*/


template <class T>
int wextend_1D_center (
	const T *sigIn, size_t sigInLen,
	double *sigOut, size_t addLen,
	MatWaveBase::dwtmode_t leftExtMethod,
	MatWaveBase::dwtmode_t rightExtMethod,
	bool invalid_float_abort

) {
  int count = 0;

  for (count = 0; count < addLen; count++)
    {
      sigOut[count] = 0;
      sigOut[count + sigInLen + addLen] = 0;
    }

  for (count = 0; count < sigInLen; count++)
    {
      if (! isfinite((double) sigIn[count])) {
        if (invalid_float_abort) {
          MatWaveDwt::SetErrMsg(
            "Invalid floating point value : %lf",
            (double) sigIn[count]
          );
          return(-1);
        }
        sigOut[count + addLen] = 0.0;
      }
      else {
        sigOut[count + addLen] = sigIn[count];
      }
    }
  if (! addLen) return(0);

  switch (leftExtMethod) {
  case MatWaveBase::ZPD: break;
  case MatWaveBase::SYMH:
    {
      for (count = 0; count < addLen; count++)
	{
	  sigOut[count] = sigIn[addLen - count - 1];
	}
      break;
    }
  case MatWaveBase::SYMW:
    {
      for (count = 0; count < addLen; count++)
	{
	  sigOut[count] = sigIn[addLen - count];
	}
      break;
    }
  case MatWaveBase::ASYMH:
    {
      for (count = 0; count < addLen; count++)
	{
	  sigOut[count] = sigIn[addLen - count - 1] * (-1);
	}
      break;
    }
  case MatWaveBase::ASYMW:
    {
      for (count = 0; count < addLen; count++)
	{
	  sigOut[count] = sigIn[addLen - count] * (-1);
	}
      break;
    }
  case MatWaveBase::SP0:
    {
      for (count = 0; count < addLen; count++)
	{
	  sigOut[count] = sigIn[0];
	}
      break;
    }
  case MatWaveBase::SP1:
    {
      for (count = (addLen - 1); count >= 0; count--)
	{
		sigOut[count] = sigIn[0]-(sigIn[1]-sigIn[0])*(addLen-count);
	}
      break;
    }
  case MatWaveBase::PPD:
    {
      for (count = 0; count < addLen; count++)
	{
	  sigOut[count] = sigIn[sigInLen - addLen + count];
	}
      break;
    }
  case MatWaveBase::PER:
    {
      if (sigInLen%2 == 0)
	{
	  for (count = 0; count < addLen; count++)
	    {
	      sigOut[count] = sigIn[sigInLen - addLen + count];
	    }
	}
      else
	{
	  sigOut[addLen-1] = sigIn[sigInLen-1];
	  addLen--;
	  for (count = 0; count < addLen; count++)
	    {
	      sigOut[count] = sigIn[sigInLen - addLen + count];
	    }
	}
      break;
    }
  default: break;
  }

  switch (rightExtMethod) {
  case MatWaveBase::ZPD: break;
  case MatWaveBase::SYMH:
    {
      for (count = 0; count < addLen; count++)
	{
	  sigOut[count + sigInLen + addLen] =
	    sigIn[sigInLen - count - 1];
	}
      break;
    }
  case MatWaveBase::SYMW:
    {
      for (count = 0; count < addLen; count++)
	{
	  sigOut[count + sigInLen + addLen] =
	    sigIn[sigInLen - count - 2];
	}
      break;
    }
  case MatWaveBase::ASYMH:
    {
      for (count = 0; count < addLen; count++)
	{
	  sigOut[count + sigInLen + addLen] =
	    sigIn[sigInLen - count - 1] * (-1);
	}
      break;
    }
  case MatWaveBase::ASYMW:
    {
      for (count = 0; count < addLen; count++)
	{
	  sigOut[count + sigInLen + addLen] =
	    sigIn[sigInLen - count - 2] * (-1);
	}
      break;
    }
  case MatWaveBase::SP0:
    {
      for (count = 0; count < addLen; count++)
	{
	  sigOut[count + sigInLen + addLen] = 
	    sigIn[sigInLen - 1];
	}
      break;
    }
  case MatWaveBase::SP1:
    {
      for (count = (addLen - 1); count >= 0; count--)
	{
		sigOut[sigInLen + 2 * addLen - count - 1] = 
			sigIn[sigInLen - 1] - (sigIn[sigInLen-2] - sigIn[sigInLen-1])*(addLen-count);
	}
      break;
    }
  case MatWaveBase::PPD:
    {
      for (count = 0; count < addLen; count++)
	{
	  sigOut[count + sigInLen + addLen] = sigIn[count];
	}
      break;
    }
  case MatWaveBase::PER:
    {
      if (sigInLen%2 == 0)
	{
	  for (count = 0; count < addLen; count++)
	    {
	      sigOut[count + sigInLen + addLen] = sigIn[count];
	    }
	}
      else
	{
	  sigOut[addLen+sigInLen] = sigIn[sigInLen-1];
	  addLen--;
	  for (count = 0; count < addLen; count++)
	    {
	      sigOut[count + sigInLen + addLen+2] = sigIn[count];
	    }
	}
      break;
    }
  default: break;
  }

  return(0);
}

//
// Perform single-level, 1D forward wavelet transform 
// (convolution + downsampling)
//
//
// The number of samples computed for both cA and cD is: sigInLen / 2
//
// If oddlow is true the odd indexed low pass samples are computed (the first
// input sample is ignored), else the even samples are computed. This
// parameter provides control over the centering of the filter. Similar
// for the oddhigh parameter.
//
// sigIn must contain sigInLen + filterLen + 1 samples if oddlow or oddhigh
// is true, otherwise sigInLen + filterLen samples are required
//
// See G. Strang and T. Nguyen, "Wavelets and Filter Banks", chap 8, finite
// length filters
//
void
forward_xform (
	const double *sigIn, size_t sigInLen, 
	const double *low_filter, const double *high_filter, 
	int filterLen, double *cA, double *cD, bool oddlow, bool oddhigh
) {
	assert(sigInLen > filterLen);

	size_t xlstart = oddlow ? 1 : 0;
	size_t xl;
	size_t xhstart = oddhigh ? 1 : 0;
	size_t xh;

	for (size_t yi = 0; yi < sigInLen; yi += 2) {
		cA[yi>>1] = cD[yi>>1] = 0.0;

		xl = xlstart;
		xh = xhstart;

		for (int k = filterLen - 1; k >= 0; k--) {
			cA[yi>>1] += low_filter[k] * sigIn[xl];
			cD[yi>>1] += high_filter[k] * sigIn[xh];
			xl++;
			xh++;
		}
		xlstart+=2;
		xhstart+=2;
	}

	return;
}

void
inverse_xform_even (
	const double *cA, const double *cD, size_t sigInLen, 
	const double *low_filter, const double *high_filter, 
	int filterLen, double *sigOut, bool matlab
) {
	size_t xi; // input and out signal indecies
	int k; // filter index

	assert((filterLen % 2) == 0);

	for (size_t yi = 0; yi < 2*sigInLen; yi++ ) {
		sigOut[yi] = 0.0;

		if (matlab  || (filterLen>>1)%2) { // odd length half filter
			xi = yi >> 1;
			if (yi % 2) {
				k =  filterLen - 1;
			} else {
				k =  filterLen - 2;
			}
		} else {
			xi = (yi+1) >> 1;
			if (yi % 2) {
				k = filterLen - 2;
			} else {
				k = filterLen - 1;
			}
		}

		for (; k >= 0; k-=2) {
			sigOut[yi] += (low_filter[k] * cA[xi]) + (high_filter[k] * cD[xi]);
			xi++;
		}
	}

	return;
}

//
// Inverse transform for odd length, symmetric filters. In this case
// it is assumed that cA coefficients come from even indexed samples
// and cD coefficients come from odd indexed samples.
//
// See G. Strang and T. Nguyen, "Wavelets and Filter Banks", 
// chap 8, finite length filters
//
void
inverse_xform_odd (
	const double *cA, const double *cD, size_t sigInLen, 
	const double *low_filter, const double *high_filter, 
	int filterLen, double *sigOut
) {
	size_t xi; // input and out signal indecies
	int k; // filter index

	assert((filterLen % 2) == 1);

	for (size_t yi = 0; yi < 2*sigInLen; yi++ ) {
		sigOut[yi] = 0.0;

		xi = (yi+1) >> 1;
		if (yi % 2) {
			k = filterLen - 2;
		} else {
			k = filterLen - 1;
		}
		for (; k >= 0; k-=2) {
			sigOut[yi] += (low_filter[k] * cA[xi]);
			xi++;
		}

		xi = (yi) >> 1;
		if (yi % 2) {
			k = filterLen - 1;
		} else {
			k = filterLen - 2;
		}
		for (; k >= 0; k-=2) {
			sigOut[yi] += (high_filter[k] * cD[xi]);
			xi++;
		}

	}

	return;
}
}

//#define Minimum(a,b) ((a<b)?a:b)
//#define BlockSize 32

 


MatWaveDwt::MatWaveDwt(
	const string &wname, const string &mode
) : MatWaveBase(wname, mode) {

	_dwt1dBufSize = 0;
	_dwt1dBuf = NULL;
} 

MatWaveDwt::~MatWaveDwt() {
	if (_dwt1dBuf) delete [] _dwt1dBuf;
}

template <class T, class U>
int dwt_template(
	MatWaveDwt *dwt,
	const T *sigIn, size_t sigInLen, const WaveFiltBase *wf,
	MatWaveBase::dwtmode_t mode,
	U *cA, U *cD, size_t L[3], double **buf, size_t *bufsize
) {

	if (dwt->wmaxlev(sigInLen) < 1) {
		MatWaveDwt::SetErrMsg("Can't transform signal of length : %d", sigInLen);
		return(-1);
	}
	if (mode == MatWaveBase::PER) {
		MatWaveDwt::SetErrMsg("Invalid boundary extension mode: %d", mode);
		return(-1);
	}

	L[0] = dwt->approxlength(sigInLen);
	L[1] = dwt->detaillength(sigInLen);
	L[2] = sigInLen;


	int filterLen = wf->GetLength();

	//
	// See if we can do symmetric convolution
	//
	bool do_sym_conv = false;
	if (wf->issymmetric()) {
		if (
			(mode == MatWaveBase::SYMW && (filterLen % 2)) ||
			(mode == MatWaveBase::SYMH && (! (filterLen % 2)))
		)  {
		
			do_sym_conv = true;
		}
	}

	//cout << "filter length " << filterLen << endl;
	//printmatrix1d("dwt: low pass decomp filter", wf->GetLowDecomFilCoef(), filterLen);
	//printmatrix1d("dwt: high pass decomp filter", wf->GetHighDecomFilCoef(), filterLen);
	//cout << endl;
	printmatrix1d("dwt: input signal", sigIn,sigInLen);

	// length of signal after boundary extension. We extend both
	// left and right boundary by the width of the filter. 
	//
	size_t sigConvolvedLen =  L[0] + L[1];
	size_t extendLen;

	bool oddlow = true;
	bool oddhigh = true;
	if (filterLen % 2) oddlow = false;
	if (do_sym_conv) {
		extendLen = filterLen>>1;
		if (sigInLen % 2) sigConvolvedLen += 1;
	}
	else {
		extendLen = filterLen-1;
	}
	size_t sigExtendedLen = sigInLen + (2*extendLen);

	*buf = buf_alloc(
		*buf, bufsize, sigExtendedLen + sigConvolvedLen
	);
	if (! buf) return(-1);

	double *sigExtended = *buf;
	double  *sigConvolved = sigExtended + sigExtendedLen;

	// Signal boundary extension
	//
	int rc = wextend_1D_center(
		sigIn, sigInLen, sigExtended, 
		extendLen, mode, mode, dwt->InvalidFloatAbortOnOff()
	);
	if (rc<0) return(-1);
	printmatrix1d("dwt: extended signal", sigExtended, sigExtendedLen);

	forward_xform(
		sigExtended, L[0]+L[1], wf->GetLowDecomFilCoef(), 
		wf->GetHighDecomFilCoef(), filterLen, sigConvolved, sigConvolved+L[0],
		oddlow, oddhigh
	);

	for (size_t i=0; i<L[0]; i++) {
		cA[i] = sigConvolved[i];
	}
	printmatrix1d("dwt: convolved lowpass signal", cA, L[0]);

	for (size_t i=0; i<L[1]; i++) {
		cD[i] = sigConvolved[i+L[0]];
	}
	printmatrix1d("dwt: convolved high signal", cD, L[1]);

	return(0);

}


int MatWaveDwt::dwt(
	const double *sigIn, size_t sigInLen, double *C, size_t L[3]
) {
	double *cA = C;
	double *cD = C + approxlength(sigInLen);

	return(dwt_template(this,
		sigIn, sigInLen, wavelet(), dwtmodeenum(), cA, cD, L,
		&_dwt1dBuf,  &_dwt1dBufSize
	));
}

int MatWaveDwt::dwt(
	const float *sigIn, size_t sigInLen, float *C, size_t L[3]
) {
	float *cA = C;
	float *cD = C + approxlength(sigInLen);
	return(dwt_template(this,
		sigIn, sigInLen, wavelet(), dwtmodeenum(), cA, cD, L,
		&_dwt1dBuf,  &_dwt1dBufSize
	));
}

int MatWaveDwt::dwt(
	const double *sigIn, size_t sigInLen, double *cA, double *cD, size_t L[3]
) {
	return(dwt_template(this,
		sigIn, sigInLen, wavelet(), dwtmodeenum(), cA, cD, L,
		&_dwt1dBuf,  &_dwt1dBufSize
	));
}

int MatWaveDwt::dwt(
	const float *sigIn, size_t sigInLen, float *cA, float *cD, size_t L[3]
) {
	return(dwt_template(this,
		sigIn, sigInLen, wavelet(), dwtmodeenum(), cA, cD, L,
		&_dwt1dBuf,  &_dwt1dBufSize
	));
}


template <class T, class U>
int idwt_template(
	MatWaveDwt *dwt,
	const T *cA, const T *cD, const size_t L[3], const WaveFiltBase *wf,
	MatWaveBase::dwtmode_t mode, U *sigOut,
	double **buf, size_t *bufsize
) {
	if (mode == MatWaveBase::PER) {
		MatWaveDwt::SetErrMsg("Invalid boundary extension mode: %d", mode);
		return(-1);
	}

	int filterLen = wf->GetLength();

	bool do_sym_conv = false;
	MatWaveBase::dwtmode_t cALeftMode = mode;
	MatWaveBase::dwtmode_t cARightMode = mode;
	MatWaveBase::dwtmode_t cDLeftMode = mode;
	MatWaveBase::dwtmode_t cDRightMode = mode;
	if (wf->issymmetric()) {
		if (
			(mode == MatWaveBase::SYMW && (filterLen % 2)) ||
			(mode == MatWaveBase::SYMH && (! (filterLen % 2)))
		)  {

			if (mode == MatWaveBase::SYMH) {
				cDLeftMode = MatWaveBase::ASYMH;
				if (L[2]%2) {
					cARightMode = MatWaveBase::SYMW;
					cDRightMode = MatWaveBase::ASYMW;
				}
				else {
					cDRightMode = MatWaveBase::ASYMH;
				}
			}
			else {
				cDLeftMode = MatWaveBase::SYMH;
				if (L[2]%2) {
					cARightMode = MatWaveBase::SYMW;
					cDRightMode = MatWaveBase::SYMH;
				}
				else {
					cARightMode = MatWaveBase::SYMH;
				}
			}
		
			do_sym_conv = true;
		}
	}

	size_t cATempLen, cDTempLen, reconTempLen;

	size_t extendLen = 0;
	size_t cDPadLen = 0;
	if (do_sym_conv) {
		extendLen = filterLen>>2;
		if ((L[0] > L[1]) && (mode == MatWaveBase::SYMH)) cDPadLen = L[0];

		cATempLen = L[0] + (2*extendLen);

		// cD length must be same as cA (it's not for odd length signals)
		//
		cDTempLen = cATempLen;
	} else {
		cATempLen = L[0];
		cDTempLen = L[1];
	}
	reconTempLen = L[2];
	if (reconTempLen % 2) reconTempLen++;

	*buf = buf_alloc(
		*buf, bufsize, 
		cATempLen + cDTempLen + reconTempLen + cDPadLen
	);
	if (! buf) return(-1);


	double *cATemp = *buf;
	double *cDTemp = cATemp + cATempLen;
	double *reconTemp = cDTemp + cDTempLen;
	double *cDPad = reconTemp + reconTempLen;

	//printmatrix1d("idwt: low pass reconstruct filter", wf->GetLowReconFilCoef(), filterLen);
	//printmatrix1d("idwt: high pass reconstruct filter", wf->GetHighReconFilCoef(), filterLen);
	//cout << endl;

	// For symmetric filters we need to add the boundary coefficients 
	//
	if (do_sym_conv) {
		int rc = wextend_1D_center(
			cA, L[0], cATemp, 
			extendLen, cALeftMode, cARightMode, dwt->InvalidFloatAbortOnOff()
		);
		if (rc<0) return(-1);

		// For odd length signals we need to add back the missing final cD 
		// coefficient before signal extension.
		// See G. Strang and T. Nguyen, "Wavelets and Filter Banks", 
		// chap 8, finite length filters
		// 
		if (cDPadLen) {
			for (size_t i=0; i<L[1]; i++) cDPad[i] = cD[i];
			cDPad[L[1]] = 0.0;

			rc = wextend_1D_center(
				cDPad, L[0], cDTemp, extendLen, cDLeftMode, cDRightMode, 
				dwt->InvalidFloatAbortOnOff()
			);
			if (rc<0) return(-1);
		}
		else {
			rc = wextend_1D_center(
				cD, L[1], cDTemp, extendLen, cDLeftMode, cDRightMode, 
				dwt->InvalidFloatAbortOnOff()
			);
			if (rc<0) return(-1);
		}
	}
	else {
		for (size_t i=0; i<L[0]; i++) {
      		if (! isfinite((double) cA[i])) {
				if (dwt->InvalidFloatAbortOnOff()) {
					MatWaveDwt::SetErrMsg(
						"Invalid floating point value : %lf", (double) cA[i]
					);
					return(-1);
				}
				cATemp[i] = 0.0;
			}
			else {
				cATemp[i] = (double) cA[i];
			}
		}

		for (size_t i=0; i<L[1]; i++) {
      		if (! isfinite((double) cD[i])) {
				if (dwt->InvalidFloatAbortOnOff()) {
					MatWaveDwt::SetErrMsg(
						"Invalid floating point value : %lf", (double) cD[i]
					);
					return(-1);
				}
				cDTemp[i] = 0.0;
			}
			else {
				cDTemp[i] = (double) cD[i];
			}
		}
		
	}

	printmatrix1d("idwt: extended cA signal", cATemp, cATempLen);
	printmatrix1d("idwt: extended cD signal", cDTemp, cDTempLen);

	if (filterLen % 2) {
		
		inverse_xform_odd (
			cATemp, cDTemp, L[0], wf->GetLowReconFilCoef(), 
			wf->GetHighReconFilCoef(), filterLen,
			reconTemp 
		);
	}
	else {
		inverse_xform_even (
			cATemp, cDTemp, L[0], wf->GetLowReconFilCoef(), 
			wf->GetHighReconFilCoef(), filterLen,
			reconTemp, ! do_sym_conv
		);
	}

	for(size_t count=0;count<L[2];count++) {
		sigOut[count] = (U) reconTemp[count];
	}
	printmatrix1d("idwt: reconstructed signal", sigOut, L[2]);

	return(0);
}

int MatWaveDwt::idwt(
	const double *C, const size_t L[3], double *sigOut
) {
	const double *cA = C;
	const double *cD = C + L[0];
	return idwt_template(
		this, cA, cD, L, wavelet(), dwtmodeenum(), sigOut, 
		&_dwt1dBuf,  &_dwt1dBufSize
	);
} 

int MatWaveDwt::idwt(
	const float *C, const size_t L[3], float *sigOut
) {
	const float *cA = C;
	const float *cD = C + L[0];
	return idwt_template(
		this, cA, cD, L, wavelet(), dwtmodeenum(), sigOut, 
		&_dwt1dBuf,  &_dwt1dBufSize
	);
} 

int MatWaveDwt::idwt(
	const double *cA, const double *cD, const size_t L[3], double *sigOut
) {
	return idwt_template(
		this, cA, cD, L, wavelet(), dwtmodeenum(), sigOut, 
		&_dwt1dBuf,  &_dwt1dBufSize
	);
} 

int MatWaveDwt::idwt(
	const float *cA, const float *cD, const size_t L[3], float *sigOut
) {
	return idwt_template(
		this, cA, cD, L, wavelet(), dwtmodeenum(), sigOut,
		&_dwt1dBuf,  &_dwt1dBufSize
	);
} 


