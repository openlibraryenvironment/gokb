package com.k_int

import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import groovyx.net.http.URIBuilder

import org.apache.lucene.search.join.ScoreMode
import org.opensearch.action.search.*
import org.opensearch.client.*
import org.opensearch.index.query.*
import org.opensearch.search.aggregations.AggregationBuilders
import org.opensearch.search.aggregations.bucket.terms.TermsAggregationBuilder
import org.opensearch.search.SearchHit
import org.opensearch.search.SearchHits
import org.opensearch.search.builder.SearchSourceBuilder
import org.opensearch.search.sort.*

import org.gokb.cred.*

import org.springframework.util.StringUtils


class ESSearchService{
// Map the parameter names we use in the webapp with the ES fields
  def reversemap = [
      'type':'rectype',
      'curatoryGroups':'curatoryGroups',
      'cpname':'cpname',
      'provider':'provider',
      'componentType':'componentType',
      'lastUpdatedDisplay':'lastUpdatedDisplay',
      'primaryUrl':'primaryUrl']

  def ESWrapperService
  def grailsApplication
  def genericOIDService
  def classExaminationService

  def requestMapping = [
      generic: [
          "id",
          "uuid",
          "importId",
          "primaryUrl"
      ],
      refdata: [
          "listStatus",
          "global",
          "editStatus",
          "contentType",
          "status"
      ],
      simpleMap: [
          "role": "roles"
      ],
      complex: [
          "identifier",
          "ids",
          "identifiers",
          "componentType",
          "curatoryGroup",
          "curatoryGroups",
          "platform",
          "suggest",
          "label",
          "name",
          "altname",
          "q",
          "qfields",
          "qsName"
      ],
      linked: [
          provider: "provider",
          currentPublisher: "publisher",
          linkedPackage: "tippPackage",
          tippPackage: "tippPackage",
          package: "tippPackage",
          pkg: "tippPackage",
          tippTitle: "tippTitle",
          linkedTitle: "tippTitle",
          title: "tippTitle",
          publisher: "publisher"
      ],
      dates: [
          "changedSince",
          "changedBefore"
      ],
      ignore: [
          "controller",
          "action",
          "max",
          "offset",
          "from",
          "skipDomainMapping",
          "sort",
          "order",
          "_embed",
          "_include",
          "_exclude"
      ]
  ]

  static Map indicesPerType = [
      "JournalInstance" : "titles",
      "DatabaseInstance" : "titles",
      "OtherInstance" : "titles",
      "BookInstance" : "titles",
      "TitleInstance" : "titles",
      "TitleInstancePackagePlatform" : "tipps",
      "Org" : "orgs",
      "Package" : "packages",
      "Platform" : "platforms"
  ]


  def search(params){
    search(params,reversemap)
  }

  def search(params, field_map){
    log.debug("ESSearchService.search() with params : ${params}")
    def result = [:]
    def esClient = ESWrapperService.getClient()
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

      log.debug("Start to build search request. Query: ${query_str}")
      SearchResponse searchResponse
      try{
        SearchRequest searchRequest = new SearchRequest(params.componentType ? [grailsApplication.config?.gokb?.es?.indices[indicesPerType[params.componentType]]] as String[] : grailsApplication.config?.gokb?.es?.indices?.values() as String[])
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
        TermsAggregationBuilder pragg = AggregationBuilders.terms("provider").field("provider")
        TermsAggregationBuilder cgagg = AggregationBuilders.terms("curatoryGroups").field("curatoryGroups")
        searchSourceBuilder.aggregation(pragg)
        searchSourceBuilder.aggregation(cgagg)

        log.debug("srb built: ${searchSourceBuilder} sort=${params.sort}")
        if (params.sort) {
          SortOrder order = SortOrder.ASC
          if (params.order) {
            order = SortOrder.valueOf(params.order?.toUpperCase())
          }
          searchSourceBuilder.sort(new FieldSortBuilder("${params.sort}").order(order))
        }
        log.debug("build searchSourceBuilder and aggregration query string is ${query_str}")
        searchSourceBuilder.query(QueryBuilders.queryStringQuery(query_str))
        searchSourceBuilder.from(params.offset)
        searchSourceBuilder.size(params.max)

        searchRequest.source(searchSourceBuilder)
        searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT)
      }
      catch (Exception ex) {
        log.error("Error occured during opensearch request using query: ${query_str}", ex)
      }

      if (searchResponse) {
        result.hits = searchResponse.getHits()
        result.firstrec = params.offset + 1
        result.resultsTotal = searchResponse.getHits().getTotalHits().value ?: 0
        result.lastrec = Math.min(params.offset + params.max, result.resultsTotal)

        if (searchResponse.getAggregations()) {
          result.facets = [:]
          searchResponse.getAggregations().each { entry ->
            log.debug("Aggregation entry ${entry} ${entry.getName()}")
            def facet_values = []
            if (entry.type == 'nested'){
              entry.getAggregations().each { subEntry ->
                subEntry.buckets.each { bucket ->
                  bucketsToFacetValues(bucket, facet_values)
                }
              }
            }
            else{
              entry.buckets.each { bucket ->
                bucketsToFacetValues(bucket, facet_values)
              }
            }
            result.facets[entry.getName()] = facet_values
          }
          log.debug("Finished results facets.")
        }
      }
    }
    else {
      log.debug("No sufficient query in params ... Show search page")
    }
    result
  }


  private void bucketsToFacetValues(def bucket, def facet_values){
    bucket.each { bi ->
      String display = "Unknown"
      if (bi.getKey().startsWith('org.gokb.cred') && KBComponent.get(bi.getKey().split(':')[1].toLong())){
        display = KBComponent.get(bi.getKey().split(':')[1].toLong()).name
      }
      facet_values.add([term: bi.getKey(), display: display, count: bi.getDocCount()])
    }
  }


  def buildQuery(params,field_map) {
    log.debug("BuildQuery... with params ${params}. ReverseMap: ${field_map}");
    StringWriter sw = new StringWriter()

    if ( params?.q != null ){
      sw.write("name:${params.q.replaceAll(':','\\\\:')}")
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
              }
              else{
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
              }
              else{
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

    result
  }

  private void checkInt(result, errors, str, String field) {
    def value = null
    if (str && str instanceof String) {
      try {
        value = str as Integer
        result[field] = value
      }
      catch (Exception e) {
        errors[field] = "Could not convert ${field} to Int."
      }
    }
    else if (str != null && str instanceof Integer) {
      result[field] = str
    }
  }

  private String sanitizeParam(String param) {
    return param.replaceAll(":", "\\\\:").replaceAll("/", "\\\\/")
  }

  private void addDateQueries(query, errors, qpars) {
    if (qpars.changedSince || qpars.changedBefore) {
      QueryBuilder dateQuery = QueryBuilders.rangeQuery("lastUpdatedDisplay")

      if (qpars.changedSince) {
        dateQuery.gte(qpars.changedSince)
      }
      if (qpars.changedBefore) {
        dateQuery.lt(qpars.changedBefore)
      }
      dateQuery.format("yyyy-MM-dd'T'HH:mm:ss'Z'||yyyy-MM-dd HH:mm:ss||yyyy-MM-dd")

      query.must(dateQuery)
    }
  }

  private void addRefdataQuery(query, errors, field, value) {
    QueryBuilder refdataQuery = QueryBuilders.boolQuery()
    if (value.getClass().isArray() || value instanceof List){
      value.each {
        addRefdataToQuery(it, refdataQuery, field)
      }
    }
    if (value instanceof String){
      addRefdataToQuery(value, refdataQuery, field)
    }
    refdataQuery.minimumShouldMatch(1)
    query.must(refdataQuery)
    return
  }


  private void addRefdataToQuery(String value, QueryBuilder refdataQuery, String field){
    try{
      value = RefdataValue.get(Long.valueOf(value))?.value
    }
    catch (Exception e){
    }

    if (value == 'null') {
      value = ""
    }

    refdataQuery.should(QueryBuilders.termQuery(field, value))
  }


  private void addIdentifierQuery(query, errors, qpars) {
    def id_params = [:]
    def val = null

    if (qpars.identifier) {
      val = qpars.identifier
    }
    else if (qpars.ids) {
      val = qpars.ids
    }
    else if (qpars.identifiers) {
      val = qpars.identifiers
    }

    if ( val?.trim() ) {
      if (val.contains(',')) {
        id_params['identifiers.namespace'] = val.split(',')[0]
        id_params['identifiers.value'] = sanitizeParam(val.split(',')[1])
      }
      else{
        id_params['identifiers.value'] = val
      }

      log.debug("Query ids for ${id_params}")
      query.must(QueryBuilders.nestedQuery("identifiers", addIdQueries(id_params), ScoreMode.Max))
    }
  }

  private void processNameFields(query, errors, qpars) {
    if (qpars.label) {
      def sanitized_param = sanitizeParam(qpars.label)
      QueryBuilder labelQuery = QueryBuilders.boolQuery()

      if (qpars.int('label')) {
        def oid = KBComponent.get(qpars.int('label'))?.uuid ?: null

        if (oid) {
          labelQuery.should(QueryBuilders.termQuery('uuid', oid).boost(10))
        }
      }
      else {
        labelQuery.should(QueryBuilders.termQuery('uuid', sanitized_param).boost(10))
      }

      boolean doPhraseSearch = StringUtils.countOccurrencesOf(sanitized_param, '"') == 2

      if (doPhraseSearch) {
        log.debug("DO phrase search!")
        def phraseQry = sanitized_param.replace('"', "")
        log.debug("${phraseQry}")
        labelQuery.should(QueryBuilders.matchPhraseQuery('name', phraseQry).boost(2f))
        labelQuery.should(QueryBuilders.matchPhraseQuery('altname', phraseQry))
      }
      else {
        labelQuery.should(QueryBuilders.matchQuery("name", sanitized_param).operator(Operator.AND).boost(2f))
        labelQuery.should(QueryBuilders.matchQuery("altname", sanitized_param).operator(Operator.AND).boost(1.3f))
      }

      labelQuery.minimumShouldMatch(1)

      query.must(labelQuery)
    }
    else if (qpars.name) {
      def sanitized_param = sanitizeParam(qpars.name)
      boolean doPhraseSearch = StringUtils.countOccurrencesOf(sanitized_param, '"') == 2

      if (doPhraseSearch) {
        def phraseQry = sanitized_param.replace('"', "")
        query.must(QueryBuilders.matchPhraseQuery('name', phraseQry))
      }
      else {
        query.must(QueryBuilders.matchQuery("name", sanitized_param).operator(Operator.AND))
      }
    }
    else if (qpars.altname) {
      def sanitized_param = sanitizeParam(qpars.altname)
      boolean doPhraseSearch = StringUtils.countOccurrencesOf(sanitized_param, '"') == 2

      if (doPhraseSearch) {
        def phraseQry = sanitized_param.replace('"', "")
        query.must(QueryBuilders.matchPhraseQuery('altname', phraseQry))
      }
      else {
        query.must(QueryBuilders.matchQuery('altname', sanitized_param).operator(Operator.AND))
      }
    }
    else if (qpars.suggest) {
      def sanitized_param = sanitizeParam(qpars.suggest)
      query.must(QueryBuilders.matchQuery('suggest', sanitized_param).operator(Operator.AND).boost(0.6f))
    }
    else if (qpars.qsName) {
      def sanitized_param = sanitizeParam(qpars.qsName)

      sanitized_param = sanitized_param.replaceAll("[()]", " ")

      QueryBuilder labelQuery = QueryBuilders.boolQuery()

      labelQuery.should(QueryBuilders.queryStringQuery(sanitized_param).defaultOperator(Operator.AND).field("name", 2f))
      labelQuery.should(QueryBuilders.queryStringQuery(sanitized_param).defaultOperator(Operator.AND).field("altname", 1.3f))

      labelQuery.minimumShouldMatch(1)

      query.must(labelQuery)
    }
  }

  private void processGenericFields(query, errors, qpars) {
    if (qpars.q?.trim()) {
      QueryBuilder genericQuery = QueryBuilders.boolQuery()
      def id_params = ['identifiers.value': sanitizeParam(qpars.q)]
      def sanitized_param = sanitizeParam(qpars.q)

      if (qpars.int('q')) {
        def oid = KBComponent.get(qpars.int('q'))?.uuid ?: null

        if (oid) {
          genericQuery.should(QueryBuilders.termQuery('uuid', oid).boost(10))
        }
      }
      else {
        genericQuery.should(QueryBuilders.termQuery('uuid', sanitized_param).boost(10))
      }

      if (qpars.qfields){
        List allQFields = (requestMapping.generic + requestMapping.refdata + requestMapping.simpleMap.values() +
                           requestMapping.complex)
        for (String field in qpars.list('qfields')){
          if (field == "name") {
            genericQuery.should(QueryBuilders.matchQuery("name", sanitized_param).operator(Operator.AND).boost(2f))
          }
          else if (field == "altname") {
            genericQuery.should(QueryBuilders.matchQuery("altname", sanitized_param).operator(Operator.AND).boost(1.3f))
          }
          else if (field == "suggest") {
            genericQuery.should(QueryBuilders.matchQuery("suggest", sanitized_param).operator(Operator.AND).boost(0.6f))
          }
          else if (field in allQFields){
            genericQuery.should(QueryBuilders.matchQuery(field, sanitized_param).operator(Operator.AND))
          }
        }
      }
      else{
        genericQuery.should(QueryBuilders.matchQuery("name", sanitized_param).operator(Operator.AND).boost(2f))
        genericQuery.should(QueryBuilders.matchQuery("altname", sanitized_param).operator(Operator.AND).boost(1.3f))
        genericQuery.should(QueryBuilders.matchQuery("suggest", sanitized_param).operator(Operator.AND).boost(0.6f))
        genericQuery.should(QueryBuilders.nestedQuery('identifiers', addIdQueries(id_params), ScoreMode.Max).boost(10))
      }
      genericQuery.minimumShouldMatch(1)

      query.must(genericQuery)
    }
  }

  private void processLinkedField(query, field, val) {
    def vals = val instanceof String ? [val] : val

    vals.each {
      if (it?.trim()) {
        QueryBuilder linkedFieldQuery = QueryBuilders.boolQuery()
        def sanitized_param = sanitizeParam(it)
        def finalVal = it

        try {
          finalVal = KBComponent.get(Long.valueOf(it)).getLogEntityId()
        }
        catch (java.lang.NumberFormatException nfe) {
        }

        if (finalVal == 'null') {
          finalVal = ""
        }

        log.debug("processLinkedField: ${field} -> ${finalVal}")

        linkedFieldQuery.should(QueryBuilders.termQuery(field, finalVal))
        linkedFieldQuery.should(QueryBuilders.termQuery("${field}Uuid".toString(), sanitized_param))
        linkedFieldQuery.should(QueryBuilders.termQuery("${field}Name".toString(), sanitized_param))
        linkedFieldQuery.minimumShouldMatch(1)

        query.must(linkedFieldQuery)
      }
    }
  }

  private void addPlatformQuery(query, errors, String val) {
    QueryBuilder linkedFieldQuery = QueryBuilders.boolQuery()

    if (val == 'null') {
      val = ""
    }

    linkedFieldQuery.should(QueryBuilders.termQuery('nominalPlatform', val))
    linkedFieldQuery.should(QueryBuilders.termQuery('nominalPlatformName', sanitizeParam(val)))
    linkedFieldQuery.should(QueryBuilders.termQuery('nominalPlatformUuid', sanitizeParam(val)))
    linkedFieldQuery.should(QueryBuilders.termQuery('hostPlatform', val))
    linkedFieldQuery.should(QueryBuilders.termQuery('hostPlatformName', sanitizeParam(val)))
    linkedFieldQuery.should(QueryBuilders.termQuery('hostPlatformUuid', sanitizeParam(val)))
    linkedFieldQuery.minimumShouldMatch(1)

    query.must(linkedFieldQuery)

    log.debug("Processing platform value ${val} .. ")
  }


  /**
   * scroll : Get large amounts of data from the opensearch index --
   * @param params : opensearch query params
   * @return chunks of scrollSize data sets. In case the answer's size is smaller than scrollSize,
   *         then the end of scrolling is reached.
   **/
  def scroll(params) throws Exception{
    def result = [:]
    def esClient = ESWrapperService.getClient()
    def unknown_fields = []
    def usedComponentTypes = getUsedComponentTypes(params, result)
    if (result.error){
      return result
    }
    // now search
    int scrollSize = 5000
    result.result = "OK"
    result.scrollSize = scrollSize

    def errors = [:]                              // TODO: use errors
    SearchResponse searchResponse
    if (!params.scrollId){
      QueryBuilder scrollQuery = QueryBuilders.boolQuery()
      if (params.component_type || params.componentType) {
        def final_type = deriveComponentType(params.componentType ?: params.component_type)
        scrollQuery.must(QueryBuilders.termQuery('componentType', final_type))
      }
      addDateQueries(scrollQuery, errors, params)
      specifyQueryWithParams(params, scrollQuery, errors, unknown_fields)

      SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      searchSourceBuilder.query(scrollQuery)
      searchSourceBuilder.size(scrollSize)
      SearchRequest searchRequest = new SearchRequest(usedComponentTypes.values() as String[])
      searchRequest.scroll("15m")
      searchRequest.source(searchSourceBuilder)
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT)
      result.lastPage = 0
    }
    else{
      SearchScrollRequest scrollRequest = new SearchScrollRequest(params.scrollId)
      scrollRequest.scroll("15m")
      searchResponse = esClient.scroll(scrollRequest, RequestOptions.DEFAULT)
      try{
        if (params.lastPage && Integer.valueOf(params.lastPage) > -1){
          result.lastPage = Integer.valueOf(params.lastPage) + 1
        }
      }
      catch (Exception e){
        log.debug("Could not process page information on scroll request.")
      }
    }
    result.scrollId = searchResponse.getScrollId()
    SearchHits searchHits = searchResponse.getHits()
    result.hasMoreRecords = searchHits.totalHits.value > scrollSize
    result.total = searchHits.totalHits.value
    result.records = searchHits.collect { it.getSourceAsMap() }
    result.size = result.records.size()
    result
  }

  private Map getUsedComponentTypes(params, LinkedHashMap<Object, Object> result){
    Map usedComponentTypes = new HashMap()
    def types = (params.component_type ?: params.componentType)

    if (!types){
      result.result = "ERROR"
      result.message = "Error. Needs 'component_type'/ specification."
    }

    if (types instanceof String){
      usedComponentTypes."${deriveComponentType(types)}" = null
    }
    else if (types instanceof List){
      for (def componentType in types){
        usedComponentTypes."${deriveComponentType(componentType)}" = null
      }
    }

    for (def ct in usedComponentTypes.keySet()){
      if (ct in indicesPerType.keySet()){
        usedComponentTypes."${ct}" = grailsApplication.config.gokb.es.indices[indicesPerType.get(ct)]
      }
      else{
        result.result = "ERROR"
        result.message = "Error. Wrong 'component_type' specification: ${ct}"
      }
    }

    return usedComponentTypes
  }

  /**
   * find : Query the opensearch index --
   * @param params : opensearch query params
   * @param context : Overrides default url path
   **/
  def find(params, def context = null, def user = null) {
    def result = [result: 'OK']
    def search_action = null
    def errors = [:]
    SearchResponse searchResponse = null
    log.debug("find :: ${params}")

    try {
      def unknown_fields = []
      def component_type = null
      if (params.componentType || params.component_type){
        component_type = deriveComponentType(params.componentType ?: params.component_type)
      }

      QueryBuilder exactQuery = QueryBuilders.boolQuery()

      filterByComponentType(exactQuery, component_type, params)
      addDateQueries(exactQuery, errors, params)
      processNameFields(exactQuery, errors, params)
      processGenericFields(exactQuery, errors, params)
      addIdentifierQuery(exactQuery, errors, params)
      specifyQueryWithParams(params, exactQuery, errors, unknown_fields)

      if(unknown_fields.size() > 0){
        errors['unknown_params'] = unknown_fields
      }

      if( !errors && exactQuery.hasClauses() ) {
        if (!params.status) {
          QueryBuilder statusQuery = QueryBuilders.boolQuery()
          statusQuery.mustNot(QueryBuilders.termQuery('status', 'Deleted'))
          exactQuery.must(statusQuery)
        }

        SearchRequest searchRequest = new SearchRequest(component_type ? [
          grailsApplication.config.gokb.es.indices[indicesPerType[component_type]]] as String[] :
          grailsApplication.config.gokb.es.indices.values() as String[])
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
        searchSourceBuilder.trackTotalHits(true)
        searchSourceBuilder.query(exactQuery)
        searchRequest.source(searchSourceBuilder)

        checkInt(result, errors, params.max, 'max')
        checkInt(result, errors, params.from, 'offset')
        checkInt(result, errors, params.offset, 'offset')

        if (params.max != null) {
          searchSourceBuilder.size(result.max)
        }
        else {
          result.max = 10
        }

        if (params.offset || params.from) {
          searchSourceBuilder.from(result.offset)
        }
        else {
          result.offset = 0
        }

        setSort(params, errors, searchSourceBuilder)

        if (!errors) {
          searchRequest.source(searchSourceBuilder)
          // log.debug("opensearch Query using Java Client API:\n${searchRequest.source().toString()}")
          searchResponse = ESWrapperService.getClient().search(searchRequest, RequestOptions.DEFAULT)
        }
      }
      else if ( !exactQuery.hasClauses() ){
        errors['params'] = "No valid parameters found"
      }

      if (searchResponse) {
        SearchHits hits = searchResponse.getHits()
        result.count = hits.totalHits.value
        result.records = []

        hits.each { r ->
          def response_record = [:]

          if (!params.skipDomainMapping) {
            response_record = mapEsToDomain(r, params, user)
          }
          else {
            response_record.id = r.id

            if (response_record.score && response_record.score != Float.NaN) {
              response_record.score = r.score
            }

            r.getSourceAsMap().each { field, val ->
              response_record."${field}" = val
            }
          }
          result.records.add(response_record)
        }

        if (!params.skipDomainMapping) {
          def contextPath = "/entities"

          if(context) {
            contextPath = context
          }
          else if (component_type) {
            def obj_cls = Class.forName("org.gokb.cred.${component_type}").newInstance()
            contextPath = obj_cls.restPath
          }

          convertEsLinks(result, params, contextPath)
        }
      }
    }
    catch (Exception se) {
      log.error("Error processing search request", se)
      result = [:]
      result.result = "ERROR"
      result.status = 500
      result.messageCode = 'error.search.unknown'
      result.errors = ['unknown': "There has been an unknown error processing the search request!"]
    }
    finally {
      if (errors) {
        result = [:]
        result.status = 400
        result.result = "ERROR"
        result.messageCode = 'error.search.input'
        result.errors = errors
      }
    }
    result
  }


  private void setSort(params, LinkedHashMap<Object, Object> errors, SearchSourceBuilder searchSourceBuilder){
    if (params.sort && params.sort instanceof String){
      def sortBy = params.sort

      if (sortBy == "name"){
        sortBy = "sortname"
      }
      else if (sortBy == "lastUpdated"){
        sortBy = "lastUpdatedDisplay"
      }

      if (ESWrapperService.mapping.properties[sortBy]?.type == 'text'){
        errors['sort'] = "Unable to sort by text field ${sortBy}!"
      }
      else{
        FieldSortBuilder sortQry = new FieldSortBuilder(sortBy)
        SortOrder order = SortOrder.ASC

        if (params.order){
          if (params.order.toUpperCase() in ['ASC', 'DESC']){
            order = SortOrder.valueOf(params.order?.toUpperCase())
          }
          else{
            errors['order'] = "Unknown sort order value '${params.order}'!"
          }
        }
        sortQry.order(order)
        searchSourceBuilder.sort(sortQry)
      }
    }
  }


  private void specifyQueryWithParams(params, QueryBuilder exactQuery, errors, unknown_fields){
    def platformParam = null
    params.each{ k, v ->
      if (requestMapping.generic && k in requestMapping.generic){
        def final_val = v
        if (k == "importId") {
          exactQuery.must(QueryBuilders.termQuery(k, final_val))
        }
        else {
          if (k == 'id' && params.int('id')) {
            final_val = KBComponent.get(params.int('id'))?.getLogEntityId()
          }
          exactQuery.must(QueryBuilders.termQuery(k, final_val))
        }
      }
      else if (requestMapping.simpleMap?.containsKey(k)){
        exactQuery.must(QueryBuilders.matchQuery(requestMapping.simpleMap[k], v).operator(Operator.AND))
      }
      else if (requestMapping.linked?.containsKey(k)){
        processLinkedField(exactQuery, requestMapping.linked[k], v)
      }
      else if (requestMapping.refdata?.contains(k)) {
        addRefdataQuery(exactQuery, errors, k, v)
      }
      else if (k.contains('platform') || k.contains('Platform')){

        if (!platformParam){
          def final_val = v
          platformParam = k

          if (params.int(k)) {
            final_val = 'org.gokb.cred.Platform:' + v
          }

          addPlatformQuery(exactQuery, errors, final_val)
        }
        else{
          errors[k] = "Platform filter has already been defined by parameter '${platformParam}'!"
        }
      }
      else if (k.contains('curatoryGroup')) {
        def cg_name = v

        if (params.int(k)) {
          def cg_by_id = CuratoryGroup.get(params.int(k))

          if (cg_by_id) {
            cg_name = cg_by_id.name
          }
        }
        exactQuery.must(QueryBuilders.termQuery('curatoryGroups', cg_name))
      }
      else if (requestMapping.dates && k in requestMapping.dates){
        log.debug("Processing date param ${k}")
      }
      else if (requestMapping.complex && k in requestMapping.complex){
        log.debug("Processing complex param ${k}")
      }
      else if (requestMapping.ignore && k in requestMapping.ignore){
        log.debug("Processing unmapped param ${k}")
      }
      else{
        unknown_fields.add(k)
      }
    }
  }


  private void filterByComponentType(BoolQueryBuilder exactQuery, component_type, params){
    if (params.componentType){
      if (component_type == "TitleInstance"){
        QueryBuilder typeQuery = QueryBuilders.boolQuery()
        typeQuery.should(QueryBuilders.termQuery('componentType', "JournalInstance"))
        typeQuery.should(QueryBuilders.termQuery('componentType', "DatabaseInstance"))
        typeQuery.should(QueryBuilders.termQuery('componentType', "BookInstance"))
        typeQuery.should(QueryBuilders.termQuery('componentType', "OtherInstance"))
        typeQuery.minimumShouldMatch(1)
        exactQuery.must(typeQuery)
      }
      else if (component_type){
        exactQuery.must(QueryBuilders.termQuery('componentType', component_type))
      }
      log.debug("Using component type ${component_type}")
    }
    else{
      log.debug("Not filtering by component type..")
    }
  }


  /**
   *  mapEsToDomain : Maps an ES record to its final REST serialization.
   * @param record : An ES record map
   * @param params : Request params
   */

  private Map mapEsToDomain(record, params, def user = null) {
    def domainMapping = [:]
    def base = grailsApplication.config.serverURL + "/rest"
    def linkedObjects = [:]
    def embed_active = params['_embed']?.split(',') ?: []
    def include_list = params['_include']?.split(',') ?: null
    def exclude_list = params['_exclude']?.split(',') ?: null
    Integer rec_id = genericOIDService.oidToId(record.id)
    def esMapping = [
        'lastUpdatedDisplay': 'lastUpdated',
        'sortname': false,
        'updater': false,
        'identifiers': false,
        'altname': false,
        'roles': false,
        'curatoryGroups': false,
        'publisher': false
    ]

    def recordSource = record.getSourceAsMap()
    def obj_cls = Class.forName("org.gokb.cred.${recordSource.componentType}").newInstance()

    if (obj_cls) {

      if (obj_cls.hasProperty('jsonMapping') && obj_cls.jsonMapping.es) {
        esMapping << obj_cls.jsonMapping.es
        log.debug("Found es mapping for class.")
      }
      else {
        log.debug("No es mapping found for class ${obj_cls} ..")
      }

      if (obj_cls.hasProperty('restPath')) {
        domainMapping['_links'] = [
          'self': ['href': base + obj_cls.restPath + "/${rec_id}"]
        ]

        def is_curator = true

        if (user && recordSource.curatoryGroups?.size() > 0) {
          is_curator = user?.curatoryGroups?.name.intersect(recordSource.curatoryGroups)
        }

        def href = (user?.hasRole('ROLE_EDITOR') && is_curator) || user?.isAdmin() ? base + obj_cls.restPath + "/${rec_id}" : null
        domainMapping['_links']['update'] = (href ? ['href': href] : null)
        domainMapping['_links']['delete'] = (href ? ['href': href + "/delete"] : null)
      }

      domainMapping['_embedded'] = [:]

      domainMapping['id'] = rec_id

      domainMapping['type'] = obj_cls.niceName

      recordSource.each { field, val ->
        def toSkip = (include_list && !include_list.contains(field)) || (exclude_list?.contains(field))

        if (field == "curatoryGroups" && !toSkip) {
          mapCuratoryGroups(domainMapping, val)
        }
        else if (field == "altname" && !toSkip) {
          domainMapping['_embedded']['variantNames'] = val
        }
        else if (field == "identifiers" && !toSkip) {
          domainMapping['_embedded']['ids'] = mapIdentifiers(val)
        }
        else if (field == "publisherUuid") {
          domainMapping['_embedded']['publisher'] = []
          if (val) {
            domainMapping['_embedded']['publisher'] << [uuid: recordSource['publisherUuid'], name: recordSource['publisherName'], id: recordSource['publisher'].split(':')[1]]
          }
        }
        else if (!toSkip && (field == "status" || field == "editStatus")) {
          domainMapping[field] = [id: RefdataCategory.lookup("KBComponent.${field}", val).id, name: val]
        }
        else if (esMapping[field] == false) {
          log.debug("Skipping field ${field}!")
        }
        else if (esMapping[field] == "refdata" && !toSkip) {
          if (val) {
            def cat = classExaminationService.deriveCategoryForProperty("org.gokb.cred.${recordSource.componentType}", field)
            def rdv = RefdataCategory.lookup(cat, val)
            domainMapping[field] = [id: rdv.id, name:rdv.value]
          }
          else {
            domainMapping[field] = null
          }
        }
        else if ( esMapping[field] && !toSkip ) {
          log.debug("Field ${esMapping[field]}")
          def fieldPath = esMapping[field].split("\\.")
          def isNull = false
          log.debug("FieldPath: ${fieldPath}")

          if (fieldPath.size() == 2) {
            if (!linkedObjects[fieldPath[0]]) {
              linkedObjects[fieldPath[0]] = [:]
            }
            if (fieldPath[1] == 'id') {
              def id_val = genericOIDService.oidToId(val)

              if (id_val) {
                linkedObjects[fieldPath[0]][fieldPath[1]] = genericOIDService.oidToId(val)
              }
              else {
                isNull = true
              }
            }
            else {
              linkedObjects[fieldPath[0]][fieldPath[1]] = val
            }
          }
          else {
            domainMapping[fieldPath[0]] = val
          }
        }
        else if (!toSkip) {
          log.debug("Transfering unmapped field ${field}:${val}")
          if (val) {
            domainMapping[field] =  val
          }
          else {
            domainMapping[field] = null
          }
        }
      }

      linkedObjects.each { field, val ->
        if (val.id) {
          domainMapping[field] = val
        }
        else {
          domainMapping[field] = null
        }
      }
    }

    log.debug("${domainMapping}")
    return domainMapping
  }

  /**
   *  convertEsLinks : Converts opensearch response layout to conform with REST mapping.
   * @param es_result : The result object
   * @param params : Request parameters
   * @param component_endpoint : Possible URL path override
   */

  private def convertEsLinks(es_result, params, component_endpoint) {
    def base = grailsApplication.config.serverURL + "/rest" + "${component_endpoint}"

    es_result['_links'] = [:]
    es_result['data'] = es_result.records
    es_result.remove('records')
    es_result.remove('result')
    es_result['_pagination'] = [
        offset: es_result.offset,
        limit: es_result.max,
        total: es_result.count
    ]

    def selfLink = new URIBuilder(base)
    selfLink.addQueryParams(params)

    params.each { p, vals ->
      log.debug("handling param ${p}: ${vals}")
      if (vals instanceof String[]) {
        selfLink.removeQueryParam(p)
        vals.each { val ->
          if (val?.trim()) {
            log.debug("Val: ${val} -- ${val.class.name}")
            selfLink.addQueryParam(p, val)
          }
        }
        log.debug("${selfLink.toString()}")
      }
    }
    if(params.controller) {
      selfLink.removeQueryParam('controller')
    }
    if (params.action) {
      selfLink.removeQueryParam('action')
    }
    if (params.componentType) {
      selfLink.removeQueryParam('componentType')
    }
    es_result['_links']['self'] = [href: selfLink.toString()]


    if (es_result.count > es_result.offset+es_result.max) {
      def nextLink = selfLink

      if(nextLink.query.offset){
        nextLink.removeQueryParam('offset')
      }

      nextLink.addQueryParam('offset', "${es_result.offset + es_result.max}")
      es_result['_links']['next'] = ['href': (nextLink.toString())]
    }
    if (es_result.offset > 0) {
      def prevLink = selfLink

      if(prevLink.query.offset){
        prevLink.removeQueryParam('offset')
      }

      prevLink.addQueryParam('offset', "${(es_result.offset - es_result.max) > 0 ? es_result.offset - es_result.max : 0}")
      es_result['_links']['prev'] = ['href': prevLink.toString()]
    }
    es_result.remove("offset")
    es_result.remove("max")
    es_result.remove("count")

    es_result
  }

  private def mapIdentifiers(ids) {
    def idmap = []
    ids.each { id ->
      def ns = IdentifierNamespace.findByValueIlike(id.namespace)

      if (ns) {
        idmap << [ namespace : [value: id.namespace, name: ns.name, id: ns.id], value: id.value ]
      }
    }
    idmap
  }

  /**
   *  mapCuratoryGroups : Generates an embed object for curatoryGroups listed in ES.
   * @param domainMapping : The current domainMapping object
   * @param cgs : The array of names of connected curatory groups
   */

  private def mapCuratoryGroups(domainMapping, cgs) {
    def base = grailsApplication.config.serverURL + "/rest"

    domainMapping['_embedded']['curatoryGroups'] = []
    cgs.each { cg ->
      def cg_obj = CuratoryGroup.findByName(cg)
      if (cg_obj){
        domainMapping['_embedded']['curatoryGroups'] << [
            'links': [
                'self': [
                    'href': base + cg_obj.getRestPath()+"/" + cg_obj.uuid
                ]
            ],
            'name': cg_obj.name,
            'id': cg_obj.id,
            'uuid': cg_obj.uuid
        ]
      }
    }
  }

  /**
   *  deriveComponentType : Selects the actual componentType of a ES record.
   * @param typeString : The componentType parameter of the request
   */

  private def deriveComponentType(String typeString) {
    def result = null
    def defined_types = [
        "Package",
        "Org",
        "JournalInstance",
        "Journal",
        "Serial",
        "BookInstance",
        "Book",
        "DatabaseInstance",
        "Database",
        "Platform",
        "TitleInstancePackagePlatform",
        "TIPP",
        "TitleInstance",
        "Title",
        "OtherInstance",
        "Other"
    ]
    def final_type = typeString.capitalize()

    if (final_type in defined_types) {
      if (final_type == 'TIPP') {
        final_type = 'TitleInstancePackagePlatform'
      }
      else if (final_type == 'Book') {
        final_type = 'BookInstance'
      }
      else if (final_type == 'Journal' || final_type == 'Serial') {
        final_type = 'JournalInstance'
      }
      else if (final_type == 'Database') {
        final_type = 'DatabaseInstance'
      }
      else if (final_type == 'Title') {
        final_type = 'TitleInstance'
      }
      else if (final_type == 'Other') {
        final_type = 'OtherInstance'
      }

      result = final_type
    }
    result
  }

  private def addIdQueries(params) {

    QueryBuilder idQuery = QueryBuilders.boolQuery()

    params.each { k,v ->
      if (v.contains("*")){
        idQuery.must(QueryBuilders.wildcardQuery(k, v))
      }
      else{
        idQuery.must(QueryBuilders.termQuery(k, v))
      }
    }

    return idQuery
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
  }

  /**
   * Tunnels the full query string to opensearch and returns the full opensearch result. Only GET operations
   * are possible. The purpose of this endpoint is not to need to open the opensearch port (usually 9200) for the
   * outside world, in order to prevent non-GET operations.
   * @param params The params necessary for this operation.
   * @param params.q The query string for opensearch
   * @param params.size Optional parameter: The maximum size of the result.
   * @return The exact Json response of the opensearch GET operation.
   * @throws Exception Any exception occuring.
   */
  def getApiTunnel(def params) throws Exception{
    if (!params || !params.q){
      return null
    }
    int port = grailsApplication.config.searchApi.port
    def indices = grailsApplication?.config?.gokb?.es?.indices?.values()
    String host = grailsApplication?.config?.gokb?.es?.host
    String url = "http://${host}:${port}/${indices.join(',')}/_search?q=${params.q}"
    if (params.size){
      url = url + "&size=${params.size}"
    }
    HTTPBuilder httpBuilder = new HTTPBuilder(url)
    httpBuilder.request(Method.GET){ req ->
      response.success = { resp, html ->
        return html
      }
      response.failure = { resp ->
        return [
            'error': "Could not process opensearch request.",
            'status': resp.statusLine,
            'message': resp.message
        ]
      }
    }
  }
}
