package com.k_int

import grails.core.GrailsClass

import groovyx.net.http.URIBuilder

import org.apache.lucene.search.join.ScoreMode

import org.elasticsearch.action.search.*
import org.elasticsearch.client.*
import org.elasticsearch.index.query.*
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.sort.*

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
  def restMappingService
  def classExaminationService

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
      "componentType",
      "platform",
      "suggest",
      "label",
      "name",
      "altname",
      "q"
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
    ],
    ignore: [
      "controller",
      "action",
      "max",
      "offset",
      "from",
      "skipDomainMapping",
      "sort",
      "order"
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
    if ( qpars.status && qpars.status instanceof List ) {

      QueryBuilder statusQuery = QueryBuilders.boolQuery()

      qpars.status.each {
        def ref_status = it

        try {
          ref_status = RefdataValue.get(Long.valueOf(it))
        }
        catch (Exception e) {
        }
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
    else if (qpars.q) {
      query.must(QueryBuilders.matchQuery('name',qpars.q))
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
    def finalVal = val

    try {
      finalVal = KBComponent.get(Long.valueOf(val)).getLogEntityId()
    }
    catch (java.lang.NumberFormatException nfe) {
    }

    log.debug("processLinkedField: ${field} -> ${finalVal}")

    linkedFieldQuery.should(QueryBuilders.termQuery(field, finalVal))
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
   * @param params : Elasticsearch query params
   * @param context : Overrides default url path
  **/

  def find(params, def context = null, def user = null) {
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
      def platformParam = null
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
      processNameFields(exactQuery, errors, params)

      params.each { k, v ->
        if (requestMapping.generic && k in requestMapping.generic) {
          exactQuery.must(QueryBuilders.matchQuery(k, v))
        }
        else if (requestMapping.simpleMap?.containsKey(k)) {
          exactQuery.must(QueryBuilders.matchQuery(requestMapping.simpleMap[k], v))
        }
        else if (requestMapping.linked?.containsKey(k)) {
          processLinkedField(exactQuery, requestMapping.linked[k], v)
        }
        else if (k.contains('platform') || k.contains('Platform')) {
          if (!platformParam) {
            platformParam = k
            addPlatformQuery(exactQuery, errors, v)
          }
          else {
            errors[k] = "Platform filter has already been defined by parameter '${platformParam}'!"
          }
        }
        else if (requestMapping.dates && k in requestMapping.dates) {
          log.debug("Processing date param ${k}")
        }
        else if (requestMapping.complex && k in requestMapping.complex) {
          log.debug("Processing complex param ${k}")
        }
        else if (requestMapping.ignore && k in requestMapping.ignore) {
          log.debug("Processing unmapped param ${k}")
        }
        else {
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

        if (params.sort && params.sort instanceof String) {
          def sortBy = params.sort

          if (sortBy == "name") {
            sortBy = "sortname"
          }

          if (ESWrapperService.mapping.component.properties[sortBy]?.type == 'text') {
            errors['sort'] = "Unable to sort by text field ${sortBy}!"
          }
          else {
            FieldSortBuilder sortQry = new FieldSortBuilder(sortBy)
            SortOrder order = SortOrder.ASC

            if (params.order) {
              if (params.order.toUpperCase() in ['ASC','DESC']) {
                order = SortOrder.valueOf(params.order?.toUpperCase())
              }
              else {
                errors['order'] = "Unknown sort order value '${params.order}'!"
              }
            }

            sortQry.order(order)

            es_request.addSort(sortQry)
          }
        }

        if (!errors) {
          search_action = es_request.execute()
        }
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

        if (!params.skipDomainMapping) {
          def contextPath = "/components"

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
    } catch (Exception se) {
      log.error("${se}")
      result = [:]
      result.result = "ERROR"
      result.errors = ['unknown': "There has been an unknown error processing the search request!"]
    } finally {
      if (errors) {
        result = [:]
        result.result = "ERROR"
        result.errors = errors
      }
    }

    result
  }

  /**
   *  mapEsToDomain : Maps an ES record to its final REST serialization.
   * @param record : An ES record map
   */

  private Map mapEsToDomain(record) {
    def domainMapping = [:]
    def base = grailsApplication.config.serverURL + "/rest"
    def linkedObjects = [:]
    Integer rec_id = genericOIDService.oidToId(record.id)
    def esMapping = [
      'lastUpdatedDisplay': 'lastUpdated',
      'sortname': false,
      'updater': false,
    ]

    def obj_cls = Class.forName("org.gokb.cred.${record.source.componentType}").newInstance()

    if (obj_cls) {

      if (obj_cls.hasProperty('jsonMapping') && obj_cls.jsonMapping.es) {
        esMapping << obj_cls.jsonMapping.es
        log.debug("Found es mapping for class.")
      }
      else {
        log.debug("No es mapping found for class ${obj_cls} ..")
      }

      if (obj_cls.hasProperty('restPath')) {
        domainMapping['_links'] = ['self': ['href': base + obj_cls.restPath + "/${rec_id}"]]
      }

      domainMapping['_embedded'] = [:]

      record.source.each { field, val ->
        if (field == "curatoryGroups") {
          mapCuratoryGroups(domainMapping, val)
        }
        else if (field == "altname") {
          domainMapping['_embedded']['variantNames'] = val
        }
        else if (field == "identifiers") {
          domainMapping['_embedded']['ids'] = mapIdentifiers(val)
        }
        else if (field == "status" || field == "editStatus") {
          domainMapping[field] = [id: RefdataCategory.lookup("KBComponent.${field}", val).id, name: val]
        }
        else if (esMapping[field] == false) {
          log.debug("Skipping field ${field}!")
        }
        else if (esMapping[field] == "refdata") {
          if (val) {
            def cat = classExaminationService.deriveCategoryForProperty("org.gokb.cred.${record.source.componentType}", field)
            def rdv = RefdataCategory.lookup(cat, val)
            domainMapping[field] = [id: rdv.id, name:rdv.value]
          }
          else {
            domainMapping[field] = null
          }
        }
        else if (esMapping[field]) {
          log.debug("Field ${esMapping[field]}")
          def fieldPath = esMapping[field].split("\\.")
          log.debug("FieldPath: ${fieldPath}")

          if (fieldPath.size() == 2) {
            if (!linkedObjects[fieldPath[0]]) {
              linkedObjects[fieldPath[0]] = [:]
            }
            if (fieldPath[1] == 'id') {
              linkedObjects[fieldPath[0]][fieldPath[1]] = genericOIDService.oidToId(val)
            }
            else {
              linkedObjects[fieldPath[0]][fieldPath[1]] = val
            }
          } else {
            domainMapping[fieldPath[0]] = val
          }
        }
        else {
          log.debug("Transfering unmapped field ${field}:${val}")
          domainMapping[field] = val
        }
      }

      linkedObjects.each { field, val ->
        domainMapping[field] = val
      }
    }

    log.debug("${domainMapping}")
    return domainMapping
  }

  /**
   *  convertEsLinks : Converts es response layout to conform with REST mapping.
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
          if (val.trim()) {
            log.debug("Val: ${val} -- ${val.class.name}")
            selfLink.addQueryParam(p, val)
          }
        }
        log.debug("${selfLink.toString()}")
      }
      else if (!p.trim()) {
        selfLink.removeQueryParam(p)
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
      idmap << [namespace : [value: id.namespace, id: ns.id], value: id.value ]
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
