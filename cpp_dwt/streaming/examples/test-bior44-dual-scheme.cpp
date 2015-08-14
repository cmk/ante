#include <iostream>
#include <cmath>
#include <vector>
#include <string>
#include <algorithm>
#include "lwave.h"


using namespace std;

int main()
{

string name="bior4.4";

liftscheme blift(name);

// Adding a Dual Lifting Stage

string c="d"; //d corrsponds to dual while p corresponds to primal
vector<double> addl;
addl.push_back(0.500);
addl.push_back(-0.125);

int mp=0;
blift.addLift(c,addl,mp);

//Getting Information

cout << " Number Of Lifting Stages : " << blift.nlifts() << endl;
cout << " K Constant : " << blift.K() << endl;
cout << " Name : " << blift.getName() << endl;

vector<double> coeff;
vector<int> lenvec;
string lattice;
double Kc;

// Getting Full Scheme

blift.getScheme(coeff,lenvec,lattice,Kc);

cout << "Lifting Structure : " << lattice << endl;
cout << " K Constant (Same As Above) : " << Kc << endl;

cout << "A Single Vector Containing All Lifting Coefficients " << endl;

for (int i=0; i <  (int)coeff.size(); i++) {
        cout << coeff[i] << " " ;
}
cout << endl;

cout << "Length Vector that corresponds to lengths of Lifting Coefficients and Maximum Power at each Stage" << endl;

for (int i=0; i <(int) lenvec.size(); i++) {
        cout << lenvec[i] << " " ;
}
cout << endl;
// disp() Function Displays Coefficients and Laurent Polynomials At Each Stage 
blift.disp();


return 0;
}
