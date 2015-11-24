#!/bin/bash

curl -v --user admin:admin -X POST \
  --form file=@../testdata/cufts/doaj \
  --form source="CUFTS" \
  --form format="cufts" \
  --form package="DOAJ" \
  --form platformUrl="http://cufts.org/doaj" \
  http://localhost:8080/gokb/packages/deposit
