
#ifndef	_WaveFiltHaar_h_
#define	_WaveFiltHaar_h_

namespace ante_dwt {

//
//! \class WaveFiltHaar
//! \brief Haar FIR filters
//! \author cem
//! \version $Revision$
//! \date    $Date$
//!
//! This class provides FIR filters for the Haar wavelet
//!
class WaveFiltHaar : public WaveFiltBase {

public:
 //! Create a set of Haar wavelet filters
 //!
 WaveFiltHaar();
 virtual ~WaveFiltHaar();
	

private:
 void _analysis_initialize();
 void _synthesis_initialize();
};

}

#endif
