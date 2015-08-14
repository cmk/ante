//! \brief general purpose utilities
//! \author cem3394
//! \version 0.1
//! \date    July 27 17:15:12 PST 2014


#ifndef ALG_H
#define ALG_H
#include <iostream>
#include <cmath>
#include <vector>
#include <string>
#include <algorithm>

using namespace std;

template <typename T>
void inline split(vector<T> &sig, vector<T> &even, vector<T> &odd)  {

	for (int i=0; i < (int) sig.size(); i++) {
		if (i%2 == 0) {
			even.push_back(sig[i]);
		} else {
			odd.push_back(sig[i]);
		}
	}
	
	
}

template <typename T>
void inline vecmult(vector<T> &sig, double x) {
	
	for (int i=0; i < (int) sig.size(); i++ ) {
		sig[i]= (T) (x*sig[i]);
	}
	
}

template <typename T>
void inline merge(vector<T> &sig, vector<T> &even, vector<T> &odd) {
	
	int N = even.size() + odd.size();
	
	for (int i=0; i < N; i++) {
		if (i%2 == 0) {
			sig.push_back(even[i/2]);
		} else {
			sig.push_back(odd[i/2]);
		}
	}
}

template <typename T> 
void inline transpose(vector<T> &sig, int rows, int cols,vector<T> &col) {
	
	for (int i=0; i < cols; i++) {
		for (int j=0; j < rows; j++) {
			col.push_back(sig[i+j*cols]);
		}
	}
	
}


#endif