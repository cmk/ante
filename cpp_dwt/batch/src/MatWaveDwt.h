
#ifndef	_MatWaveDwt_h_
#define	_MatWaveDwt_h_

#include "MatWaveBase.h"

namespace ante_dwt {

//
//! \class MatWaveDwt
//! \brief Implements a single level wavelet filter
//! \author cem
//! \version $Revision$
//! \date    $Date$
//!


class MatWaveDwt : public MatWaveBase {

public:

 //! Create a wavelet filter bank 
 //!
 //! \param[in] wname The name of the wavelet to apply.
 //! \param[in] mode The boundary extension mode.
 //!
 //! \sa dwtmode()
 //!
 MatWaveDwt(const string &wname, const string &mode);
 virtual ~MatWaveDwt();

 //! Single-level discrete 1D wavelet transform
 //!
 //! This method performs a single-level, one-dimensional wavelet 
 //! decomposition with respect to the current wavelet 
 //! and boundary extension mode.
 //!
 //! \param[in] sigIn The discrete signal
 //! \param[in] sigInLength The length of \p sigIn
 //! \param[out] C The wavelet decompostion vector. The length of \p C, 
 //! must be equal to
 //! the value returned by MatWaveWavedec::coefflength().
 //! \param[out] cA The wavelet decompostion vector approximation coefficients
 //! \param[out] cD The wavelet decompostion vector detail coefficients
 //! \param[out] L[3] The book keeping vector.  The length of \p L, must 
 //! be equal to 3. \p L[0] provides the length of the approximation
 //! coefficients, \p L[1] provides the length of the detail coefficients,
 //! and \p L[2] is equal to \p sigInLength.
 //!
 //! \retval status A negative number indicates failure.
 //!
 //! \sa MatWaveBase::coefflength(), idwt()
 //
 int dwt(const double *sigIn, size_t sigInLength, double *C, size_t L[3]);
 int dwt(const float *sigIn, size_t sigInLength, float *C, size_t L[3]);
 int dwt(const double *sigIn, size_t sigInLength, double *cA, double *cD, size_t L[3]);
 int dwt(const float *sigIn, size_t sigInLength, float *cA, float *cD, size_t L[3]);

 //! Single-level inverse discrete 1D wavelet transform
 //!
 //! This method performs a single-level, one-dimensional wavelet 
 //! reconstruction with respect to the current wavelet and
 //! boundary extension mode.
 //!
 //! \param[in] C The Wavelet decomposition vector, dimensioned according
 //! to \p L.
 //! \param[in] cA The wavelet decompostion vector approximation coefficients
 //! \param[in] cD The wavelet decompostion vector detail coefficients
 //! \param[in] L[3] The Wavelet decomposition book keeping vector.
 //! \param[out] sigOut Single-level reconstruction approximation based
 //! on the approximation and detail coefficients (\p C). The length of
 //! \p sigOut is must be \p L[2].
 //!
 //! \retval status A negative number indicates failure.
 //!
 //! \sa MatWaveBase::coefflength(), dwt()
 //
 int idwt(const double *C, const size_t L[3], double *sigOut); 
 int idwt(const float *C, const size_t L[3], float *sigOut); 
 int idwt(const double *cA, const double *cD, const size_t L[3], double *sigOut); 
 int idwt(const float *cA, const float *cD, const size_t L[3], float *sigOut); 


private:

 // 1D buffers
 size_t _dwt1dBufSize;
 double *_dwt1dBuf;

};

}

#endif


