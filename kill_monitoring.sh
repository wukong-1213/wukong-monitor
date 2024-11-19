# bin/bash
ps -ax | grep 'monitoring.*jar' | awk '{print $1}' | xargs kill 2>/dev/null || true
