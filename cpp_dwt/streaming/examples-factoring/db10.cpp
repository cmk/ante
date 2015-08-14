#include <iostream>
#include <cmath>
#include <vector>
#include <string>
#include <algorithm>
#include "Laurent.h"
#include "filter.h"
#include "lift.h"

using namespace std;

int main()
{
	/* db2 Wavelet Factoring Demo
	 * Based on Sweldens/Daubechies Factoring Scheme
	 * 
	 * I. Daubechies, W. Sweldens, Factoring wavelets transforms into lifting steps, 
	 * J. Fourier Anal. Appl. 4 (3) (1998) 247-269. 
	 * 
	 * (It is highly recommended that you download a copy of the paper from either the
	 * website of the Journal or at http://cm.bell-labs.com/who/wim/papers/factor/index.html)
	 * 
	 * 1. We find Matrix PZ - Polyphase matrix of Synthesis Filters
	 * 2. We factor Low Pass filter (lpr) to obtain P0 (Factorization into dual/primal step)
	 * 3. Lift P0 by another step to obtain PZ
	 * 
	 * 
	 */
	 
	Laurent<double> pnz,nz;
    vector<double> temp1;
    temp1.push_back(1.0);
    pnz.setPoly(temp1,1);
    temp1.clear();
    temp1.push_back(1.0);
    nz.setPoly(temp1,-1);
	 
	string name="db10";
	Laurent<double> lpd,hpd,lpr,hpr;
	lpoly(name,lpd,hpd,lpr,hpr);
	Laurent<double> leven,lodd;
	EvenOdd(lpr,leven,lodd);
	Laurent<double> heven,hodd;
	EvenOdd(hpr,heven,hodd);
	cout << "Reconstruction Low Pass Filters" << endl;
	lpr.dispPoly();
	cout << "Reconstruction High Pass Filters" << endl;
	hpr.dispPoly();
	LaurentMat<double> PZ;
	//leven.LaurentMult(leven,pnz);
	//hodd.LaurentMult(hodd,nz);
	PZ.setMat(leven,heven,lodd,hodd);
    
	// Q contains the quotient (Lifting Factors)
	// gcd algorithm is used to obtain quotients and the remainders at
	// each step.
	// The factorization is non-unique so the process is not automated.
	// You will have to select appropriate quotient and remainder at each step
	// and repeat the process.
	// The process terminates at a{n}= K and b{n} = 0.
	vector<Laurent<double> > loup,Q;
	Div(leven,lodd,loup);
	cout << "Polyphase Components of Low Pass Filters ( Even and Odd) or (a0 and b0)" << endl;
	leven.dispPoly();
	lodd.dispPoly();
	cout <<endl;
	Laurent<double> quot,rem;

	cout << "All Quotients and Remainders obtained after the first step of gcd algorithm" << endl;
	for (int i=0; i < (int) loup.size() / 2;i++) {
		quot=loup[2*i];
		rem=loup[2*i+1];
		
		quot.dispPoly();
		rem.dispPoly();
		cout << endl;
	}
	leven = lodd;
	lodd = loup[3];
	Q.push_back(loup[2]);
	loup.clear();
	Div(leven,lodd,loup);
	cout << "a1 and b1 components" << endl;
	leven.dispPoly();
	lodd.dispPoly();
	cout <<endl;
	cout << "All Quotients and Remainders obtained after the second step of gcd algorithm" << endl;
	for (int i=0; i < (int) loup.size() / 2;i++) {
		quot=loup[2*i];
		rem=loup[2*i+1];
		
		quot.dispPoly();
		rem.dispPoly();
		cout << endl;
	}
	
	leven = lodd;
	lodd = loup[1];
	Q.push_back(loup[0]);
	loup.clear();
	Div(leven,lodd,loup);
	cout << "a2 and b2 components" << endl;
	leven.dispPoly();
	lodd.dispPoly();
	cout <<endl;
	cout << "All Quotients and Remainders obtained after the  step of gcd algorithm" << endl;
	for (int i=0; i < (int) loup.size() / 2;i++) {
		quot=loup[2*i];
		rem=loup[2*i+1];
		
		quot.dispPoly();
		rem.dispPoly();
		cout << endl;
	}
	
	leven = lodd;
	lodd = loup[5];
	Q.push_back(loup[4]);
		loup.clear();
	Div(leven,lodd,loup);
	cout << "a3 and b3 components" << endl;
	leven.dispPoly();
	lodd.dispPoly();
	cout <<endl;
	cout << "All Quotients and Remainders obtained after the  step of gcd algorithm" << endl;
	for (int i=0; i < (int) loup.size() / 2;i++) {
		quot=loup[2*i];
		rem=loup[2*i+1];
		
		quot.dispPoly();
		rem.dispPoly();
		cout << endl;
	}
	
	leven = lodd;
	lodd = loup[1];
	Q.push_back(loup[0]);
	loup.clear();
	Div(leven,lodd,loup);
	cout << "a4 and b4 components" << endl;
	leven.dispPoly();
	lodd.dispPoly();
	cout <<endl;
	cout << "All Quotients and Remainders obtained after the  step of gcd algorithm" << endl;
	for (int i=0; i < (int) loup.size() / 2;i++) {
		quot=loup[2*i];
		rem=loup[2*i+1];
		
		quot.dispPoly();
		rem.dispPoly();
		cout << endl;
	}
	
	leven = lodd;
	lodd = loup[5];
	Q.push_back(loup[4]);
	loup.clear();
	Div(leven,lodd,loup);
	cout << "a5 and b5 components" << endl;
	leven.dispPoly();
	lodd.dispPoly();
	cout <<endl;
	cout << "All Quotients and Remainders obtained after the  step of gcd algorithm" << endl;
	for (int i=0; i < (int) loup.size() / 2;i++) {
		quot=loup[2*i];
		rem=loup[2*i+1];
		
		quot.dispPoly();
		rem.dispPoly();
		cout << endl;
	}
	
	leven = lodd;
	lodd = loup[5];
	Q.push_back(loup[4]);
	loup.clear();
	Div(leven,lodd,loup);
	cout << "a6 and b6 components" << endl;
	leven.dispPoly();
	lodd.dispPoly();
	cout <<endl;
	cout << "All Quotients and Remainders obtained after the  step of gcd algorithm" << endl;
	for (int i=0; i < (int) loup.size() / 2;i++) {
		quot=loup[2*i];
		rem=loup[2*i+1];
		
		quot.dispPoly();
		rem.dispPoly();
		cout << endl;
	}
	
	leven = lodd;
	lodd = loup[5];
	Q.push_back(loup[4]);
	loup.clear();
	Div(leven,lodd,loup);
	cout << "a7 and b7 components" << endl;
	leven.dispPoly();
	lodd.dispPoly();
	cout <<endl;
	cout << "All Quotients and Remainders obtained after the  step of gcd algorithm" << endl;
	for (int i=0; i < (int) loup.size() / 2;i++) {
		quot=loup[2*i];
		rem=loup[2*i+1];
		
		quot.dispPoly();
		rem.dispPoly();
		cout << endl;
	}
	
	leven = lodd;
	lodd = loup[1];
	Q.push_back(loup[0]);
	loup.clear();
	Div(leven,lodd,loup);
	cout << "a8 and b8 components" << endl;
	leven.dispPoly();
	lodd.dispPoly();
	cout <<endl;
	cout << "All Quotients and Remainders obtained after the  step of gcd algorithm" << endl;
	for (int i=0; i < (int) loup.size() / 2;i++) {
		quot=loup[2*i];
		rem=loup[2*i+1];
		
		quot.dispPoly();
		rem.dispPoly();
		cout << endl;
	}
	
	leven = lodd;
	lodd = loup[5];
	Q.push_back(loup[4]);
	loup.clear();
	Div(leven,lodd,loup);
	cout << "a9 and b9 components" << endl;
	leven.dispPoly();
	lodd.dispPoly();
	cout <<endl;
	cout << "All Quotients and Remainders obtained after the  step of gcd algorithm" << endl;
	for (int i=0; i < (int) loup.size() / 2;i++) {
		quot=loup[2*i];
		rem=loup[2*i+1];
		
		quot.dispPoly();
		rem.dispPoly();
		cout << endl;
	}
	
	leven = lodd;
	lodd = loup[5];
	Q.push_back(loup[4]);
	cout << "an(constant) and bn(zero). A constant quotient is obtained which terminates the algorithm" << endl;
	Div(leven,lodd,loup);
	leven.dispPoly();
	lodd.dispPoly();
	cout <<endl;
	
	// Building P0. SZ and TZ are primal and dual lift Laurent matrices.
	Laurent<double> o,z;
	o.One();
	z.Zero();
	
	LaurentMat<double> Mat1,Mat2,Mat3,Mat4,Mat5,Mat6,Mat7,Mat8,Mat9,Mat10,oup,Kmat,P0,P0Inv,slift;
	Mat1.SZ(Q[0]);
	Mat2.TZ(Q[1]);
	Mat3.SZ(Q[2]);
	Mat4.TZ(Q[3]);
	Mat5.SZ(Q[4]);
	Mat6.TZ(Q[5]);
	Mat7.SZ(Q[6]);
	Mat8.TZ(Q[7]);
	Mat9.SZ(Q[8]);
	Mat10.TZ(Q[9]);
	oup.MatMult(Mat1,Mat2);
	oup.MatMult(oup,Mat3);
	oup.MatMult(oup,Mat4);
	oup.MatMult(oup,Mat5);
	oup.MatMult(oup,Mat6);
	oup.MatMult(oup,Mat7);
	oup.MatMult(oup,Mat8);
	oup.MatMult(oup,Mat9);
	oup.MatMult(oup,Mat10);
	oup.dispMat();
	
	// Set Constant Matrix [K,0,0,1/K]
	// Since the process terminates at leven =K*z^0, we can easily find 
	// K and 1/K to construct scaling matrix
	vector<double> cfd;
	double K,K2;
	leven.getPoly(cfd);
	K=cfd[0];
	K2=(1.0)/K;

	Laurent<double> k4,detm;
	k4.One();
	k4.scale(K2);
	
	Kmat.setMat(leven,z,z,k4);
	
	// If the Matrix is Invertible then you can easily find
	// slift, which is simple inv(P0)*PZ
	oup.MatInv(P0Inv);
	P0Inv.dispMat();
	
	slift.MatMult(P0Inv,PZ);
	slift.dispMat();
	
	LaurentMat<double> Kinv,foup;
	Kmat.MatInv(Kinv);
	// To obtain the final lifitng step, we need to eliminate constant matrix from the
	// Left Hand side
	foup.MatMult(slift,Kinv);
	foup.dispMat();
	
	// As per the paper fin is of the form [1 S(Z);0 1]
	// We find S(Z) using the function getLpoly
	Laurent<double> fin;
	foup.getLpoly(fin,2);
	
	Q.push_back(fin);
	cout << endl << "Lifting Steps" << endl << endl;
	for (int i=0; i < (int) Q.size(); i++)
		Q[i].dispPoly();
	
	LaurentMat<double> Mat11;
	Mat1.SZ(Q[0]);
	Mat2.TZ(Q[1]);
	Mat3.SZ(Q[2]);
	Mat4.TZ(Q[3]);
	Mat5.SZ(Q[4]);
	Mat6.TZ(Q[5]);
	Mat7.SZ(Q[6]);
	Mat8.TZ(Q[7]);
	Mat9.SZ(Q[8]);
	Mat10.TZ(Q[9]);
	Mat11.SZ(Q[10]);
	oup.MatMult(Mat1,Mat2);
	oup.MatMult(oup,Mat3);
	oup.MatMult(oup,Mat4);
	oup.MatMult(oup,Mat5);
	oup.MatMult(oup,Mat6);
	oup.MatMult(oup,Mat7);
	oup.MatMult(oup,Mat8);
	oup.MatMult(oup,Mat9);
	oup.MatMult(oup,Mat10);
	oup.MatMult(oup,Mat11);
	oup.MatMult(oup,Kmat);
	oup.dispMat(); 
	PZ.dispMat();
	Laurent<double> ou1,ou2,ou3,ou4;
	oup.getLpoly(ou1,1);
	oup.getLpoly(ou2,2);
	oup.getLpoly(ou3,3);
	oup.getLpoly(ou4,4);
	Laurent<double> Hout,Gout;
	Hout.merge(ou1,ou3);
	Hout.dispPoly();
	Gout.merge(ou2,ou4);
	Gout.dispPoly();

	return 0;
}