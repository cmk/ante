#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cassert>
#include <cerrno>
#include <cctype>
#include <string>
#include <vector>

#include "MyBase.h"


#include <iostream>

using namespace VetsUtil;

char 	*MyBase::ErrMsg = NULL;
int	MyBase::ErrMsgSize = 0;
int	MyBase::ErrCode = 0;
FILE	*MyBase::ErrMsgFilePtr = NULL;
void (*MyBase::ErrMsgCB) (const char *msg, int err_code) = NULL;

char 	*MyBase::DiagMsg = NULL;
int	MyBase::DiagMsgSize = 0;
#ifdef	DEBUG
FILE	*MyBase::DiagMsgFilePtr = stderr;
#else
FILE	*MyBase::DiagMsgFilePtr = NULL;
#endif
void (*MyBase::DiagMsgCB) (const char *msg) = NULL;

MyBase::MyBase() {
	SetClassName("MyBase");
}


void	MyBase::_SetErrMsg(
	char **msgbuf,
	int *msgbufsz,
	const char *format, 
	va_list args
) {
	int	done = 0;
	const int alloc_size = 256;
	int rc;
//#ifdef WIN32
	//CHAR szBuf[80]; 
	
	
    //DWORD dw = GetLastError(); 
 
	//sprintf(szBuf, "Reporting error message: GetLastError returned %u\n", dw); 
    //MessageBox(NULL, szBuf, "Error", MB_OK); 
//#endif
	if (!*msgbuf) {
		*msgbuf = new char[alloc_size];
		assert(*msgbuf != NULL);
		*msgbufsz = alloc_size;
	}

	string formatstr(format);
	size_t loc;
	while ((loc = formatstr.find("%M", 0)) != string::npos) {
		formatstr.replace(loc, 2, strerror(errno), strlen(strerror(errno)));
	}

	format = formatstr.c_str();


	// Loop until we've successfully buffered the error message, growing
	// the message buffer as needed
	//
	while (! done) {
#ifdef WIN32
		rc = _vsnprintf(*msgbuf, *msgbufsz, format, args);

#else
		rc = vsnprintf(*msgbuf, *msgbufsz, format, args);
#endif

		if (rc < (*msgbufsz-1)) {
			done = 1;
		} else {
			if (*msgbuf) delete [] *msgbuf;
			*msgbuf = new char[*msgbufsz + alloc_size];
			assert(*msgbuf != NULL);
			*msgbufsz += alloc_size;
		}
	}

}

void	MyBase::SetErrMsg(
	const char *format, 
	...
) {
	va_list args;

	ErrCode = 1;

	va_start(args, format);
	_SetErrMsg(&ErrMsg, &ErrMsgSize, format, args);
	va_end(args);

	if (ErrMsgCB) (*ErrMsgCB) (ErrMsg, ErrCode);

	if (ErrMsgFilePtr) {
		(void) fprintf(ErrMsgFilePtr, "%s\n", ErrMsg);
	}
}

void	MyBase::SetErrMsg(
	int errcode,
	const char *format, 
	...
) {
	va_list args;

	ErrCode = errcode;

	va_start(args, format);
	_SetErrMsg(&ErrMsg, &ErrMsgSize, format, args);
	va_end(args);

	if (ErrMsgCB) (*ErrMsgCB) (ErrMsg, ErrCode);

	if (ErrMsgFilePtr) {
		(void) fprintf(ErrMsgFilePtr, "%s\n", ErrMsg);
	}
}

void	MyBase::SetDiagMsg(
	const char *format, 
	...
) {
	va_list args;

	va_start(args, format);
	_SetErrMsg(&DiagMsg, &DiagMsgSize, format, args);
	va_end(args);

	if (DiagMsgCB) (*DiagMsgCB) (DiagMsg);

	if (DiagMsgFilePtr) {
		(void) fprintf(DiagMsgFilePtr, "%s\n", DiagMsg);
	}
}

int	VetsUtil::IsPowerOfTwo(
	unsigned int x
) {
	if( !x ) return 1;
	while( !(x & 1) ) x >>= 1;
	return (x == 1);
} 

//Find integer log, base 2, of a 32-bit positive integer:
int VetsUtil::ILog2(int n){
	int i;
	for (i = 0; i<31;i++){
		if (n <= (1<<i)) break;
	}
	return i;
}
int VetsUtil::StrCmpNoCase(const string &s, const string &t) {
    string::const_iterator sptr = s.begin();
    string::const_iterator tptr = t.begin();

    while (sptr != s.end() && tptr != t.end()) {
        if (toupper(*sptr) != toupper(*tptr)) {
            return(toupper(*sptr) < toupper(*tptr) ? -1 : 1);
        }
        *sptr++; *tptr++;
    }

    return((s.size() == t.size()) ? 0 : ((s.size()<t.size()) ? -1 : 1));
}


void	VetsUtil::StrRmWhiteSpace(string &s) {
	string::size_type	i;
	
	if (s.length() < 1) return;

	i = 0;
	while (isspace(s[i])) i++;
	
	if (i>0) {
		s.replace(0, i, "", 0);
	}

	if (s.length() < 1) return;
	i = s.length() - 1;

	while (isspace(s[i])) i--;

	if (i<(s.length() - 1)) {
		s.replace(i+1, s.length()-i+1, "", 0);
	}
}

void VetsUtil::StrToWordVec(const string &s, vector <string> &v)
{
	string tmp = s;
	v.clear();
	while (! tmp.empty()) {
		while (! tmp.empty() && isspace(tmp[0])) tmp.erase(0, 1);

		int index = 0;
		while (index < tmp.length() && ! isspace(tmp[index])) index++;

		if (index) {
			string word = tmp.substr(0, index);
			v.push_back(word);
			tmp.erase(0, index);
		}
	}
}

unsigned long long VetsUtil::GetBits64(
    unsigned long long targ,
    int pos,
    int n
) {
    return ((targ >> (pos+1-n)) & ~(~0ULL <<n));
}

unsigned long long VetsUtil::SetBits64(
    unsigned long long targ,
    int pos,
    int n,
    unsigned long long src
) {
        targ &= ~(~(~0ULL << n) << (pos+1 - n));
        targ |= (src & ~(~0ULL << n)) << (pos+1-n);
        return(targ);
}
// From Numerical Recipes in C
/*
Minimal" random number generator of Park and Miller with Bays-Durham shuffle and
safeguards. Returns a uniform random deviate between 0.0 and 1.0 (exclusive of the endpoint
values). Call with idum a negative integer to initialize; thereafter, do not alter idum between
successive deviates in a sequence. RNMX should approximate the largest floating value that is
less than 1.
*/
#define IA 16807
#define IM 2147483647
#define AM (1.0/IM)
#define IQ 127773
#define IR 2836
#define NTAB 32
#define NDIV (1+(IM-1)/NTAB)
#define EPS 1.2e-7
#define RNMX (1.0 - EPS)

double VetsUtil::ran1(long *idum) {

        int j;
        long k;
        static long iy=0;
        static long iv[NTAB];
        double temp;

        if (*idum <=0 || !iy) {
                if (-(*idum) < 1) *idum = 1;
                else *idum = -(*idum);
                for (j=NTAB+7; j>=0; j--) {
                        k = (*idum)/IQ;
                        *idum=IA*(*idum-k*IQ)-IR*k;
                        if (*idum < 0) *idum += IM;
                        if (j < NTAB) iv[j] = *idum;
                }
                iy=iv[0];
        }
        k= (*idum)/IQ;
        *idum=IA*(*idum-k*IQ)-IR*k;
        if (*idum < 0) *idum += IM;
        j = iy/NDIV;
        iy=iv[j];
        iv[j] = *idum;
        if ((temp=AM*iy) > RNMX) return RNMX;
        else return temp;
}
