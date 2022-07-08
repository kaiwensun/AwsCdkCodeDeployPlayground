#!/bin/bash

target=`date -d"2022-07-08T05:10:00 UTC" +%s`
now=`date +%s`
diff=`bc <<< "$target - $now"`
echo "now $now"
echo "diff $diff"

if (( diff > 0 )); then
  echo "INFO: sleep $diff"
  sleep $diff
else
  echo "WARN: diff $diff is not positive"
fi
