#!/bin/bash

for cufts_file in `ls`
do
  echo Processing $cufts_file
  curl -v --user admin:admin -X POST \
    --form content=@./$cufts_file \
    --form source="CUFTS" \
    --form fmt="cufts" \
    --form pkg="$cufts_file" \
    --form platformUrl="http://lib-code.lib.sfu.ca/projects/CUFTS/" \
    --form format="JSON" \
    http://localhost:8080/gokb/packages/deposit
done
