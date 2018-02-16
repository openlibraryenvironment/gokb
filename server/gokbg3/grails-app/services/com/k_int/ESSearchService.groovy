package com.k_int
import org.elasticsearch.client.*
import org.elasticsearch.client.Client


class ESSearchService{
// Map the parameter names we use in the webapp with the ES fields
  def reversemap = ['subject':'subject', 
                    'provider':'provid',
                    'type':'rectype',
                    'endYear':'endYear',
                    'startYear':'startYear',
                    'consortiaName':'consortiaName',
                    'cpname':'cpname',
                    'availableToOrgs':'availableToOrgs',
                    'isPublic':'isPublic',
                    'lastModified':'lastModified']

  def ESWrapperService
  def grailsApplication

  def search(params){
    search(params,reversemap)
  }

  def search(params, field_map){
    // log.debug("Search Index, params.coursetitle=${params.coursetitle}, params.coursedescription=${params.coursedescription}, params.freetext=${params.freetext}")
    log.debug("ESSearchService::search - ${params}")

   def result = [:]

   Client esclient = ESWrapperService.getClient()
  
    try {
      if ( (params.q && params.q.length() > 0) || params.rectype) {
  
        params.max = Math.min(params.max ? params.int('max') : 15, 100)
        params.offset = params.offset ? params.int('offset') : 0

        def query_str = buildQuery(params,field_map)
        if (params.tempFQ) //add filtered query
        {
            query_str = query_str + " AND ( " + params.tempFQ + " ) "
            params.remove("tempFQ") //remove from GSP access
        }

        log.debug("index:${grailsApplication.config.aggr_es_index} query: ${query_str}");
  
        def search = esclient.search{
          indices grailsApplication.config.aggr_es_index ?: "kbplus"
          source {
            from = params.offset
            size = params.max
            sort = params.sort?[
              ("${params.sort}".toString()) : [ 'order' : (params.order?:'asc') ]
            ] : []

            query {
              query_string (query: query_str)
            }
            aggregations {
              consortiaName {
                terms {
                  field = 'consortiaName'
                  size = 25
                }
              }
              cpname {
                terms {
                  field = 'cpname'
                  size = 25
                }
              }
              type {
                terms {
                  field = 'rectype'
                }
              }
              startYear {
                terms {
                  field = 'startYear'
                  size = 25
                }
              }
              endYear {
                terms {
                  field = 'endYear'
                  size = 25
                }
              }
            }

          }

        }.actionGet()

        if ( search ) {
          def search_hits = search.hits
          result.hits = search_hits.hits
          result.resultsTotal = search_hits.totalHits

          // We pre-process the facet response to work around some translation issues in ES
          if ( search.getAggregations()) {
            result.facets = [:]
            search.getAggregations().each { entry ->
              log.debug("Aggregation entry ${entry} ${entry.getName()}");
              def facet_values = []
              entry.buckets.each { bucket ->
                log.debug("Bucket: ${bucket}");
                bucket.each { bi ->
                  log.debug("Bucket item: ${bi} ${bi.getKey()} ${bi.getDocCount()}");
                  facet_values.add([term:bi.getKey(),display:bi.getKey(),count:bi.getDocCount()])
                  }
                }
              result.facets[entry.getName()] = facet_values




          /*if ( search.getFacets()) {
            result.facets = [:]
            search.getFacets().facets().each { facet ->
              def facet_values = []
              for (entry in facet) {
                facet_values.add([term: entry.getTerm(), display: entry.getTerm(), count: entry.getCount()])
              }

              result.facets[facet.getName()] = facet_values*/

            }
          }
        }
      }
      else {
        log.debug("No query.. Show search page")
      }
    }
    finally {
      try {
      }
      catch ( Exception e ) {
        log.error("problem",e);
      }
    }
    result
  }

  def buildQuery(params,field_map) {
    log.debug("BuildQuery... with params ${params}. ReverseMap: ${field_map}");

    StringWriter sw = new StringWriter()

    if ( params?.q != null ){
      sw.write(params.q)
    }
      
    if(params?.rectype){
      if(sw.toString()) sw.write(" AND ");
      sw.write(" rectype:'${params.rectype}' ")
    } 

    field_map.each { mapping ->

      if ( params[mapping.key] != null ) {
        if ( params[mapping.key].class == java.util.ArrayList) {
          if(sw.toString()) sw.write(" AND ");
          sw.write(" ( ( ( NOT _type:\"com.k_int.kbplus.Subscription\" ) AND ( NOT _type:\"com.k_int.kbplus.License\" )) OR ( ")

          params[mapping.key].each { p ->  
                sw.write(mapping.value)
                sw.write(":")
                sw.write("\"${p}\"")
                if(p == params[mapping.key].last()) {
                  sw.write(" ) ) ")
                }else{
                  sw.write(" OR ")
                }
          }
        }
        else {
          // Only add the param if it's length is > 0 or we end up with really ugly URLs
          // II : Changed to only do this if the value is NOT an *

          log.debug("Processing ${params[mapping.key]} ${mapping.key}");

          try {
            if ( params[mapping.key] ) {
              if ( params[mapping.key].length() > 0 && ! ( params[mapping.key].equalsIgnoreCase('*') ) ) {
                if(sw.toString()) sw.write(" AND ");
                sw.write(mapping.value)
                sw.write(":")
                if(params[mapping.key].startsWith("[") && params[mapping.key].endsWith("]")){
                  sw.write("${params[mapping.key]}")
                }else{
                  sw.write("\"${params[mapping.key]}\"")
                }
              }
            }
          }
          catch ( Exception e ) {
            log.error("Problem procesing mapping, key is ${mapping.key} value is ${params[mapping.key]}",e);
          }
        }
      }
    }

    def result = sw.toString();
    result;
  }
}
