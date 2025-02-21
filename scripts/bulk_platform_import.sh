#!/bin/bash

# Configuration
URLPROD="https://gokb.org/gokb/integration/crossReferencePlatform/"
URLTEST="https://gokbt.gbv.de/gokb/integration/crossReferencePlatform/"
URLQA="https://gokb-qa.hbz-nrw.de/gokb/integration/crossReferencePlatform/"
URLDEV="https://gokb-dev.hbz-nrw.de/gokb/integration/crossReferencePlatform/"
USERNAME="username"
PASSWORD="password"
CSV_FILE="platforms.tsv"

# Explicit setting of the IFS variable to avoid errors in interpretation by bash
IFS="$(printf '\t')"

# Check if CSV exists
if [[ ! -f "$CSV_FILE" ]]; then
    echo "Error: File $CSV_FILE not found."
    exit 1
fi

# Read CSV without header line by line
tail -n +2 "$CSV_FILE" | while read -r platformUrl name
do
   
    DATA='{"platformUrl": "'$platformUrl'", "primaryUrl": "'$platformUrl'", "name": "'$name'"}'
    
    # Debug
    echo "Send Data:" \
    
    echo ${DATA}

    # Build JSON and send with CURL
    response=$(
    curl -X POST "$URLQA" \
         -H "Content-Type: application/json" \
         -u "$USERNAME":"$PASSWORD" \
         -d "$DATA" 
    )

    echo -e "\nResponse from Server: $response"
done

