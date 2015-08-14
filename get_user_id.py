import re,subprocess
clientId = '5529052630cbb1a05dfb3ab18074554e'

"""trashy little script that generates Ids from Unames"""

def bash(cmd,cwd=None):
    print(cmd)
    retVal = subprocess.Popen(cmd, shell=True, \
        stdout=subprocess.PIPE, cwd=cwd).stdout.read().strip('\n').split('\n')
    if retVal==['']:
        return(0)
    else:
        return(retVal)

def getId(uName):
    cmd = 'curl -v \'http://api.soundcloud.com/resolve.json?url=http://soundcloud.com/'+uName+'&client_id='+clientId+'\''
    userId = re.split(r'(users/|\.json)',bash(cmd)[0])[2]
    return userId

handles = ['sangobeats','lidogotsongs','madeaux','take_a_daytrip','hucci','et_aliae','awe','ekalimusic','talaofficial','eric-dingus','iamganz']
ids = []
for h in handles:
  ids.append(getId(h))
print ids

#['1184201', '1314584', '2140230', '53168760', '1143882', '45112414', '25279198', '27053630', '4171610', '11313687', '6458238']