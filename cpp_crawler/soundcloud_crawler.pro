TEMPLATE = app
CONFIG += console
CONFIG -= app_bundle
CONFIG -= qt

SOURCES += \
    src/main.cpp \
    src/Debug/assert.cpp \
    src/Debug/assert_settings.cpp \
    src/DB/postgres_wrapper.cpp \
    src/Graph/pagerank.cpp \
    src/Internet/curl_fetcher.cpp

INCLUDEPATH += src
DEPENDPATH += src
INCLUDEPATH += libs
DEPENDPATH += libs
# graphchi needs it's files to be directly includable
INCLUDEPATH += libs/graphchi
DEPENDPATH += libs/graphchi

QMAKE_CXXFLAGS += -std=c++11
QMAKE_CXXFLAGS += -g
QMAKE_CXXFLAGS += -Wdeprecated
QMAKE_CXXFLAGS += -Werror
QMAKE_CXXFLAGS += -fopenmp

LIBS += -lpq
LIBS += -lcurl
LIBS += -lgtest
LIBS += -lpthread
LIBS += -lz
LIBS += -fopenmp

HEADERS += \
    src/Debug/assert.hpp \
    src/Debug/assert_settings.hpp \
    src/Util/function.hpp \
    src/DB/postgres_wrapper.hpp \
    src/Graph/pagerank.hpp \
    src/Internet/curl_fetcher.hpp \
    src/Util/scope_exit.hpp
