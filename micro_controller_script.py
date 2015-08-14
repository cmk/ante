import subprocess
import datetime
import time
import sys

if __name__ == '__main__':
	shutdown_old = 'gcloud compute instances delete soundcloud-pagerank --zone us-central1-f -q'
	start_new = 'gcloud compute instances create soundcloud-pagerank --image container-vm-v20140710 --image-project google-containers --zone us-central1-f --machine-type n1-standard-1 --scopes userinfo-email datastore compute-rw --metadata-from-file startup-script=compute_instance_startup_script.sh'
	twenty_one_hours = 21 * 60 * 60
	while(True):
		before = datetime.datetime.now()
		print(shutdown_old)
		subprocess.call(shutdown_old, shell=True)
		print(start_new)
		subprocess.call(start_new, shell=True)
		time_spent = (datetime.datetime.now() - before).total_seconds()
		to_sleep = twenty_one_hours - time_spent
		print('starting pagerank took %s seconds. will now sleep for %s seconds before starting again' % (time_spent, to_sleep))
		sys.stdout.flush()
		sys.stderr.flush()
		time.sleep(to_sleep)
