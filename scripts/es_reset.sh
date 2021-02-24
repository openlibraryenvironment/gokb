
export INDEXNAME="${1:-gokb}"
export SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
export MAPPING_FILE=$SCRIPT_DIR"/../server/gokbg3/src/elasticsearch/es_mapping.json"
export SETTINGS_FILE=$SCRIPT_DIR"/../server/gokbg3/src/elasticsearch/es_settings.json"

printf 'Reset ES indexes for index name \"$INDEXNAME\"\n'

printf "Drop old index\n"
curl -XDELETE "http://localhost:9200/$INDEXNAME"

printf "\nCreate index\n"
curl -X PUT "localhost:9200/$INDEXNAME" -H 'Content-Type: application/json' -d@$SETTINGS_FILE

printf "\nCreate component mapping\n"
curl -X PUT "localhost:9200/$INDEXNAME/component/_mapping" -H "Content-Type: application/json" -d@$MAPPING_FILE
printf "\n\n"
