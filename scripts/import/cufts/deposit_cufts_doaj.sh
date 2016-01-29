#!/bin/bash

curl -v --user admin:admin -X POST \
  --form content=@../testdata/cufts/doaj \
  --form source="CUFTS" \
  --form fmt="cufts" \
  --form pkg="DOAJ" \
  --form platformUrl="http://cufts.org/doaj" \
  --form format="JSON" \
  http://localhost:8080/gokb/packages/deposit
