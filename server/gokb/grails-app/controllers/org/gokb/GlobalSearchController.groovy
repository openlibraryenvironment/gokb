package org.gokb

class GlobalSearchController {

  static def reversemap = ['subject':'subjectKw','componentType':'componentType']
  static def non_analyzed_fields = ['componentType']


  def ESWrapperService
  def grailsApplication

  def index() { 
    def result = [:]

    def esclient = ESWrapperService.getClient()

    try {

      if ( params.q && params.q.length() > 0) {

        // Comment out replacement of ' by " so we can do exact string searching on identifiers - not sure what the use case
        // was for this anyway. Pls document in comment and re-add if needed.
        // params.q = params.q.replace('"',"'")
        params.q = params.q.replace('[',"(")
        params.q = params.q.replace(']',")")

        result.max = params.max ? Integer.parseInt(params.max) : 10;
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

        def query_str = buildQuery(params);

        log.debug("Searching for ${query_str}");

        def typing_field = grailsApplication.config.globalSearch.typingField ?: 'componentType'

        def search_action = esclient.search {
                       indices grailsApplication.config.globalSearch.indices
                       types grailsApplication.config.globalSearch.types
                       source {
                         from = result.offset
                         size = result.max
                         query {
                           query_string (query: query_str)
                         }
                         aggregations {
                           'Component Type' {
                             terms {
                               field = typing_field
                             }
                           }
                         }
                       }
                     }

        def search = search_action.actionGet()

        result.hits = search.hits

        if(search.hits.maxScore == Float.NaN) { //we cannot parse NaN to json so set to zero...
          search.hits.maxScore = 0;
        }

        result.resultsTotal = search.hits.totalHits
        // We pre-process the facet response to work around some translation issues in ES

        if ( search.getAggregations() != null ) {
          result.facets = [:]
          search.getAggregations().each { entry ->
            def facet_values = []
            entry.buckets.each { bucket ->
                log.debug("Bucket: ${bucket}");
                bucket.each { bi ->
                  log.debug("Bucket item: ${bi} ${bi.getKey()} ${bi.getDocCount()}");
                  facet_values.add([term:bi.getKey(),display:bi.getKey(),count:bi.getDocCount()])
                  }
            }
            result.facets[entry.getName()] = facet_values
          }
        }
      }
    }
    finally {
    }

    result;
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
        }
      }
    }

    def result = sw.toString();
    result;
  }

}
