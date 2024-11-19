# bin/bash
ps -ax | grep '[m]onitoring.*jar' | awk '{print $1}' | xargs kill
