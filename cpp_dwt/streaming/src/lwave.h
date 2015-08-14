//! \class lwt
//! \brief class implementing wavelet transform
//! \author cem3394
//! \version 0.1
//! \date    July 27 17:15:12 PST 2014

#ifndef LWAVE_H
#define LWAVE_H
#include <iostream>
#include <cmath>
#include <vector>
#include <string>
#include <algorithm>

#include "lift.h"
#include "alg.h"

using namespace std;

template <class T>
struct IsInt 
{
	static const bool value = false;
};

template <>
struct IsInt<int> 
{
	static const bool value = true;
};

template <>
struct IsInt<short> 
{
	static const bool value = true;
};

template <>
struct IsInt<long> 
{
	static const bool value = true;
};

template <typename T>
class lwt {
	vector<T> cA,cD;
	vector<int> cD_length;
	int level;

public:
    lwt(vector<T> &signal, liftscheme &lft){
	level=1;	
	vector<double> coeff;
	vector<int> lenv;
	string lat;
	double K;
	lft.getScheme(coeff,lenv,lat,K);
	
	// Number Of Liftin Stages N
	int N = lat.size();
	vector<T> sl,dl;
	split(signal,sl,dl);
	int cume_coeff=0;
	
	for (int i=0; i < N ; i++) {
		char lft_type = lat.at(i);
		vector<double> filt;
		int len_filt = lenv[2*i];
		int max_pow = lenv[2*i+1];
		
		for (int j=0; j < len_filt; j++) {
			filt.push_back(coeff[cume_coeff+j]);
		}
		cume_coeff=cume_coeff+len_filt;
		
		if (lft_type == 'd') {
			
			for (int len_dl = 0; len_dl < (int) dl.size();len_dl++) {
				double temp = 0.0;
				for (int lf=0; lf < len_filt; lf++) {
					if ((len_dl+max_pow-lf) >= 0 && (len_dl+max_pow-lf) < (int) sl.size()) {
						temp=temp+filt[lf]*sl[len_dl+max_pow-lf];
					
					}
				}
				dl[len_dl]=dl[len_dl]-(T) temp;
				
			}
		
			
		} else if (lft_type == 'p') {
			
			for (int len_sl = 0; len_sl < (int) dl.size();len_sl++) {
				double temp = 0.0;
				for (int lf=0; lf < len_filt; lf++) {
					if ((len_sl+max_pow-lf) >= 0 && (len_sl+max_pow-lf) < (int) dl.size()) {
						temp=temp+filt[lf]*dl[len_sl+max_pow-lf];
					
					}
				}
				sl[len_sl]=sl[len_sl]-(T) temp;
				
			}
			
		}
		
	}
	double K1 = 1.0/K;
	if ( !IsInt<T>::value) {
		vecmult(sl,K1);
		vecmult(dl,K);
	}

	cA=sl;
	cD=dl;
	cD_length.clear();
	cD_length.push_back((int) cD.size());
		
	}
	
	lwt(vector<T> &signal, string &name){
	level=1;	
	liftscheme lft(name);
	lwt<T> wavelift(signal,lft);
	vector<T> sx,dx;
	wavelift.getCoeff(sx,dx);
	cA=sx;
	cD=dx;
	cD_length.clear();
	cD_length.push_back((int) cD.size());
	}
	
	lwt(vector<T> &signal, liftscheme &lft, int &J) {
	/*	int Max_Iter;
		Max_Iter = (int) ceil(log( double(signal.size()))/log (2.0)) - 1;

		if ( Max_Iter < J) {
			J = Max_Iter;

		}*/
		
		vector<T> temp=signal;
		vector<T> det,temp2;
		vector<int> len_det;
		
		for (int iter=0; iter < J; iter++) {
			lwt jlevel(temp,lft);
			jlevel.getCoeff(temp,temp2);
			int len_d = temp2.size();
			det.insert(det.begin(),temp2.begin(),temp2.end());
			len_det.insert(len_det.begin(),len_d);
		}
		cA=temp;
		cD=det;
		cD_length=len_det;
		level = J;
		
	}
	
	lwt(vector<T> &signal, string &name, int &J){
	liftscheme lft(name);
	lwt<T> wavelift(signal,lft,J);
	vector<T> sx,dx;
	wavelift.getCoeff(sx,dx);
	cA=sx;
	cD=dx;
	vector<int> cdlen;
	wavelift.getDetailVec(cdlen);
	cD_length=cdlen;
	level=J;
	}
	
void getCoeff(vector<T> &appx, vector<T> &det) {
	appx = cA;
	det = cD;
}

void getDetailVec(vector<int> &detvec) {
	detvec=cD_length;
}

int getLevels() {
	return level;
}
	
	
	virtual ~lwt()
	{
	}

};

template <typename T>
class ilwt {
	vector<T> signal;

public:
	ilwt(vector<T> &sl, vector<T> &dl, liftscheme &lft){
	vector<double> coeff;
	vector<int> lenv;
	string lat;
	double K;
	lft.getScheme(coeff,lenv,lat,K);
	//vector<T> sl,dl;
	//sl=cA;
	//dl=cD;
	double K1 = 1.0/K;
	if ( !IsInt<T>::value) {
		vecmult(sl,K);
		vecmult(dl,K1);
	}
	
	int N = lat.size();
	
	int cume_coeff=coeff.size();
	
	for (int i=N-1; i >= 0 ; i--) {
		char lft_type = lat.at(i);
		vector<double> filt;
		int len_filt = lenv[2*i];
		int max_pow = lenv[2*i+1];
		
		cume_coeff=cume_coeff-len_filt;
		
		for (int j=0; j < len_filt; j++) {
			filt.push_back(coeff[cume_coeff+j]);
		}
		
		
		if (lft_type == 'd') {
			
			for (int len_dl = 0; len_dl < (int) dl.size();len_dl++) {
				double temp =  0.0;
				for (int lf=0; lf < len_filt; lf++) {
					if ((len_dl+max_pow-lf) >= 0 && (len_dl+max_pow-lf) < (int) sl.size()) {
						temp=temp+filt[lf]*sl[len_dl+max_pow-lf];
					
					}
				}
				dl[len_dl]=dl[len_dl]+(T) temp;
				
			}
			
		} else if (lft_type == 'p') {
			
			for (int len_sl = 0; len_sl < (int) dl.size();len_sl++) {
				double temp =  0.0;
				for (int lf=0; lf < len_filt; lf++) {
					if ((len_sl+max_pow-lf) >= 0 && (len_sl+max_pow-lf) < (int) dl.size()) {
						temp=temp+filt[lf]*dl[len_sl+max_pow-lf];
					
					}
				}
				sl[len_sl]=sl[len_sl]+(T) temp;
				
			}
			
		}
		
	}
	vector<T> idwt_oup;
	merge(idwt_oup,sl,dl);
	
	signal=idwt_oup;
	
	}
	
	ilwt(vector<T> &sl,vector<T> &dl, string &name){
	liftscheme lft(name);
	ilwt<T> wavelift(sl,dl,lft);
	vector<T> sigx;
	wavelift.getSignal(sigx);
	signal=sigx;
	}
	
	ilwt(lwt<T> &wt,liftscheme &lft) {
	int J=wt.getLevels();
	vector<T> sl,dl;
	wt.getCoeff(sl,dl);
	vector<int> detv;
	
	wt.getDetailVec(detv);
	int total=0;
	
	for (int i=0; i < J; i++) {
		vector<T> temp,temp2;
		for (int j=0; j < (int) detv[i]; j++) {
			temp.push_back(dl[total+j]);
		}
		total=total+(int) detv[i];
		ilwt<T> iwt(sl,temp,lft);
		iwt.getSignal(temp2);
		sl=temp2;
		
	}
	signal=sl;
	}
	
	ilwt(lwt<T> &wt, string &name){
	liftscheme lft(name);
	ilwt<T> wavelift(wt,lft);
	vector<T> sigx;
	wavelift.getSignal(sigx);
	signal=sigx;
	}
	
void getSignal(vector<T> &sig) {
	sig=signal;
}
	
	virtual ~ilwt()
	{
	}
};


#endif // LWAVE_H
