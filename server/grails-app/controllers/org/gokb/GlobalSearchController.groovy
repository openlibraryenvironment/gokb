package org.gokb

import grails.converters.*
import org.opensearch.action.search.*
import org.opensearch.client.RequestOptions
import org.opensearch.search.aggregations.AggregationBuilders
import org.opensearch.index.query.*
import org.opensearch.search.builder.SearchSourceBuilder
import org.opensearch.search.sort.FieldSortBuilder
import org.opensearch.search.sort.SortOrder

class GlobalSearchController {

  static def reversemap = ['subject':'subjectKw','componentType':'componentType','status':'status']
  static def non_analyzed_fields = ['componentType','status']


  def ESWrapperService

  def index() {
    def result = [:]
    def apiresponse = null

    def esclient = ESWrapperService.getClient()
    if ( params.q && params.q.length() > 0) {
      params.q = params.q.replace('[',"(")
      params.q = params.q.replace(']',")")
      params.q = params.q.replace(':',"")

      result.max = params.max ? Integer.parseInt(params.max) : 10;
      result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

      def query_str = buildQuery(params)
      log.debug("Searching for ${query_str}")
      log.debug("... using indices ${grailsApplication.config.gokb?.es?.indices?.values().join(", ")}")
      QueryBuilder esQuery = QueryBuilders.queryStringQuery(query_str)

      def typing_field = grailsApplication.config.globalSearch.typingField ?: 'componentType'
      SearchResponse searchResponse
      SearchRequest searchRequest = new SearchRequest(grailsApplication.config.globalSearch.indices as String[])
      SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()

      if (params.sort){
        SortOrder order = SortOrder.ASC
        if (params.order){
          order = SortOrder.valueOf(params.order?.toUpperCase())
        }
        searchSourceBuilder.sort(new FieldSortBuilder("${params.sort}").order(order))
      }

      searchSourceBuilder.query(QueryBuilders.queryStringQuery(query_str))
      searchSourceBuilder.aggregation(AggregationBuilders.terms('ComponentType').size(25).field(typing_field))
      searchSourceBuilder.from(result.offset)
      searchSourceBuilder.size(result.max)
      searchRequest.source(searchSourceBuilder)
      searchResponse = esclient.search(searchRequest, RequestOptions.DEFAULT)
      result.hits = searchResponse.getHits()

      result.resultsTotal = searchResponse.getHits().getTotalHits().value ?: 0
      // We pre-process the facet response to work around some translation issues in ES

      if (searchResponse.getAggregations()) {
        result.facets = [:]
        searchResponse.getAggregations().each { entry ->
          def facet_values = []
          if(entry.type == 'nested'){
            entry.getAggregations().each { subEntry ->
              subEntry.buckets.each { bucket ->
                bucket.each { bi ->
                  def displayTerm = (bi.getKey() != 'TitleInstancePackagePlatform' ? bi.getKey() : 'Titles')
                  log.debug("Bucket item: ${bi} ${bi.getKey()} ${bi.getDocCount()}")
                  facet_values.add([term:bi.getKey(),display:displayTerm,count:bi.getDocCount()])
                }
              }
            }
          }
          else {
            entry.buckets.each { bucket ->
              bucket.each { bi ->
                def displayTerm = (bi.getKey() != 'TitleInstancePackagePlatform' ? bi.getKey() : 'TIPP')
                log.debug("Bucket item: ${bi} ${bi.getKey()} ${bi.getDocCount()}");
                facet_values.add([term: bi.getKey(), display: displayTerm, count: bi.getDocCount()])
              }
            }
          }
          result.facets[entry.getName()] = facet_values
        }
      }
      if ( ( response.format == 'json' ) || ( response.format == 'xml' ) ) {
        apiresponse = [:]
        apiresponse.count = result.resultsTotal
        apiresponse.max = result.max
        apiresponse.offset = result.offset
        apiresponse.records = []
        result.hits.each { r ->
          def response_record = [:]
          response_record.id = r.id
          response_record.score = r.score
          response_record.name = r.getSourceAsMap().name
          response_record.identifiers = r.getSourceAsMap().identifiers
          response_record.altNames = r.getSourceAsMap().altname
          apiresponse.records.add(response_record)
        }
      }
    }

    withFormat {
      html result
      json { render apiresponse as JSON }
      xml { render apiresponse as XML }
    }
  }

  private def buildQuery(params) {

    StringWriter sw = new StringWriter()

    if ( ( params != null ) && ( params.q != null ) )
      if(params.q.equals("*")){
        sw.write(params.q)
      }
      else{
        sw.write("(${params.q})")
      }
    else
      sw.write("*:*")

    // For each reverse mapping
    reversemap.each { mapping ->

      // log.debug("testing ${mapping.key}");

      // If the query string supplies a value for that mapped parameter
      if ( params[mapping.key] != null ) {

        // If we have a list of values, rather than a scalar
        if ( params[mapping.key].class == java.util.ArrayList) {
          params[mapping.key].each { p ->
            sw.write(" AND ")
            sw.write(mapping.value)
            sw.write(":")

            if(non_analyzed_fields.contains(mapping.value)) {
              sw.write("${p}")
            }
            else {
              sw.write("\"${p}\"")
            }
          }
        }
        else {
          // We are dealing with a single value, this is "a good thing" (TM)
          // Only add the param if it's length is > 0 or we end up with really ugly URLs
          // II : Changed to only do this if the value is NOT an *
          if ( params[mapping.key].length() > 0 && ! ( params[mapping.key].equalsIgnoreCase('*') ) ) {
            sw.write(" AND ")
            // Write out the mapped field name, not the name from the source
            sw.write(mapping.value)
            sw.write(":")

            if(non_analyzed_fields.contains(mapping.value)) {
              sw.write("${params[mapping.key]}")
            }
            else {
              sw.write("\"${params[mapping.key]}\"")
            }
          }
          else if (mapping.key == 'status') {
                  sw.write(" AND ")
                  sw.write("status:Current")
          }
        }
      }
      else if (mapping.key == 'status') {
        sw.write(" AND ")
        sw.write("status:Current")
      }
    }

    def result = sw.toString();
    result;
  }

}
