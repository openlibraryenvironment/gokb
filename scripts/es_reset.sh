
export INDEXNAME="${1:-gokb}"

echo Reset ES indexes for index name $INDEXNAME

echo Drop old index
curl -XDELETE "http://localhost:9200/$INDEXNAME"

echo Create index
curl -X PUT "localhost:9200/$INDEXNAME" -d '{
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

echo Create component mapping
curl -X PUT "localhost:9200/$INDEXNAME/component/_mapping" -d '{
  "component" : {
    "properties" : {
      "name" : {
        "type" : "text",
	"copy_to" : "suggest",
        "fields" : {
          "name" : { "type" : "string", "analyzer" : "snowball" },
          "altname" : { "type" : "string", "analyzer" : "snowball" }
        }
      },
      "componentType" : { 
        "type":"string", 
        "index":"not_analyzed" 
      },
      "status" : {
        "type":"string",
        "index":"not_analyzed"
      },
      "suggest" : {
        "type" : "string",
        "analyzer" : "autocomplete",
        "search_analyzer" : "standard"
      }
    }
  }
}'
