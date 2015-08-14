#include <string>
#include "WaveFiltBase.h"

using namespace std;

#ifndef	_WaveFiltBior_h_
#define	_WaveFiltBior_h_

namespace ante_dwt {

//
//! \class WaveFiltBior
//! \brief Biorthogonal spline family FIR filters
//! \author cem
//! \version $Revision$
//! \date    $Date$
//!
//! This class provides FIR filters for the Biorlet family of wavelets
//!
class WaveFiltBior : public WaveFiltBase {

public:

 //! Create a set of Biorthogonal spline filters
 //!
 //! \param[in] wavename The Biorlet family wavelet member. Valid values
 //! are "bior1.1", "bior1.3", "bior1.5", "bior2.2", "bior2.4",
 //! "bior2.6", "bior2.8", "bior3.1", "bior3.3", "bior3.5", "bior3.7",
 //! "bior3.9"
 //!
 WaveFiltBior(const string &wavename);
 virtual ~WaveFiltBior();

 //! Returns true if the wavelet is symmetric (or antisymmetric)
 //!
 virtual bool issymmetric() const { return(true); };
	

private:
 void _analysis_initialize (int member);
 void _synthesis_initialize (int member);
};

}

#endif
