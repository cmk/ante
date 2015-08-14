import numpy as np

#from google.appengine.ext import ndb
#from google.appengine.api import users

# class User(ndb.Model):
#   """Models an individual guestbook entry with author, content, and date."""
#   author = ndb.UserProperty()
#   date = ndb.DateTimeProperty(auto_now_add=True)
#   Wt_0 = ndb.FloatProperty(indexed=False,repeated=True)
#   Wt_1 = ndb.FloatProperty(indexed=False,repeated=True)
#   Wt_2 = ndb.FloatProperty(indexed=False,repeated=True)
#   Wt_3 = ndb.FloatProperty(indexed=False,repeated=True)
#   Wt_4 = ndb.FloatProperty(indexed=False,repeated=True)
#   Vout = ndb.FloatProperty(indexed=False,repeated=True)
#   @classmethod
#   def query_book(cls, ancestor_key):
#     return cls.query(ancestor=ancestor_key).order(-cls.date)
    
    
class dwtArray(np.ndarray):
    """
    class dwtArray(numpy.ndarray)
    
    NAME
       dwtArray -- generic array containing meta data on the transformation
    
    INPUTS
       same as numpy.ndarray
       
       info -- (optional) pass a dictionary of meta data
       
    DESCRIPTION
       An array class that contains meta data.
       This is the basic building block of the package, since it enables one to 
       easily pass data with information on the transformations applied to
       the various functions
       
    EXAMPLE
       a = np.arange(10)
       b = a.view(dwtArray)
       b.info = {'metadata':'info'}
    
    REFERENCES
       http://docs.scipy.org/doc/numpy/user/basics.subclassing.html
    """
    
    stdinfo = {'Transform': None,
               'Type'     : None,
               'WTF'      : None,
               'N'        : None,
               'NW'       : None,
               'J0'       : None,
               'Aligned'  : None,
               'RetainVJ' : None,
               'BCs'      : None
               }
    
    def __new__(cls, input_array, info=stdinfo):
        # Input array is an already formed ndarray instance
        obj = np.asarray(input_array).view(cls)
        # add the new attribute to the created instance
        obj.info = info
        # return the newly created object:
        return obj

    def __array_finalize__(self, obj):
        # see InfoArray.__array_finalize__ 
        if obj is None: return
        self.info = getattr(obj, 'info', self.stdinfo)

class wtfilter(object):
    """
    wtfilter -- Class defining the wavelet filter, see __init__
    """
    
    def __init__(self, wtfilter, transform='SWT'):
        """
         wtfilter -- Define wavelet transform filter coefficients.
        
         NAME
           wtfilter -- Define wavelet transform filter coefficients.
        
         INPUTS
           * wtfname    -- name of wavelet transform filter (string, case-insenstive).
           * transform  -- name of wavelet transform  (string, case-insenstive).
        
         OUTPUTS
           * wtf        -- wavelet tranform filter class (wtf_s).
        
         DESCRIPTION
           wtfilter returns a class with the wavelet (high-pass) and
           scaling (low-pass) filter coefficients, and associated attributes. 
        
           The wtf_s class has attributes:
           * g         -- scaling (low-pass) filter coefficients (vector).
           * h         -- wavelet (high-pass) filter coefficients (vector).
           * L         -- filter length (= number of coefficients) (integer).
           * Name      -- name of wavelet filter (character string).
           * WTFclass  -- class of wavelet filters (character string).
           * Transform -- name of transform (character string).
        
           The SWT filter coefficients are calculated from the DWT filter 
           coefficients:
        
              ht = h / sqrt(2)
              gt = g / sqrt(2)
        
           The wavelet filter coefficients (h) are calculated from the scaling
           filter coefficients via the QMF function (ante_qmf).
         
         EXAMPLE
            wtf = wtfilter('LA8', 'swt')
            wtf = wtfilter('d2', 'dwt')
        
         SEE ALSO
          wtf_s, wtf_qmf
        """

        if wtfilter.lower()=='la8':
            # Least asymmetric filters 
            self.Name ='la8'
            self.g = np.array(\
                        [-0.0757657147893407,-0.0296355276459541, 0.4976186676324578,\
                          0.8037387518052163,  0.2978577956055422,-0.0992195435769354,\
                         -0.0126039672622612,  0.0322231006040713])
            self.h = ante_qmf(self.g)
            self.L = 8
            self.SELFClass = 'LeastAsymmetric'
            self.Transform = 'DWT'
        elif wtfilter.lower()=='d2':
            # Daubechies 2 tap
            self.Name = 'd2'
            self.g = np.array(\
                              [0.7071067811865475, 0.7071067811865475])
            self.h = ante_qmf(self.g)
            self.L = 2
            self.WTFClass = 'ExtremalPhase'
            self.Transform = 'DWT'
        elif wtfilter.lower()=='d4':
            # Daubechies 4 tap
            self.Name = 'd4'
            self.g    = np.array(\
                                 [0.4829629131445341, 0.8365163037378077, 0.2241438680420134, -0.1294095225512603])
            self.h = ante_qmf(self.g)
            self.L = 4
            self.WTFClass = 'ExtremalPhase'
            self.Transform = 'DWT'
        elif wtfilter.lower()=='d6':
            # Daubechies 6 tap
            self.Name = 'd6'
            self.g    = np.array(\
                                 [ 3.326705529500826159985115891390056300129233992450683597084705e-01, \
                                 8.068915093110925764944936040887134905192973949948236181650920e-01, \
                                 4.598775021184915700951519421476167208081101774314923066433867e-01, \
                                 -1.350110200102545886963899066993744805622198452237811919756862e-01, \
                                 -8.544127388202666169281916918177331153619763898808662976351748e-02, \
                                 3.522629188570953660274066471551002932775838791743161039893406e-02])
            self.h = ante_qmf(self.g)
            self.L = 6
            self.WTFClass = 'ExtremalPhase'
            self.Transform = 'DWT'
        elif wtfilter.lower()=='d8':
            # Daubechies 8 tap
            self.Name = 'd8'
            self.g    = np.array(\
                                 [  2.303778133088965008632911830440708500016152482483092977910968e-01, \
 									7.148465705529156470899219552739926037076084010993081758450110e-01, \
 									6.308807679298589078817163383006152202032229226771951174057473e-01, \
 									-2.798376941685985421141374718007538541198732022449175284003358e-02, \
 									-1.870348117190930840795706727890814195845441743745800912057770e-01, \
 									3.084138183556076362721936253495905017031482172003403341821219e-02, \
 									3.288301166688519973540751354924438866454194113754971259727278e-02, \
 									-1.059740178506903210488320852402722918109996490637641983484974e-02])
            self.h = ante_qmf(self.g)
            self.L = 8
            self.WTFClass = 'ExtremalPhase'
            self.Transform = 'DWT'

        elif wtfilter.lower()=='c6':
            # Coiflet filters
            self.Name ='c6'
            self.g = np.array(\
                              [-0.0156557285289848, -0.0727326213410511, 0.3848648565381134,\
                               0.8525720416423900,  0.3378976709511590,-0.0727322757411889])
            self.h = ante_qmf(self.g)
            self.L = 6
            self.WTFClass = 'Coiflet'
            self.Transform = 'DWT'
        else:
            raise ValueError ('Unrecognised wavelet filter name')

        if transform.lower()=='swt':
            self.Transform = 'SWT'
            self.g = self.g / (2**0.5)
            self.h = self.h / (2**0.5)
            
def ante_qmf(a, inverse=False):
    """
     ante_qmf -- Calculate quadrature mirror filter (QMF).
    
     NAME
       ante_qmf -- Calculate quadrature mirror filter (QMF).
    
     INPUTS
       * a           -- filter coefficients (vector).
       * inverse     -- (optional) flag for calculating inverse QMF (Boolean).
                        Default: inverse = False
    
     OUTPUTS
        b            - QMF coefficients (vector).
    
     DESCRIPTION
        ante_qmf calculates the quadrature mirror filter (QMF) of
        for the specified filter coefficients.  If a is a vector,
        the QMF of the vector is calculated. If a is an array, an
        error is raised
    
       The inverse flag, if set, calculates the inverse QMF.  inverse
       is a Boolean values specified as (1/0, y/n, T/F or true/false).
    
     EXAMPLE
        # h is the QMF of g.
        g = [0.7071067811865475 0.7071067811865475];
        h = ante_qmf(g);
    
        # g is the inverse QMF of h.
        h = [0.7071067811865475 -0.7071067811865475];
        g = ante_qmf(h, 1);
    
     ALGORITHM
          g_l = (-1)^(l+1) * h_L-1-l
          h_l = (-1)^l * g_L-1-l             #page 75 of P&W

     SEE ALSO
       yn
    """

    a = np.array(a)
    
    # we must deep copy a
    b = a.copy()
    
    if len(b.shape)>1:
        raise ValueError('Input array must be 1-dimensional')
    
    b = b[::-1]

    if inverse:
        first = 0
    else:
        first = 1
    
    b[first::2] = -b[first::2]
  
    return b