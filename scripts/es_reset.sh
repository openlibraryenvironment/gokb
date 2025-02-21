
export INDEXNAME="${1:-gokb}"
export MAPPING_FILE="../server/grails-app/conf/elasticsearch/es_mapping.json"
export SETTINGS_FILE="../server/grails-app/conf/elasticsearch/es_settings.json"

printf "Reset ES indexes for index name $INDEXNAME \n"

printf "Drop old index\n"
curl -XDELETE "http://localhost:9200/$INDEXNAME"

printf "\nCreate index\n"
curl -X PUT "localhost:9200/$INDEXNAME" -H 'Content-Type: application/json' -d@$SETTINGS_FILE

printf "\nCreate component mapping\n"
curl -X PUT "localhost:9200/$INDEXNAME/_mapping" -H "Content-Type: application/json" -d@$MAPPING_FILE
printf "\n\n"
