
export INDEXNAME="${1:-gokb}"
export MAPPING_FILE="src/elasticsearch/es_mapping.json"

echo Reset ES indexes for index name \"$INDEXNAME\"

echo Drop old index
curl -XDELETE "http://localhost:9200/$INDEXNAME"

echo \\nCreate index
curl -X PUT "localhost:9200/$INDEXNAME" -H 'Content-Type: application/json' -d '{
  "settings": {
      "number_of_shards": 1,
      "analysis": {
          "filter": {
              "autocomplete_filter": {
                  "type":     "edge_ngram",
                  "min_gram": 1,
                  "max_gram": 20
              }
          },
          "analyzer": {
              "autocomplete": {
                  "type":      "custom",
                  "tokenizer": "standard",
                  "filter": [
                      "lowercase",
                      "autocomplete_filter"
                  ]
              }
          }
      }
  }
}'

echo \\nCreate component mapping
curl -X PUT "localhost:9200/$INDEXNAME/component/_mapping" -H "Content-Type: application/json" -d@$MAPPING_FILE
echo \\n
