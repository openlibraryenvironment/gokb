

curl -XDELETE 'http://localhost:9200/gokb'

curl -X PUT "localhost:9200/gokb" -d '{
  "settings" : {}
}'

curl -X PUT "localhost:9200/gokb/component/_mapping" -d '{
  "component" : {
    "properties" : {
      "name" : {
        type : "multi_field",
        fields : {
          name : { type : "string", analyzer : "snowball" },
          altname : { type : "string", analyzer : "snowball" }
        }
      },
      "componentType" : { 
        type:"string", 
        index:"not_analyzed" 
      }
    }
  }
}'
