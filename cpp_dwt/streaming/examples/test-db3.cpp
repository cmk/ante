#include <iostream>
#include <cmath>
#include <vector>
#include <string>
#include <algorithm>
#include "lwave.h"


using namespace std;

int main()
{

	string name="db3";
	int J=2;
	double lp1_a[] = {0.9501,0.2311,0.6068,0.4860,0.8913,0.7621,0.4565,0.0185,0.8214,
		0.4447,0.6154,0.7919,0.9218,0.7382,0.1763,0.4057};
	/*double lp1_a[] = {1.000,1.000,1.000,1.000,1.000,1.000,1.000,1.000,1.000,
	 * 1.000,1.000,1.000,1.000,1.000,1.000,1.000};*/
	vector<double> sig;
	    sig.assign(lp1_a,lp1_a + sizeof(lp1_a)/sizeof(double));

	    liftscheme blift(name);
	    string c="d";
	    vector<double> addl;
	    addl.push_back(0.500);
	    addl.push_back(-0.125);

	    int mp=0;
	    blift.addLift(c,addl,mp);
	    blift.disp();

	    lwt<double> dlift(sig,blift,J);
	    vector<double> a,d;
	    vector<int> lengths;
	    dlift.getCoeff(a,d);
	    dlift.getDetailVec(lengths);
	    cout << " Approximation : " << endl;
	    for (int i=0; i < a.size();i++) {
		    cout << a[i] << " ";
	    }
	    cout << endl;

	    cout << " Detail : " << endl;
	    for (int i=0; i < d.size();i++) {
		    cout << d[i] << " ";
	    }
	    cout << endl;

	    cout << " Lengths Of Detail Vectors : " << endl;
	    for (int i=0; i < lengths.size();i++) {
		    cout << lengths[i] << " ";
	    }
	    cout << endl;


	    ilwt<double> idlift(dlift,blift);
	    vector<double> oup;
	    idlift.getSignal(oup);

	    cout << " Reconstructed : " << endl;
	    for (int i=0; i < oup.size();i++) {
		    cout << oup[i] << " ";
	    }
	    cout << endl;


	    return 0;
}
