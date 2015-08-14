#!/usr/bin/python

from ante.wavelet import swt
import numpy as np


x=np.zeros(32)
#x[40:46] = 0.5*np.cos(3*np.pi*np.arange(40,46)/16. + 0.08)
#x[0:16] = 1
x[0:32] = [4.139, 4.139, 4.151, 4.163, 4.176, 4.189, 4.202, 4.215, 4.227, 4.239, 4.253, 4.266, 4.277, 4.289, 4.301, 4.311, 4.321, 4.327, 4.338, 4.349, 4.359, 4.369, 4.378, 4.385, 4.394, 4.403, 4.412, 4.423, 4.424, 4.434, 4.448, 4.457]
print x
wa,sc = swt.swt(x,wtf='d2', nlevels=5)
print [x[-1] for x in wa]
#print '-----------------------------------------------'
#print sc
de,sm = swt.iswt_mra(wa, sc)
#print de
#print sm
war,scr = swt.swt_cir_shift(wa,sc)
varw,vars = swt.swt_rot_cum_wav_svar(wa,sc)