#CXX = icpc
CXXFLAGS = -O3 -I./ 
CXXFLAGS = -g -I./ 

OBJS = MyBase.o WaveFiltBase.o  WaveFiltBior.o  WaveFiltDaub.o  WaveFiltCoif.o \
	WaveFiltHaar.o MatWaveBase.o  MatWaveDwt.o MatWaveWavedec.o  \
	

LDLIBS = -lm -lrt
LDLIBS = -lm 

DRIVERS = driver0 driver1

all:: $(DRIVERS)

driver0:: $(OBJS) driver0.o driver_support.o
	$(CXX) -o $@ $(LDFLAGS) $(OBJS) driver0.o  driver_support.o $(LDLIBS) 

driver1:: $(OBJS) driver1.o driver_support.o
	$(CXX) -o $@ $(LDFLAGS) $(OBJS) driver1.o  driver_support.o $(LDLIBS) 

tsig0:: $(OBJS) tsig0.o
	$(CXX) -o $@ $(LDFLAGS) $(OBJS) tsig0.o  $(LDLIBS) 

tsig1:: $(OBJS) tsig1.o
	$(CXX) -o $@ $(LDFLAGS) $(OBJS) tsig1.o  $(LDLIBS) 

decompose_driver:: $(OBJS) decompose_driver.o 
	$(CXX) -o $@ $(LDFLAGS) $(OBJS) decompose_driver.o  $(LDLIBS) 

reconstruct_driver:: $(OBJS) reconstruct_driver.o 
	$(CXX) -o $@ $(LDFLAGS) $(OBJS) reconstruct_driver.o  $(LDLIBS) 

compress_driver:: $(OBJS) compress_driver.o 
	$(CXX) -o $@ $(LDFLAGS) $(OBJS) compress_driver.o  $(LDLIBS) 


clean:
	/bin/rm *.o  $(DRIVERS)
