
export INDEXNAME="${1:-gokb}"
export MAPPING_FILE="src/elasticsearch/es_mapping.json"
export SETTINGS_FILE="src/elasticsearch/es_settings.json"

echo Reset ES indexes for index name \"$INDEXNAME\"

echo Drop old index
curl -XDELETE "http://localhost:9200/$INDEXNAME"

echo \\nCreate index
curl -X PUT "localhost:9200/$INDEXNAME" -H 'Content-Type: application/json' -d@$SETTINGS_FILE

echo \\nCreate component mapping
curl -X PUT "localhost:9200/$INDEXNAME/component/_mapping" -H "Content-Type: application/json" -d@$MAPPING_FILE
echo \\n
