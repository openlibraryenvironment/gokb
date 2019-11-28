package com.k_int

import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.action.search.*
import org.elasticsearch.index.query.*
import org.elasticsearch.search.sort.*
import org.elasticsearch.client.*
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.sort.SortOrder;



class ESSearchService{
// Map the parameter names we use in the webapp with the ES fields
  def reversemap = [
                    'type':'rectype',
                    'curatoryGroups':'curatoryGroups',
                    'cpname':'cpname',
                    'provider':'provider',
                    'componentType':'componentType',
                    'lastUpdatedDisplay':'lastUpdatedDisplay']

  def ESWrapperService
  def grailsApplication

  def search(params){
    search(params,reversemap)
  }

  def search(params, field_map){
    log.debug("ESSearchService::search - ${params}")

   def result = [:]

   Client esclient = ESWrapperService.getClient()
  
    try {
      if ( (params.q && params.q.length() > 0) || params.rectype) {

       if ((!params.all) || (!params.all?.equals("yes"))) {
         params.max = Math.min(params.max ? params.max : 15, 100)
        }

        params.offset = params.offset ? params.offset : 0

        def query_str = buildQuery(params,field_map)

        if (params.tempFQ) {
          log.debug("found tempFQ, adding to query string")
          query_str = query_str + " AND ( " + params.tempFQ + " ) "
          params.remove("tempFQ") //remove from GSP access
        }

        
        def es_index = grailsApplication.config.gokb?.es?.index ?: "gokbg3"
        log.debug("index:${es_index} query: ${query_str}");

        def search_results = null
        
        try {
          log.debug("start to build srb with index: " + es_index)
          SearchRequestBuilder srb = esclient.prepareSearch(es_index)
          log.debug("srb built: ${srb} sort=${params.sort}");
          if (params.sort) {
            SortOrder order = SortOrder.ASC
            if (params.order) {
              order = SortOrder.valueOf(params.order?.toUpperCase())
            }
            srb = srb.addSort("${params.sort}".toString(), order)
          }
          log.debug("srb start to add query and aggregration query string is ${query_str}")
    
          srb.setQuery(QueryBuilders.queryStringQuery(query_str))//QueryBuilders.wrapperQuery(query_str)
             .addAggregation(AggregationBuilders.terms('curatoryGroups').size(25).field('curatoryGroups'))
             .addAggregation(AggregationBuilders.terms('provider').size(25).field('provider'))
             .setFrom(params.offset)
             .setSize(params.max)
             
          // log.debug("finished srb and aggregrations: " + srb)
          search_results = srb.get()
          // log.debug("search results: " + search_results)
        }
        catch (Exception ex) {
          log.error("Error processing ${es_index} ${query_str}",ex);
        }
        
        //TODO: change this part to represent what we really need if this is not it, see the final part of this method where hits are done
        if (search_results) {
          def search_hits = search_results.getHits()
          result.hits = search_hits.getHits()
          result.firstrec = params.offset + 1
          result.resultsTotal = search_hits.totalHits
          result.lastrec = Math.min ( params.offset + params.max, result.resultsTotal)
          
          if (search_results.getAggregations()) {
            result.facets = [:]
            search_results.getAggregations().each { entry ->
              log.debug("Aggregation entry ${entry} ${entry.getName()}");
              def facet_values = []
              entry.buckets.each { bucket ->
                bucket.each { bi ->
                  facet_values.add([term:bi.getKey(),display:bi.getKey(),count:bi.getDocCount()])
                }
              }
              result.facets[entry.getName()] = facet_values
            }
          }
        }
        log.debug("finished results facets")
      }
      else {
        log.debug("No query.. Show search page")
      }
    }
    finally {
      try {
        log.debug("in finally")
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
      sw.write("name:${params.q}")
    }
      
    if(params?.rectype){
      if(sw.toString()) sw.write(" AND ");
      sw.write(" rectype:${params.rectype} ")
    } 

    field_map.each { mapping ->

      if ( params[mapping.key] != null ) {

        log.debug("Class is: ${params[mapping.key].class.name}")

        if ( params[mapping.key] instanceof String[] ) {
          log.debug("mapping is an arraylist: ${mapping} ${mapping.key} ${params[mapping.key]}")
          if(sw.toString()) sw.write(" AND ");

          def plist = params[mapping.key]

          plist.eachWithIndex { p, idx ->
            if (p) {
                if (idx == 0){
                  sw.write(" ( ")
                }
                sw.write(mapping.value?.toString())
                sw.write(":".toString())

                p = p.replaceAll(":","\\\\:")

                sw.write(p.toString())
                if(idx == plist.size()-1) {
                  sw.write(" ) ")
                }else{
                  sw.write(" OR ")
                }
            }
          }
        }
        else {
          // Only add the param if it's length is > 0 or we end up with really ugly URLs
          // II : Changed to only do this if the value is NOT an *

          log.debug("Processing - scalar value : ${params[mapping.key]}");

          try {
            if ( params[mapping.key].length() > 0 && ! ( params[mapping.key].equalsIgnoreCase('*') ) ) {

                def pval = params[mapping.key].replaceAll(":","\\\\:");

                log.debug("pval = ${pval}")

                if(sw.toString()) sw.write(" AND ");

                sw.write(mapping.value)
                sw.write(":")

                if(params[mapping.key].startsWith("[") && params[mapping.key].endsWith("]")){
                  sw.write(pval)
                }else{
                  sw.write(pval)
                }
            }
          }
          catch ( Exception e ) {
            log.error("Problem procesing mapping, key is ${mapping.key} value is ${params[mapping.key]}",e);
          }
        }
      }
    }

    if(!params['status']) {
      sw.write(" AND NOT (status:Deleted)")
    }

    def result = sw.toString();
    log.debug("Result of buildQuery is ${result}");

    result;
  }

  private void checkInt(def result, def errors, def str, String field) {
    def value = null
    if (str && str instanceof String) {
      try {
        value = str as Integer
        result[field] = value
      } catch (Exception e) {
        errors[field] = "Could not convert ${field} to Int."
      }
    }
    else if (str && str instanceof Integer) {
      result[field] = value
    }
  }

  /**
   * find : Query the Elasticsearch index
   * @param max : Define result size
   * @param offset : Define offset
   * @param from : Define offset
   * @param label : Search in name + variantNames
   * @param name : Search in name
   * @param altname : Search in variantNames
   * @param id : Search by object ID ([classname]:[id])
   * @param uuid : Search by component UUID
   * @param identifier : Search for a linked external identifier ([identifier] or [namespace],[identifier])
   * @param componentType : Filter by component Type (Package, Org, Platform, BookInstance, JournalInstance, TIPP)
   * @param role : Filter by Org role (only in context of componentType=Org)
   * @param linkedPackage : Filter by linked Package (only in context of componentType=TIPP)
   * @param listStatus : Filter by title list status (only in context of componentType=Package)
   * @param status : Filter by component status (Current, Expected, Retired, Deleted)
   * @param linkedTitle : Filter by linked TitleInstance (only in context of componentType=TIPP)
   * @param curatoryGroup : Filter by connected Curatory Group
  **/

  def find(params) {
    def result = [:]
    def esclient = ESWrapperService.getClient()
    def search_action = null
    def errors = [:]
    log.debug("find :: ${params}")

    try {

      QueryBuilder exactQuery = QueryBuilders.boolQuery()

      def singleParams = [:]
      def linkedFieldParams = [:]
      def unknown_fields = []
      def other_fields = ["controller","action","max","offset","from"]
      def direct_fields = ["cpname", "provider", "id", "uuid", "suggest"]
      def linked_fields = ["hostPlatform", "nominalPlatform", "platform", "listStatus", "role"]
      def id_params = [:]
      def pkgNameSort = false
      def acceptedStatus = []
      def component_type = null
      def date_filters = [changedSince: null, changedBefore: null]

      params.each { k, v ->
        if ( k == 'componentType' && v instanceof String ) {

          component_type = deriveComponentType(v)

          if(!component_type) {
            errors['componentType'] = "Requested component type ${v} does not exist"
          }
        }
        else if (k in direct_fields && v instanceof String) {
          singleParams[k] = v
        }

        else if (k in linked_fields && v instanceof String) {
          linkedFieldParams[k] = v
        }

        else if (params.componentType == 'Package' && k == 'sort' && v == 'name') {
          pkgNameSort = true
        }

        else if ( (k == 'publisher' || k == 'currentPublisher') && v instanceof String) {
          linkedFieldParams['publisher'] = v
        }

        else if ( (k == 'linkedPackage' || k == 'tippPackage') && v instanceof String) {
          linkedFieldParams['tippPackage'] = v
        }

        else if (k == 'status') {
          acceptedStatus = params.list(k)
        }

        else if ( (k == 'linkedTitle' || k == 'tippTitle') && v instanceof String) {
          linkedFieldParams['tippTitle'] = v
        }

        else if (k == 'curatoryGroup' && v instanceof String) {
          singleParams['curatoryGroups'] = v
        }

        else if ((k == 'label' || k == "q") && v instanceof String) {

          QueryBuilder labelQuery = QueryBuilders.boolQuery()

          labelQuery.should(QueryBuilders.matchQuery('name', v))
          labelQuery.should(QueryBuilders.matchQuery('altname', v))
          labelQuery.minimumNumberShouldMatch(1)

          exactQuery.must(labelQuery)
        }
        else if ((k == 'changedSince' || k == 'changedBefore') && v instanceof String) {
          date_filters[k] = v
        }

        else if (!params.label && k == "name" && v instanceof String) {
          singleParams['name'] = v
        }

        else if (!params.label && k == "altname" && v instanceof String) {
          singleParams['altname'] = v
        }

        else if ( k == "identifier" && v instanceof String) {
          if (v.contains(',')) {
            id_params['identifiers.namespace'] = v.split(',')[0]
            id_params['identifiers.value'] = v.split(',')[1]
          }else{
            id_params['identifiers.value'] = v
          }
        }

        else if (!other_fields.contains(k)){
          unknown_fields.add(k)
        }
      }

      if(unknown_fields.size() > 0){
        errors['unknown_params'] = unknown_fields
      }

      if ( date_filters.changedSince || date_filters.changedBefore ) {
        QueryBuilder dateQuery = QueryBuilders.rangeQuery("lastUpdatedDisplay")

        if (date_filters.changedSince) {
          dateQuery.gte(date_filters.changedSince)
        }
        if (date_filters.changedBefore) {
          dateQuery.lt(date_filters.changedBefore)
        }
        dateQuery.format("yyyy-MM-dd HH:mm:ss||yyyy-MM-dd")

        exactQuery.must(dateQuery)
      }

      if ( linkedFieldParams.listStatus ) {
        if ( component_type && component_type == 'Package' ) {
          singleParams['listStatus'] = pkgListStatus
        }
        else {
          errors['listStatus'] = "To filter by Package List Status, please add filter componentType=Package to the query"
        }
      }

      if ( acceptedStatus.size() > 0 ) {

        QueryBuilder statusQuery = QueryBuilders.boolQuery()

        acceptedStatus.each {
          statusQuery.should(QueryBuilders.termQuery('status', it))
        }

        statusQuery.minimumNumberShouldMatch(1)

        exactQuery.must(statusQuery)
      }

      else {
        exactQuery.must(QueryBuilders.termQuery('status', 'Current'))
      }

      linkedFieldParams.each { k, v ->
        QueryBuilder linkedFieldQuery = QueryBuilders.boolQuery()

        linkedFieldQuery.should(QueryBuilders.termQuery(k, v))
        linkedFieldQuery.should(QueryBuilders.termQuery("${k}Uuid".toString(), v))
        linkedFieldQuery.minimumNumberShouldMatch(1)

        exactQuery.must(linkedFieldQuery)
      }

      if ( linkedFieldParams['role'] ) {
        if ( component_type && component_type == 'Org') {
          singleParams['roles'] = orgRoleParam
        }
        else {
          errors['role'] = "To filter by Org Roles, please add filter componentType=Org to the query"
        }
      }

      if ( linkedFieldParams.hostPlatform ) {
        if ( component_type && component_type == 'TitleInstancePackagePlatform' ) {
          QueryBuilder linkedFieldQuery = QueryBuilders.boolQuery()

          linkedFieldQuery.should(QueryBuilders.termQuery('hostPlatform', hostPlatformId))
          linkedFieldQuery.should(QueryBuilders.termQuery('hostPlatformUuid', hostPlatformId))
          linkedFieldQuery.minimumNumberShouldMatch(1)

          exactQuery.must(linkedFieldQuery)
        }
        else {
          errors['hostPlatform'] = "To filter by Host Platform, please add filter componentType=TIPP to the query"
        }
      }

      if ( linkedFieldParams.nominalPlatform ) {
        if ( component_type && component_type == 'Package' ) {
          QueryBuilder linkedFieldQuery = QueryBuilders.boolQuery()

          linkedFieldQuery.should(QueryBuilders.termQuery('nominalPlatform', nominalPlatformId))
          linkedFieldQuery.should(QueryBuilders.termQuery('platformUuid', nominalPlatformId))
          linkedFieldQuery.minimumNumberShouldMatch(1)

          exactQuery.must(linkedFieldQuery)
        }
        else {
          errors['nominalPlatform'] = "To filter by Package Platform, please add filter componentType=Package to the query"
        }
      }

      if ( linkedFieldParams.platform ) {
        if ( component_type ) {

          if( component_type == 'TitleInstancePackagePlatform' ) {
            QueryBuilder linkedFieldQuery = QueryBuilders.boolQuery()

            linkedFieldQuery.should(QueryBuilders.termQuery('hostPlatform', genericPlatformId))
            linkedFieldQuery.should(QueryBuilders.termQuery('hostPlatformUuid', genericPlatformId))
            linkedFieldQuery.minimumNumberShouldMatch(1)

            exactQuery.must(linkedFieldQuery)
          }

          else if ( component_type == 'Package' ) {
            QueryBuilder linkedFieldQuery = QueryBuilders.boolQuery()

            linkedFieldQuery.should(QueryBuilders.termQuery('nominalPlatform', genericPlatformId))
            linkedFieldQuery.should(QueryBuilders.termQuery('platformUuid', genericPlatformId))
            linkedFieldQuery.minimumNumberShouldMatch(1)

            exactQuery.must(linkedFieldQuery)
          }

          else {
            errors['platform'] = "No platform context available for component type ${component_type}"
          }
        }
        else {
          errors['platform'] = "To filter by Platform, please add filter componentType=TIPP to the query"
        }
      }

      if (component_type == "TitleInstance") {
        QueryBuilder typeQuery = QueryBuilders.boolQuery()

        typeQuery.should(QueryBuilders.matchQuery('componentType', "JournalInstance"))
        typeQuery.should(QueryBuilders.matchQuery('componentType', "DatabaseInstance"))
        typeQuery.should(QueryBuilders.matchQuery('componentType', "BookInstance"))

        typeQuery.minimumNumberShouldMatch(1)

        exactQuery.must(typeQuery)
      }
      else if (component_type) {
        singleParams['componentType'] = component_type
      }

      if (singleParams) {
        singleParams.each { k,v ->
          exactQuery.must(QueryBuilders.matchQuery(k,v))
        }
      }

      if (id_params) {
        exactQuery.must(QueryBuilders.nestedQuery("identifiers", addIdQueries(id_params), ScoreMode.None))
      }

      if( !errors && (singleParams || params.label || id_params || component_type) ) {
        SearchRequestBuilder es_request =  esclient.prepareSearch("exact")

        es_request.setIndices(grailsApplication.config.gokb.es.index)
        es_request.setTypes(grailsApplication.config.globalSearch.types)
        es_request.setQuery(exactQuery)

        checkInt(result, errors, params.max, 'max')
        checkInt(result, errors, params.from, 'offset')
        checkInt(result, errors, params.offset, 'offset')

        if (params.max) {
          es_request.setSize(result.max)
        }
        else {
          result.max = 10
        }

        if (params.offset || params.from) {
          es_request.setFrom(result.offset)
        }
        else {
          result.offset = 0
        }

        if (pkgNameSort) {
          FieldSortBuilder pkgSort = new FieldSortBuilder('sortname')
          es_request.addSort(pkgSort)
        }

        search_action = es_request.execute()
      }
      else if ( !singleParams && !component_type && !params.label && !id_params){
        errors['params'] = "No valid parameters found"
      }

      def search = null

      if (search_action) {
        search = search_action.actionGet()


        if(search.hits.maxScore == Float.NaN) { //we cannot parse NaN to json so set to zero...
          search.hits.maxScore = 0;
        }

        result.count = search.hits.totalHits
        result.records = []

        search.hits.each { r ->
          def response_record = [:]
          response_record.id = r.id

          if (response_record.score && response_record.score != Float.NaN) {
            response_record.score = r.score
          }

          r.source.each { field, val ->
            response_record."${field}" = val
          }

          result.records.add(response_record);
        }
      }

    } finally {
      if (errors) {
        result = [:]
        result.result = "ERROR"
        result.errors = errors
      }
    }

    result
  }

  private def deriveComponentType(String typeString) {
    def result = null
    def defined_types = [
      "Package",
      "Org",
      "JournalInstance",
      "Journal",
      "BookInstance",
      "Book",
      "DatabaseInstance",
      "Database",
      "Platform",
      "TitleInstancePackagePlatform",
      "TIPP",
      "TitleInstance",
      "Title"
    ]
    def final_type = typeString.capitalize()

    if(final_type in defined_types) {

      if(final_type== 'TIPP') {
        final_type = 'TitleInstancePackagePlatform'
      }
      else if (final_type == 'Book') {
        final_type = 'BookInstance'
      }
      else if (final_type == 'Journal') {
        final_type = 'JournalInstance'
      }
      else if (final_type == 'Database') {
        final_type = 'DatabaseInstance'
      }
      else if (final_type == 'Title') {
        final_type = 'TitleInstance'
      }

      result = final_type
    }
    result
  }

  private def addIdQueries(params) {

    QueryBuilder idQuery = QueryBuilders.boolQuery()

    params.each { k,v ->
      idQuery.must(QueryBuilders.termQuery(k, v))
    }

    return idQuery
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
  }

}
