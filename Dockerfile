FROM ubuntu:14.04
RUN apt-get update
RUN apt-get install -y --no-install-recommends default-jre
RUN apt-get install -y --no-install-recommends libgomp1

ADD soundcloud_pagerank/soundcloud-user-info-75eaf8a774e3.p12 /home/ante/pagerank/soundcloud_pagerank/

ADD graphchi /home/ante/pagerank/graphchi/

ADD soundcloud_pagerank/run_pagerank.sh /home/ante/pagerank/soundcloud_pagerank/
ADD soundcloud_pagerank/pagerank.jar /home/ante/pagerank/soundcloud_pagerank/
