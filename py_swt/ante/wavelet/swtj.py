import numpy as np

def swtj(Vin, j, ht, gt):

    N = len(Vin)
    L = len(ht)
    Wout = np.zeros(N, dtype=np.float64)
    Vout = np.zeros(N, dtype=np.float64)

    if L!=len(gt): raise ValueError('filters ht and gt must have the same length')
    
    for t in range(N):
        k = t
        Wout[t] = ht[0] * Vin[k]
        Vout[t] = gt[0] * Vin[k]
        for n in range(1,L):
            k -= 2**(j - 1)
            if (k < 0):
                tmp = np.abs(k)
            else:
            	tmp = k
            Wout[t] += ht[n] * Vin[tmp]
            Vout[t] += gt[n] * Vin[tmp]
    return Wout, Vout

    
def iswtj(Win, Vin, j, ht, gt):

    N = len(Vin)
    L = len(ht)
    Vout = np.zeros(N, dtype=np.float64)

    if L!=len(gt): raise ValueError('filters ht and gt must have the same length')
    
    for t in range(N):
        k = t
        Vout[t] = (ht[0] * Win[k]) + (gt[0] * Vin[k])
        for n in range(1,L):
            k += 2**(j - 1)
            if (k >= N):
                k = N - (k-N) -1
            Vout[t] += (ht[n] * Win[k]) + (gt[n] * Vin[k])
    return Vout