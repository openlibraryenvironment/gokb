

curl -XDELETE 'http://localhost:9200/gokb'

curl -X PUT "localhost:9200/gokb" -d '{
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

curl -X PUT "localhost:9200/gokb/component/_mapping" -d '{
  "component" : {
    "properties" : {
      "name" : {
        type : "multi_field",
        fields : {
          name : { type : "string", analyzer : "snowball", copy_to : "suggest" },
          altname : { type : "string", analyzer : "snowball" }
        }
      },
      "componentType" : { 
        type:"string", 
        index:"not_analyzed" 
      },
      "status" : {
        type:"string",
        index:"not_analyzed"
      },
      "suggest" : {
        type : "string",
        analyzer : "autocomplete",
        search_analyzer : "standard"
      }
    }
  }
}'
