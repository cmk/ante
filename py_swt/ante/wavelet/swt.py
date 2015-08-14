from ante import dwtArray, wtfilter
import numpy as np

def swt(X, wtf='d4', nlevels='conservative', RetainVJ=False):
    """
    function swt(X, wtf='d4', nlevels='conservative', RetainVJ=False)
    
    NAME
       swt -- Compute the (partial) stationary wavelet transform (SWT).
    
    INPUTS
       X          -- set of observations 
                    (vector of length NX)
       wtf        -- (optional) wavelet transform filter name
                     (string, case-insensitve or wtf struct).
                     Default:  'd4'
       nlevels    -- (optional) maximum level J0 (integer) 
                     or method of calculating J0 (string).
                     Valid values: integer>0 or a valid method name
                     Default:  'conservative'
       RetainVJ   -- (optional) boolean flag to retain V at each
                     decomposition level
                     Default: False
    
    OUTPUTS
       WJt        -- SWT wavelet coefficents (J x NW array) dwtArray
       VJt        -- SWT scaling coefficients ((1 or J) x NW vector) dwtArray
    
    DESCRIPTION
       swt calculates the wavelet and scaling coefficients using the stationary wavelet transform (SWT).
    
       The optional input arguments have default values:
       * wtf      -- 'd4' filter
       * nlevels  -- 'convservative' --> J0 < log2( N / (L-1) + 1)
    
       The output arguments include an info attribute with metadata.
       info is a dictionary with the following fields:
       * Transform  -- name of transform ('SWT')
       * WTF        -- name of wavelet transform filter or a wtf_s struct.
       * NX         -- number of observations in original series (= length(X))
       * NW         -- number of wavelet coefficients
       * J0         -- number of levels of partial decompsition.
       * Aligned    -- Boolean flag indicating whether coefficients are aligned
                       with original series (1 = true) or not (0 = false).
       * RetainVJ -- Boolean flag indicating whether VJ scaling coefficients
                       at all levels have been retained (1= true) or not (0 = false).
    
    EXAMPLE
       WJt, VJt = swt(X, 'd4', 6)
    
    NOTES 
       pages 177-178 of P&W 
    
    SEE ALSO
       swtj, swt_filter, swt_choose_nlevels, nargerr, argterr, dwtArray
    """

    # Get a valid wavelet transform filter coefficients struct.
    wtf_s = wtfilter(wtf)
  
    wtfname = wtf_s.Name
    gt = wtf_s.g
    ht = wtf_s.h
    L  = wtf_s.L

    # ensure X is a numpy array
    X = np.array(X)
    if len(X.shape)>1:
        raise ValueError('SWT: Input array must be one-dimensional')
    #  N    = length of original series
    N = X.size
        
    #  If nlevels is an integer > 0, set J0 = nlevels.
    #  otherwise, select J0 based on choice method specified.
    if isinstance(nlevels, str):
        J0 = swt_choose_nlevels(nlevels, wtfname, N)
    elif isinstance(nlevels, int):
        if nlevels > 0:
            J0 = nlevels
        else:
            raise ValueError('SWT:negativeJ0, nlevels must be an integer greater than 0.')
    else:
        raise ValueError('SWT:invalidNLevelsValue')
    
    if (J0 < 0):
        raise ValueError('SWT:negativeJ0')
    
    if (2**J0 > N):
        raise ValueError('SWT:LargeJ0', 'JO must be < log2(Number of samples).')

    # NW = length of the extended series = number of coefficients
    NW = X.size
    # Initialize the scale (Vin) for first level by setting it equal to X
    Vin = X
    # Pre-allocate memory.
    WJt = np.ndarray((J0, NW), dtype=np.float64)*np.nan
    if RetainVJ:
        VJt = np.ndarray((J0, NW), dtype=np.float64)*np.nan
    else:
        VJt = np.ndarray((NW), dtype=np.float64)*np.nan

    # Do the SWT.
    from swtj import swtj
    bw = np.ndarray((J0,2))*np.nan
    for j in range(J0):
        Wt_j, Vout = swtj(Vin, j+1, ht, gt)
        WJt[j,:]   = Wt_j
        Vin        = Vout
        if RetainVJ:
            VJt[j,:] = Vout
            
        # boundary values 198 
        L_j     = equivalent_filter_width(L, j+1)
        bw[j,0] = min(L_j - 2, NW-1) #max index to the left
        #bw[j,1] = np.nan #Bc are only at the beginning
    if not RetainVJ:
        VJt[:] = Vout
        bv     = bw[-1,:]
    else:
        bv     = bw

    # Update attributes
    att = {'Transform':'SWT',
           'WTF'      : wtfname,
           'N'        : N,
           'NW'       : NW,
           'J0'       : J0,
           'Aligned'  : False,
           'RetainVJ' : RetainVJ,
           'Type'     : 'Wavelet',
           'BCs'      : bw
           }
    WJt = dwtArray(WJt, info=att)

    att = {'Transform':'SWT',
           'WTF'      : wtfname,
           'N'        : N,
           'NW'       : NW,
           'J0'       : J0,
           'Aligned'  : False,
           'RetainVJ' : RetainVJ,
           'Type'     : 'Scaling',
           'BCs'      : bv
           }
    VJt = dwtArray(VJt, info=att)
    
    return WJt, VJt


def iswt_details(WJt):
    """
    iswt_details -- Calculate details via inverse stationary wavelet transform (ISWT).

    NAME
       iswt_details -- Calculate details via inverse stationary wavelet transform (ISWT).
    
    INPUTS
       WJt          -  NxJ array of SWT wavelet coefficents
                       where N  = number of time points
                             J = number of levels.
                       The array must be a dwtArray (containing the information on the transform)
    
    OUTPUT
       DJt          -  JxN dwtArray of reconstituted details of data series for J0 scales.
       att          -  structure containing ISWT transform attributes.
    
    DESCRIPTION
       The output parameter att is a structure with the following fields:
           name      - name of transform (= 'SWT')
           wtfname   - name of SWT wavelet filter
           npts      - number of observations (= length(X))
           J0        - number of levels 
    
    EXAMPLE
       DJt = iswt_details(WJt)
    
    SEE ALSO
       iswtj, iswt, iswt_smooth, swt_filter, swt
    """
    
    # Get a the wavelet transform filter coefficients.
    if type(WJt) is not dwtArray:
        raise TypeError('Input must be a dwtArray')
    if WJt.info['Type'] is not 'Wavelet':
        raise TypeError('Input array does not contain Wavelet coefficients but {} coefficients'.format(WJt.info['Type']))
    wtfname = WJt.info['WTF']
    wtf_s   = wtfilter(wtfname)
  
    gt = wtf_s.g
    ht = wtf_s.h
    L  = wtf_s.L

    J,N = WJt.shape
    J0  = J

    zeroj = np.zeros(N)
    DJt   = np.zeros((J, N))

    from swtj import iswtj 
    for j in range(J0-1,-1,-1):
        Vin = zeroj
        Win = WJt[j,:]
        for jj in range(j,-1,-1):
            Vout = iswtj(Win, Vin, jj+1, ht, gt)
            Win = zeroj
            Vin = Vout
        DJt[j,:] = Vout
    
    # boundary values 199 
    bw = np.ndarray((J0,2), dtype=np.int16)*np.nan
    for j in range(J0):
        #Calculate wavelet coefficient boundary indices at jth level
        L_j     = equivalent_filter_width(L, j+1)
        bw[j,0] = L_j-2; bw[j,1]= -L_j+1
    # Update attributes
    att = dict(WJt.info.items() +
               {'Transform':'ISWT',
                'Type'     :'Detail',
                'BCs'      :bw}.items())
    
    DJt = dwtArray(DJt, info=att)

    return DJt

def iswt_smooth(VJt):
    """
    iswt_smooth -- Calculate smooths at J0 level via inverse stationary wavelet transform (ISWT).

    NAME
       iswt_smooth -- Calculate smooths at J0 level via inverse stationary wavelet transform (ISWT).
    
    INPUTS
       VJt          =  N dwtArray of SWT scaling coefficients at J0 level.
    
    OUTPUT
       SJOt         =  dwtArray of reconstituted smoothed data series.
    
    DESCRIPTION
    
    EXAMPLE
       SJt = iswt_smooth(VJt)
    
    SEE ALSO
       iswtj, iswt, iswt_details, swt_filter, swt
    """

    # Get the wavelet transform filter coefficients.
    if type(VJt) is not dwtArray:
        raise TypeError('Input must be a dwtArray')
    if VJt.info['Type'] is not 'Scaling':
        raise TypeError('Input array does not contain Scaling coefficients but {} coefficients'.format(VJt.info['Type']))
    wtfname = VJt.info['WTF']
    J0      = VJt.info['J0']
    wtf_s   = wtfilter(wtfname)
    gt = wtf_s.g
    ht = wtf_s.h
    L  = wtf_s.L

    if len(VJt.shape)>1:
        if VJt.info['RetainVJ']:
            VJt = VJt[-1,:]
        else:
            raise TypeError('The input is a multidimensional array but {} SWT\
             has been computed with RetainVJ=False. \n')
    N = VJt.size

    # initialize arrays
    zeroj = np.zeros(N)

    Vin = VJt

    #import pyximport; pyximport.install()
    from swtj import iswtj
    for j in range(J0-1,-1,-1):
        Vout = iswtj(zeroj, Vin, j+1, ht, gt)
        Vin  = Vout

    SJt = Vout
    
    # boundary values pag.199 P&W
    L_j = equivalent_filter_width(L, J0)
    bv  = np.array([L_j-2, -L_j+1])

    # Update attributes
    att = dict(VJt.info.items() +
               {'Transform':'ISWT',
                'Type'     :'Smooth',
                'BCs'      :bv}.items())

    SJt = dwtArray(SJt, info=att)

    return SJt

def iswt_mra(WJt, VJt):
    """
    iswt_mra -- Calculate SWT multi-resolution details and smooths from wavelet coefficients via ISWT transform.

    NAME
    iswt_mra -- Calculate SWT multi-resolution details and smooths from wavelet coefficients via ISWT transform.
    
    INPUTS
       * WJt        -- SWT wavelet coefficents (J x N).
       * VJt        -- SWT scaling coefficients ((1 or J) x N)
    
    OUTPUT
       * DJt        -- SWT details coefficents (J x N).
       * SJt        -- SWT smooth coefficients ((1 or J) x N)
    
    DESCRIPTION
       swt_mra computes the multi-resolution detail and smooth coefficients
       from the SWT wavelet and scaling coefficients.
    
    EXAMPLE
       DJt, SJt = iswt_smooth(WJt, VJt)

    SEE ALSO
       iswt_details, iswt_smooth, iswtj, swt, swt_filter
    """

    # check input
    if type(WJt) is not dwtArray:
        raise TypeError('Input must be a dwtArray')
    if WJt.info['Type'] is not 'Wavelet':
        raise TypeError('First input array does not contain Wavelet coefficients but {} coefficients'.format(WJt.info['Type']))
    if type(VJt) is not dwtArray:
        raise TypeError('Input must be a dwtArray')
    if VJt.info['Type'] is not 'Scaling':
        raise TypeError('Second input array does not contain Scaling coefficients but {} coefficients'.format(VJt.info['Type']))

    DJt = iswt_details(WJt)
    SJt = iswt_smooth(VJt)

    return DJt, SJt

def swt_cir_shift(WJt, VJ0t, subtract_mean_VJ0t=True):
    """
    shift_swt_coef -- shift the SWT wavelet and scaling coefficients.
    
    NAME
        shift_swt_coef -- Shift the SWT wavelet and scaling coefficients.

    INPUTS
        WJt          =  JxN dwtArray of SWT wavelet coefficents
                        where N = number of time intervals,
                              J = number of levels
        VJ0t         =  N dwtArray of SWT scaling coefficients at level J0.

        subtract_mean_VJ0t = (optional) subtract mean value of scaling coefficient 
                        from itself
                        Default: True

    OUTPUTS
        W  = shifted wavelet coefficients with boundary conditions (dwtArray)
        V  = shifted scaling coefficients with boundary conditions (dwtArray)
        boundaries = demarcing the circularly shifted SWT coefficients influenced 
                     by the circularity conditions
        bw  = Jx2 array with min/max indices of wavelet boundary coefficients
        bv  = Jx2 array with min/max indices of scaling boundary coefficients

    DESCRIPTION
       The SWT coefficients are circularly shifted at each level so as to 
       properly align the coefficients with the original data series. See P&W fig 183

    SEE ALSO
       swt, swt_filter
       multi_yoffset_plot
    """
    
    # check input
    if type(WJt) is not dwtArray:
        raise TypeError('Input must be a dwtArray')
    if WJt.info['Type'] is not 'Wavelet':
        raise TypeError('First input array does not contain Wavelet coefficients but {} coefficients'.format(WJt.info['Type']))
    if type(VJ0t) is not dwtArray:
        raise TypeError('Input must be a dwtArray')
    if VJ0t.info['Type'] is not 'Scaling':
        raise TypeError('Second input array does not contain Scaling coefficients but {} coefficients'.format(VJ0t.info['Type']))
    
    wtf_s = wtfilter(WJt.info['WTF'])
    L  = wtf_s.L

    wtfname       = WJt.info['WTF']
    N             = WJt.info['N']
    NW            = WJt.info['NW']
    J0            = WJt.info['J0']
    
    Nm = min(N,NW)

    if WJt.info['Aligned']:
        print ('WARNING (ante.swt.swt_cir_shift): Wavelet coefficients are already aligned')
        W = WJt
    else:
        if WJt!=dwtArray([]):
            W = np.ndarray(WJt.shape)*np.nan; bw = np.ndarray((J0,2))*np.nan
            for j in range(J0):
                # shift wavelet coefficients
                nuHj = advance_wavelet_filter(wtfname, j+1)
                W[j,:] = np.roll(WJt[j,:], nuHj)
                
                #Calculate circularly shifted wavelet coefficient boundary indices at jth level
                L_j   = equivalent_filter_width(L, j+1)
        
                bw[j,0] = L_j - 2 - np.abs(nuHj) #max index to the left
                bw[j,1] = Nm - abs(nuHj) #min index to the right
            W = W[:,:Nm]
            # Update attributes
            att = dict(WJt.info.items() +
                       {'Aligned':True,
                        'BCs'    :bw
                        }.items())
            W = dwtArray(W, info=att)
        else: # if an empty array was given, do nothing and return it
            W = WJt

    if VJ0t.info['Aligned']:
        print ('WARNING (ante.swt.swt_cir_shift): Wavelet coefficients are already aligned')
        V = VJ0t
    else:
        V = np.ndarray(VJ0t.shape)*np.nan; bv = np.ndarray((2,))*np.nan
        # shift scaling coefficients
        if VJ0t!=dwtArray([]):
            nuGj = advance_scaling_filter(wtfname, J0)
            if subtract_mean_VJ0t:
                VJ0t = VJ0t - VJ0t.mean()
            V    = np.roll(VJ0t, nuGj)
    
            bv[0] = L_j - 2 - np.abs(nuGj) #max index to the left
            bv[1] = Nm - np.abs(nuGj) #min index to the right
            # Update attributes
            att = dict(VJ0t.info.items() +
                       {'Aligned':True,
                        'BCs'    :bv
                        }.items())
            V = dwtArray(V, info=att)
        else: # if an empty array was given, do nothing and return it
            V = VJ0t

    return W,V

def swt_rot_cum_wav_svar(WJt, VJ0t, method='power'):
    """
    swt_cum_wav_svar -- Calculate cumulative sample variance of SWT wavelet coefficients.
    
     NAME
       swt_cum_wav_svar -- Calculate cumulative sample variance of 
             SWT wavelet coefficients.
    
     INPUTS
       WJt          -  JxN dwtArray of SWT wavelet coefficents
                       where N = number of time intervals
                             J = number of levels
                       they can be already rotated or not
       VJ0t         -  N dwtArray of SWT J0 scaling coefficents
                       they can be already rotated or not
       method       - variance estimate returned
                       'power' = |W^2|/N
                       'cum'   = cumulative variance
                       'cumsc' = cumulative "scaled" (see pag.189)
                       Default: 'power' 
    
     OUTPUTS
       cwsvar       -  cumulative wavelet sample variance (dwtArray).
    
     DESCRIPTION
       'cumsc' method is equivalent to the one on pag.189 of P&W .
    
     ALGORITHM
    
       cwsvar[j,t] = 1/N * sum( WJt^2 subscript(j,u+nuH_j mod N)) 
                        for t = 0,N-1 at jth level
       for j in range(J):
        rcwsvar[j,:] = cswvar[j,:] - t*cwsvarN[j]/(N-1.)
      
     SEE ALSO
       swt_cir_shift, swt
    """
    
    if method not in ('power','cum','cumsc'):
        raise ValueError('Valid methods are: "power", "cum" or "cumsc"')
    
    # check input
    if type(WJt) is not dwtArray:
        raise TypeError('Input must be a dwtArray')
    if WJt.info['Type'] is not 'Wavelet':
        raise TypeError('First input array does not contain Wavelet coefficients but {} coefficients'.format(WJt.info['Type']))
    if type(VJ0t) is not dwtArray:
        raise TypeError('Input must be a dwtArray')
    if VJ0t.info['Type'] is not 'Scaling':
        raise TypeError('Second input array does not contain Scaling coefficients but {} coefficients'.format(VJ0t.info['Type']))
    
    # get dimensions
    try:
        J, N   = WJt.shape
    except ValueError:
        N = WJt.size
        J=1
    if len(VJ0t.shape)>1:
        raise ValueError('Only J0 level scaling coefficient should be given')
        
    # rotate if they are not yet aligned
    WJt,VJ0t = swt_cir_shift(WJt, VJ0t) # the check for rotation is done in swt_cir_shift

    pWJt = WJt**2; pVJ0t = VJ0t**2
    
    if method=='power':
        Watt = dict(WJt.info.items() + {'Type':'WavRotPower'}.items())
        Vatt = dict(VJ0t.info.items() + {'Type':'ScalRotPower'}.items())
        return dwtArray(pWJt, info=Watt),dwtArray(pVJ0t, info=Vatt)
    
    cwsvar = np.cumsum(pWJt, axis=1)/N
    swsvar = np.cumsum(pVJ0t)/N

    if method=='cum':
        Watt = dict(WJt.info.items() + {'Type':'WavRotCumVar'}.items())
        Vatt = dict(VJ0t.info.items() + {'Type':'ScalRotCumVar'}.items())
        return dwtArray(cwsvar, info=Watt),dwtArray(swsvar, info=Vatt)

    # compute rotated cumulative variance
    cwsvarN = cwsvar[:,-1]
    swsvarN = swsvar[-1]
    
    t = np.arange(N, dtype=np.float64)

    rcwsvar = cwsvar - t*cwsvarN[:,np.newaxis]/(N-1)
    rswsvar = swsvar - t*swsvarN/(N-1)

    Watt = dict(WJt.info.items() + {'Type':'WavRotCumScVar'}.items())
    Vatt = dict(VJ0t.info.items() + {'Type':'ScalRotCumScVar'}.items())
    return dwtArray(rcwsvar, info=Watt),dwtArray(rswsvar, info=Vatt)
    # orginal algorithm
    #rcwsvar = np.ndarray((J,N)) 
    #for j in range(J):
    #    rcwsvar[j,:] = cswvar[j,:] - t*cwsvarN[j]/(N-1.)
    # vector version
    
def advance_time_series_filter(wtfname):
    """
    advance_time_series_filter -- Calculate the advance of the time series or filter for a given wavelet.
    
    NAME
       advance_time_series_filter -- Calculate the advance of the time series or filter for a given wavelet.
    
    SYNOPSIS
       nu = advance_time_series_filter(wtfname)
    
    INPUTS
       wtfname      -  string containing name of ante-supported wavelet filter.
    
    OUTPUTS
       nu           -  advance of time series for specified wavelet filter.
    
    SIDE EFFECTS
       wavelet is a ante-supported wavelet filter; otherwise error.
    
    DESCRIPTION
    
      For Least Asymmetric filters, equation 112e of P&W:
       nu =   -L/2 + 1,   for L/2 is even;
          =   -L/2,       for L = 10 or 18;
          =   -L/2 + 2,   for L = 14.  
    
      For Best Localized filter, page 119 of P&W
          =   -11,        for L = 18;
          =   -9,         for L = 20.
    
      For Coiflet filters, page 124 and equation 124 of P&W:
       nu =   -2*L/3 + 1
    
    SEE ALSO
       dwt_filter    
    """

    
    def advance_least_asymetric_filter(L):
        #Equation 112c of P&W.
        if (L/2)%2 == 0:
            #L/2 is even, i.e. L = 8, 12, 16, 20
            nu = -(L/2) + 1
        else:
            if L in(10, 18):
                nu = -(L/2)
            elif L==14:
                nu = -(L/2) + 2
            else:
                raise ValueError('Invalid filter length (L = {}) specified.'.format(L))
        return nu

    def advance_best_localized_filter(L):
        #Page 119 of ante.
        if L==14:
            nu = -5
        elif L==18:
            nu = -11
        elif L==20:
            nu = -9
        else:
            pass
        return nu
  
    def advance_coiflet_filter(L):
        #Page 124 and equation 124 of P&W
        nu = -(2 * L / 3) + 1
        return nu

    # Get a valid wavelet transform filter coefficients struct.
    wtf_s = wtfilter(wtfname)
    L     = wtf_s.L

    nu = np.nan

    #Haar
    if wtfname.lower() in ('d2'):
        nu = 0
    # Haar filter
    # Value from Figure 115
    elif wtfname.lower() in ('d4','d6', 'd8'):
        nu = -1
    #Extremal Phase filters
    #case {'d2', 'd4', 'd6', 'd8', 'd12', 'd14', 'd16', 'd18', 'd20'}
    elif wtfname.lower() in ('d12', 'd14', 'd16', 'd18', 'd20'):
        raise ValueError('Need to determine nu for this Extremal Phase filter.')
    #Least Asymmetric filters
    elif wtfname.lower() in ('la8', 'la10', 'la12', 'la14', 'la18', 'la16', 'la20'):
        nu = advance_least_asymetric_filter(L)
    #Best Localized filters
    elif wtfname.lower() in ('bl14', 'bl18', 'bl20'):
        nu = advance_best_localized_filter(L)
    #Coiflet filters
    elif wtfname.lower() in ('c6', 'c12', 'c18', 'c24', 'c30'):
        nu = advance_coiflet_filter(L)
    #otherwise
    else:
        pass
    
    return nu

def advance_wavelet_filter(wtfname, j):
    """
    advance_wavelet_filter -- Calculate the advance of the wavelet filter at jth level for a given wavelet.
    
    NAME
       advance_wavelet_filter -- Calculate the advance of the wavelet filter at jth level for a given wavelet.

    INPUTS
       wtfname      = string containing name of ante-supported wavelet filter.
       j            = jth level (index) of scale or a range of j levels of scales
                      (integer or Jx1 vector of integers).
    
    OUTPUTS
       nuHj         = advance of wavelet filter at jth level
                      (integer or vector of integers).
    
    ALGORITHM
       nuHj = - (2^(j-1) * (L-1) + nu)   #see equation 114b of P&W
    
    SEE ALSO
       advance_time_series_filter, dwt_filter
    """

    # Get a valid wavelet transform filter coefficients struct.
    wtf_s = wtfilter(wtfname)
    L     = wtf_s.L

    nu = advance_time_series_filter(wtfname)

    nuHj = -(2**(j-1) * (L-1) + nu)

    return nuHj

def advance_scaling_filter(wtfname, j):
    """
     advance_scaling_filter -- Calculate the value to advance scaling filter at jth level for a given wavelet.
    
     NAME
       advance_scaling_filter -- Calculate the value to advance scaling filter at jth level for a given wavelet.
    
     SYNOPSIS
       nuGj = advance_scaling_filter(wtfname, level)
    
     INPUTS
       wtfname      = string containing name of ante-supported wavelet filter.
       j            = jth level (index) of scale or a range of j levels of scales
                      (integer or vector of integers).
    
     OUTPUTS
       nuGj         = advance of scaling filter at specified levels.
    
     SIDE EFFECTS
       wavelet is a ante-supported scaling filter; otherwise error.
    
     ALGORITHM
       nuGj = (2^j - 1) * nu  #see equation 114a of P&W
    
     SEE ALSO
       advance_time_series_filter, dwt_filter
    """

    if wtfname.lower()=='d2':
        nuGj = advance_wavelet_filter('d2', j)
    else:
        nu = advance_time_series_filter(wtfname)
        nuGj = (2**j - 1) * nu

    return nuGj



def equivalent_filter_width(L, j):
    """
     equivalent_filter_width -- Calculate width of the equivalent wavelet or scaling filter.
    
     NAME
       equivalent_filter_width -- Calculate width of the equivalent wavelet or scaling filter.
    
     INPUTS
       * L          --  width of wavelet or scaling filter (unit scale).
       * j          --  jth level (index) of scale or a range of j levels of scales. 
                        (integer or vector of integers).
    
     OUTPUTS
       * Lj         -- equivalent width of wavelet or scaling filter for specified
                       levels (integer or J vector of integers).
    
     SIDEEFFECTS
       1.  L > 0, otherwise error.
       2.  j > 0, otherwise error.
    
     DESCRIPTION
       Given the length of a wavelet or scaling filter, the function calculates the
       width of the equivalent filter a level or range of levels j for the specified
       base filter width L.
    
     ALGORITHM
        Lj = (2^j - 1) * (L - 1) + 1   #equation 96a
    """
    #Check input arguments and set defaults.
    if L<1:
        raise ValueError('L must be positive')
    if j<1:
        raise ValueError('j must be positive')

    Lj = (2.0**j - 1) * (L - 1) + 1

    return Lj



def swt_choose_nlevels(choice, wtfname, N):
    """
    swt_choose_nlevels -- Select J0 based on choice, wavelet filter and data series length.
    
    NAME
      swt_choose_nlevels -- Select J0 based on choice, wavelet filter and data series length.
    
    USAGE
       J0 = swt_choose_nlevels(choice, wtfname, N)
    
    INPUTS
    choice      -- choice for method for calculating J0 (string)
                   Valid Values:
                   'conservative'
                   'max', 'maximum'
                   'supermax', 'supermaximum'
    wtfname     -- wavelet transform filter name (string)
                   Valid Values:  see swt_filter
    N           -- number of observations.
    
    OUTPUT
    J0          -- number of levels (J0) based selection criteria.

    EXAMPLE
    J0 = swt_choose_nlevels('convservative', 'la8', N)
    
    ERRORS  
    ante:SWT:InvalidNumLevels    =  Invalid type/value specified for nlevels.
    
    ALGORITHM
    for 'conservative':              J0  < log2( N / (L-1) + 1)
    for 'max', 'maximum':            J0 =< log2(N)
    for 'supermax', 'supermaximum':  J0 =< log2(1.5 * N)        #see page 200 of P&W.
    
    SEE ALSO
    swt_filter
    """
      
    available_choices = ('conservative', 'max', 'supermax')

    #Check for valid wtfname and get wavelet filter coefficients

    wtf = wtfilter(wtfname)

    L = wtf.L

    if choice=='conservative':
        J0 = np.floor(np.log2( (np.float(N) / (L - 1)) - 1))
    elif choice in ('max', 'maximum'):
        J0 = np.floor(np.log2(N))
    elif ('supermax', 'supermaximum'):
        J0 = np.floor(np.log2(1.5 * N))
    else:
        raise ValueError('ante:invalidNLevelsValue: available choices are {}'.format(available_choices))
    return J0