package com.k_int

import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.action.search.*
import org.elasticsearch.index.query.*
import org.elasticsearch.search.sort.*
import org.elasticsearch.client.*
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.gokb.cred.*


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
  def genericOIDService

  def requestMapping = [
    generic: [
      "id",
      "uuid",
      "listStatus"
    ],
    simpleMap: [
      "curatoryGroup": "curatoryGroups",
      "role": "roles"
    ],
    complex: [
      "identifier",
      "status",
      "platform",
      "suggest",
      "label",
      "name",
      "altname"
    ],
    linked: [
      provider: "provider",
      publisher: "publisher",
      currentPublisher: "publisher",
      linkedPackage: "tippPackage",
      tippPackage: "tippPackage",
      pkg: "tippPackage",
      tippTitle: "tippTitle",
      linkedTitle: "tippTitle",
      title: "tippTitle"
    ],
    dates: [
      "changedSince",
      "changedBefore"
    ]
  ]

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

  private void checkInt(result, errors, str, String field) {
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

  private void addDateQueries(query, errors, qpars) {
    if ( qpars.changedSince || qpars.changedBefore ) {
      QueryBuilder dateQuery = QueryBuilders.rangeQuery("lastUpdatedDisplay")

      if (qpars.changedSince) {
        dateQuery.gte(date_filters.changedSince)
      }
      if (qpars.changedBefore) {
        dateQuery.lt(date_filters.changedBefore)
      }
      dateQuery.format("yyyy-MM-dd HH:mm:ss||yyyy-MM-dd")

      query.must(dateQuery)
    }
  }

  private void addStatusQuery(query, errors, qpars) {
    if ( qpars.list('status').size() > 0 ) {

      QueryBuilder statusQuery = QueryBuilders.boolQuery()

      qpars.list('status').each {
        statusQuery.should(QueryBuilders.termQuery('status', it))
      }

      statusQuery.minimumNumberShouldMatch(1)

      query.must(statusQuery)
    }
    else {
      query.must(QueryBuilders.termQuery('status', 'Current'))
    }
  }

  private void addIdentifierQuery(query,errors, qpars) {
    def id_params = [:]

    if (qpars.identifier) {
      if (v.contains(',')) {
        id_params['identifiers.namespace'] = v.split(',')[0]
        id_params['identifiers.value'] = v.split(',')[1]
      }else{
        id_params['identifiers.value'] = v
      }
      query.must(QueryBuilders.nestedQuery("identifiers", addIdQueries(id_params), ScoreMode.None))
    }
  }

  private void processNameFields(query, errors, qpars) {
    if (qpars.label) {

      QueryBuilder labelQuery = QueryBuilders.boolQuery()

      labelQuery.should(QueryBuilders.matchQuery('name', qpars.label))
      labelQuery.should(QueryBuilders.matchQuery('altname', qpars.label))
      labelQuery.minimumNumberShouldMatch(1)

      query.must(labelQuery)
    }
    else if (qpars.name) {
      query.must(QueryBuilders.matchQuery('name',qpars.name))
    }
    else if (qpars.altname) {
      query.must(QueryBuilders.matchQuery('altname',qpars.altname))
    }
    else if (qpars.suggest) {
      query.must(QueryBuilders.matchQuery('suggest',qpars.suggest))
    }
  }

  private void processLinkedField(query, field, val) {
    QueryBuilder linkedFieldQuery = QueryBuilders.boolQuery()

    linkedFieldQuery.should(QueryBuilders.termQuery(field, val))
    linkedFieldQuery.should(QueryBuilders.termQuery("${field}Uuid".toString(), val))
    linkedFieldQuery.should(QueryBuilders.termQuery("${field}Name".toString(), val))
    linkedFieldQuery.minimumNumberShouldMatch(1)

    query.must(linkedFieldQuery)
  }

  private void addPlatformQuery(query, errors, val) {
    QueryBuilder linkedFieldQuery = QueryBuilders.boolQuery()

    linkedFieldQuery.should(QueryBuilders.termQuery('nominalPlatform', val))
    linkedFieldQuery.should(QueryBuilders.termQuery('nominalPlatformName', val))
    linkedFieldQuery.should(QueryBuilders.termQuery('nominalPlatformUuid', val))
    linkedFieldQuery.should(QueryBuilders.termQuery('hostPlatform', val))
    linkedFieldQuery.should(QueryBuilders.termQuery('hostPlatformName', val))
    linkedFieldQuery.should(QueryBuilders.termQuery('hostPlatformUuid', val))
    linkedFieldQuery.minimumNumberShouldMatch(1)

    query.must(linkedFieldQuery)

    log.debug("Processing platform value ${val} .. ")
  }

  /**
   * find : Query the Elasticsearch index -- 
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
    def result = [result: 'OK']
    def search_action = null
    def errors = [:]
    log.debug("find :: ${params}")

    try {

      Client esclient = ESWrapperService.getClient()

      QueryBuilder exactQuery = QueryBuilders.boolQuery()

      def singleParams = [:]
      def linkedFieldParams = [:]
      def unknown_fields = []
      def other_fields = ["controller","action","max","offset","from","skipDomainMapping", "sort", "componentType"]
      def pkgNameSort = false
      def acceptedStatus = []
      def component_type = null
      def date_filters = [changedSince: null, changedBefore: null]

      if (params.componentType){
        component_type = deriveComponentType(params.componentType)

        if (component_type == "TitleInstance") {
          QueryBuilder typeQuery = QueryBuilders.boolQuery()

          typeQuery.should(QueryBuilders.termQuery('componentType', "JournalInstance"))
          typeQuery.should(QueryBuilders.termQuery('componentType', "DatabaseInstance"))
          typeQuery.should(QueryBuilders.termQuery('componentType', "BookInstance"))

          typeQuery.minimumNumberShouldMatch(1)

          exactQuery.must(typeQuery)
        }
        else if (component_type) {
          exactQuery.must(QueryBuilders.termQuery('componentType',component_type))
        }
        log.debug("Using component type ${component_type}")
      }
      else {
        log.debug("Not filtering by component type..")
      }

      addStatusQuery(exactQuery, errors, params)
      addDateQueries(exactQuery, errors, params)

      requestMapping.linked.platform.each {
        def platformQry = null

        if (params[it] && !platformQry) {
          addPlatformQuery(exactQuery, errors, params[it])
        }
      }

      requestMapping.generic.each {
        if (params[it]) {
          exactQuery.must(QueryBuilders.matchQuery(it, params[it]))
        }
      }

      params.each { k, v ->
        if (k in requestMapping.generic) {
          exactQuery.must(QueryBuilders.matchQuery(k, v))
        }
        else if (requestMapping.simpleMap.containsKey(k)) {
          exactQuery.must(QueryBuilders.matchQuery(requestMapping.simpleMap[k], v))
        }
        else if (requestMapping.linked.containsKey(k)) {
          processLinkedField(exactQuery, k, v)
        }
        else if (k.contains('platform')) {
          addPlatformQuery(exactQuery, errors, v)
        }
        else if (k in requestMapping.dates) {
        }
        else if (k in requestMapping.complex) {
        }
        else if (k in other_fields) {
        }
        else  {
          unknown_fields.add(k)
        }
      }

      if(unknown_fields.size() > 0){
        errors['unknown_params'] = unknown_fields
      }

      if( !errors && exactQuery.hasClauses() ) {
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

        if (params.sort) {
          FieldSortBuilder sortQry = new FieldSortBuilder(params.sort)
          es_request.addSort(sortQry)
        }

        search_action = es_request.execute()
      }
      else if ( !exactQuery.hasClauses() ){
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
          
          if (!params.skipDomainMapping) {
            response_record = mapEsToDomain(r)
          }
          else {
            response_record.id = r.id

            if (response_record.score && response_record.score != Float.NaN) {
              response_record.score = r.score
            }

            r.source.each { field, val ->
              response_record."${field}" = val
            }
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

  private Map mapEsToDomain(record) {
    def domainMapping = [:]
    def base = grailsApplication.config.serverURL + "/rest"
    def esMapping = [
      'lastUpdatedDisplay': 'lastUpdated',
      'sortname': false,
      'updater': false,
    ]

    def obj = KBComponent.findByUuid(record.source.uuid)

    if (obj) {

      if (KBComponent.has(obj,'jsonMapping') && obj.jsonMapping.es) {
        esMapping << obj.jsonMapping.es
      }

      domainMapping['links'] = ['self': ['href': base + obj.restPath + "/${obj.uuid}"]]
      domainMapping['embedded'] = [:]
      
      log.debug("Mapping ${record}")

      record.source.each { field, val ->
        if (field == "curatoryGroups") {
          mapCuratoryGroups(domainMapping, val)
        }
        else if (field == "altname") {
          domainMapping['embedded']['variantNames'] = val
        }
        else if (field == "identifiers") {
          domainMapping['embedded']['ids'] = val
        }
        else if (esMapping[field] == false) {
          log.debug("Skipping field ${field}!")
        }
        else if (esMapping[field]) {
          log.debug("Field ${esMapping[field]}")
          def fieldPath = esMapping[field].split("\\.")
          log.debug("FieldPath: ${fieldPath}")

          if (fieldPath.size() == 2) {
            def linkedObj = obj."${fieldPath[0]}"

            if (linkedObj) {
              domainMapping['links'][fieldPath[0]] = ['href': base + linkedObj.restPath + "/${linkedObj.uuid}"]
            }
          } else {
            domainMapping[fieldPath[0]] = obj."${esMapping[field]}"
          }
        }
        else {
          log.debug("Transfering unmapped field ${field}:${val}")
          domainMapping[field] = val
        }
      }
    }

    log.debug("${domainMapping}")
    return domainMapping
  }

  private def mapCuratoryGroups(domainMapping, cgs) {
    def base = grailsApplication.config.serverURL + "/rest"

    domainMapping['embedded']['curatoryGroups'] = []
    cgs.each { cg ->
      def cg_obj = CuratoryGroup.findByName(cg)
      domainMapping['embedded']['curatoryGroups'] << [
        'links': [
          'self': [
            'href': base + "/groups/" + cg_obj.uuid
          ]
        ],
        'name': cg_obj.name,
        'id': cg_obj.id,
        'uuid': cg_obj.uuid
      ]
    }
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
