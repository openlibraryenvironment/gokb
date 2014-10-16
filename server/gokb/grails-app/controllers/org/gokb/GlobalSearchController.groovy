package org.gokb

class GlobalSearchController {

  static def reversemap = ['subject':'subjectKw','componentType':'componentType']
  static def non_analyzed_fields = ['componentType']


  def ESWrapperService

  def index() { 
    def result = [:]
    org.elasticsearch.groovy.node.GNode esnode = ESWrapperService.getNode()
    org.elasticsearch.groovy.client.GClient esclient = esnode.getClient()
    try {

      if ( params.q && params.q.length() > 0) {

        params.q = params.q.replace('"',"'")
        params.q = params.q.replace('[',"(")
        params.q = params.q.replace(']',")")

        result.max = params.max ? Integer.parseInt(params.max) : 10;
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

        def query_str = buildQuery(params);

        log.debug("Searching for ${query_str}");

        def search = esclient.search{
                       indices "gokb"
                       types "component"
                       source {
                         from = result.offset
                         size = result.max
                         query {
                           query_string (query: query_str)
                         }
                         facets {
                           'Component Type' {
                             terms {
                               field = 'componentType'
                             }
                           }
                         }
                       }
                     }

        result.hits = search.response.hits

        if(search.response.hits.maxScore == Float.NaN) { //we cannot parse NaN to json so set to zero...
          search.response.hits.maxScore = 0;
        }

        result.resultsTotal = search.response.hits.totalHits
        // We pre-process the facet response to work around some translation issues in ES

        if ( search.response.facets != null ) {
          result.facets = [:]
          search.response.facets.facets.each { facet ->
            def facet_values = []
            facet.value.entries.each { fe ->
              facet_values.add([term: fe.term,display:fe.term,count:"${fe?.count}"])
            }
            result.facets[facet.key] = facet_values
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
