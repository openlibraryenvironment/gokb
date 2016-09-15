#!/bin/bash

export GOKB_HOST="http://localhost:8080"

curl -vvv --user admin:admin -X POST --form users=@./users.tsv $GOKB_HOST/gokb/api/bulkLoadUsers

