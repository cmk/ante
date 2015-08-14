#! /bin/bash

docker login --username="skarupke" --password="abitunsafe" --email="malteskarupke@web.de"
docker run -i -t skarupke/soundcloud /bin/sh -c "cd /home/ante/pagerank/soundcloud_pagerank; ./run_pagerank.sh"

docker run google/cloud-sdk gcloud compute instances delete soundcloud-pagerank --zone us-central1-f -q
