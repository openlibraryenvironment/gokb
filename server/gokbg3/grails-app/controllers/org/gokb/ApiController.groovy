package org.gokb

import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job
import com.k_int.ExtendedHibernateDetachedCriteria
import com.k_int.TextUtils
import com.k_int.TsvSuperlifterService
import grails.converters.JSON
import grails.util.GrailsNameUtils
import grails.util.Holders
import groovy.util.logging.*
import org.elasticsearch.action.search.*
import org.elasticsearch.index.query.*
import org.elasticsearch.search.sort.*
import org.gokb.cred.*
import org.gokb.refine.RefineOperation
import org.gokb.refine.RefineProject
import org.hibernate.criterion.CriteriaSpecification
import org.hibernate.criterion.Subqueries
import org.springframework.security.access.annotation.Secured
import org.springframework.web.multipart.MultipartHttpServletRequest
import org.apache.lucene.search.join.ScoreMode

import java.security.SecureRandom

import static java.util.UUID.randomUUID
/**
 * TODO: Change methods to abide by the RESTful API, and implement GET, POST, PUT and DELETE with proper response codes.
 *
 * @author Steve Osguthorpe
 */
@Slf4j
class ApiController {

  TsvSuperlifterService tsvSuperlifterService
  SecureRandom rand = new SecureRandom()
  UploadAnalysisService uploadAnalysisService
  def ESWrapperService

  static def reversemap = ['subject':'subjectKw','componentType':'componentType','identifier':'identifiers.value']
  static def non_analyzed_fields = ['componentType','identifiers.value']

  private static final Closure TRANSFORMER_USER = {User u ->
    [
      "id"      : "${u.id}",
      "email"     : "${u.email}",
      "displayName"   : "${u.displayName ?: u.username}"
    ]
  }

  private static final Closure TRANSFORMER_PROJECT = {

    // Treat as refine project.
    RefineProject proj = it as RefineProject

    // Populate the map manually instead of excluding more and more.
    TreeMap props = [
      "id"                : proj.id,
      "localProjectID"    : proj.localProjectID,
      "name"              : proj.name,
      "description"       : proj.description,
      "projectStatus"     : proj.projectStatus,
      "lastCheckedOutBy"  : ApiController.TRANSFORMER_USER (proj.lastCheckedOutBy),
      "progress"          : proj.progress,
      "modified"          : proj.modified,
      "createdBy"         : ApiController.TRANSFORMER_USER (proj.createdBy),
    ]

    return props
  }

  def springSecurityService
  def componentLookupService
  def genericOIDService
  ConcurrencyManagerService concurrencyManagerService

  /**
   * Check if the api is up. Just return true.
   */
  def isUp() {
    apiReturn(["isUp" : true])
  }

  // Internal API return object that ensures consistent formatting of API return objects
  private def apiReturn = {result, String message = "", String status = (result instanceof Throwable) ? "error" : "success" ->

    // If the status is error then we should log an entry.
    if (status == 'error') {

      // Generate 6bytes of random data to be base64 encoded which can be returned to the user to help with tracking issues in the logs.
      byte[] randomBytes = new byte[6]
      rand.nextBytes(randomBytes)
      def ticket = Base64.encodeBase64String(randomBytes);

      // Let's see if we have a throwable.
      if (result && result instanceof Throwable) {

        // Log the error with the stack...
        log.error("[[${ticket}]] - ${message == "" ? result.getLocalizedMessage() : message}", result)
      } else {
        log.error("[[${ticket}]] - ${message == "" ? 'An error occured, but no message or exception was supplied. Check preceding log entries.' : message}")
      }

      // Ensure we have something to send back to the user.
      if (message == "") {
        message = "An unknow error occurred."
      } else {

        // We should now send the message along with the ticket.
        message = "${message}".replaceFirst("\\.\\s*\$", ". The error has been logged with the reference '${ticket}'")
      }
    }

    def data = [
      code    : (status),
      result    : (result),
      message    : (message),
    ]

    def json = data as JSON
    //  log.debug (json)
    render json
    //    render (text: "${params.callback}(${json})", contentType: "application/javascript", encoding: "UTF-8")
  }

  def index() {
  }

  @Secured(['IS_AUTHENTICATED_FULLY'])
  def checkLogin() {
    apiReturn(["login": true])
  }

  def userData() {
    if (!springSecurityService.currentUser) {
      return
    }
    apiReturn ( TRANSFORMER_USER( springSecurityService.currentUser ) )
  }

  def refdata() {
    def result = [:];

    // Should take a type parameter and do the right thing. Initially only do one type
    switch ( params.type ) {
      case 'cp' :
        def oq = Org.createCriteria()
        def orgs = oq.listDistinct {
          roles {
            "owner" {
              eq('desc','Org.Role');
            }
            eq('value','Content Provider');
          }
          order("name", "asc")
        }
        result.datalist=new java.util.ArrayList()
        orgs.each { o ->
          result.datalist.add([ "value" : "${o.id}", "name" : (o.name) ])
        }
        break;

      case 'org' :
        def oq = Org.createCriteria()
        def orgs = oq.listDistinct {
          order("name", "asc")
        }
        result.datalist=new java.util.ArrayList()
        orgs.each { o ->
          result.datalist.add([ "value" : "${o.id}", "name" : (o.name) ])
        }
        break;
      default:
        break;
    }
    apiReturn(result)
  }

  def namespaces() {

    def result = []
    def all_ns = null

    if (params.category && params.category?.trim().size() > 0) {
      all_ns = IdentifierNamespace.findAllByFamily(params.category)
    }
    else {
      all_ns = IdentifierNamespace.findAll()
    }

    all_ns.each { ns ->
      result.add([value: ns.value, category: ns.family ?: ""])
    }

    apiReturn(result)
  }

  @Secured(['ROLE_SUPERUSER', 'ROLE_REFINEUSER', 'IS_AUTHENTICATED_FULLY'])
  def quickCreate() {
    // Get the type of component we are going to attempt to create.
    def type = params.qq_type

    try {
      Class<? extends KBComponent> c = grailsApplication.getClassLoader().loadClass(
        "org.gokb.cred.${GrailsNameUtils.getClassNameRepresentation(type)}"
      )

      // Try and create a new instance passing in the supplied parameters.
      def comp = c.newInstance()

      // Set all the parameters passed in.
      params.each { prop, value ->
        // Only set the property if we have a value.
        if (value != null && value != "") {
          try {

            // We may get a component ID here now. Just run it through the component
            // lookup service. If it isn't the correct format it will return quickly.
            KBComponent com = componentLookupService.lookupComponent(value)
            if (com) {
              // Set to the component value.
              comp."${prop}" = com
            } else {
              comp."${prop}" = value
            }

          } catch (Throwable t) {
            /* Suppress the error */
          }
        }
      }

      switch (c) {

        case Package :

          // We may also need to create a review request against Packages created here.
          if ( !comp.provider ) {
            ReviewRequest.raise (
              comp,
              "Review and set provider of this package.",
              "Package created in refine without a provider.",
              springSecurityService.currentUser
            )
          }
          break;
      }

      // Save.
      comp.save(failOnError: true)

      // Now that the object has been saved we need to return the string.
      apiReturn("${comp.name}::{${c.getSimpleName()}:${comp.id}}")

    } catch (Throwable t) {
      apiReturn (t, "There was an error creating a new Component of ${type}")
    }
  }

  // this is used as an entrypoint for single page apps based on frameworks like angular.
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def search() {
    def result = [:]

    User user = springSecurityService.currentUser

    log.debug("Entering SearchController:index");

    result.max = params.max ? Integer.parseInt(params.max) : ( user.defaultPageSize ?: 10 );
    result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

    if ( request.JSON ) {

        result.qbetemplate = request.JSON.cfg

        // Looked up a template from somewhere, see if we can execute a search
        if ( result.qbetemplate ) {
          log.debug("Execute query");
          def qresult = [max:result.max, offset:result.offset]
          result.rows = doQuery(result.qbetemplate, params, qresult)
          log.debug("Query complete");
          result.lasthit = result.offset + result.max > qresult.reccount ? qresult.reccount : ( result.offset + result.max )

          // Add the page information.
          result.page_current = (result.offset / result.max) + 1
          result.page_total = (qresult.reccount / result.max).toInteger() + (qresult.reccount % result.max > 0 ? 1 : 0)
        }
        else {
          log.error("no template ${result?.qbetemplate}");
        }
    }
    else {
      log.debug("No request json");
    }

    render result as JSON
  }

  /**
   * suggest : Get a list of autocomplete suggestions from ES
   *
   * @param max : Define result size
   * @param offset : Define offset
   * @param from : Define offset
   * @param q : Search term
   * @param componentType : Restrict search to specific component type (Package, Org, Platform, BookInstance, JournalInstance, TIPP)
   * @param role : Filter by Org role (only in context of componentType=Org)
   * @return JSON Object
  **/

  def suggest() {
    def result = [:]
    def esclient = ESWrapperService.getClient()
    def acceptedStatus = []
    def component_type = null
    def errors = [:]
    def offsetDefault = 0
    def maxDefault = 10

    try {

      if ( params.q?.size() > 0 ) {

        QueryBuilder suggestQuery = QueryBuilders.boolQuery()

        suggestQuery.must(QueryBuilders.matchQuery('suggest', params.q))

        if ( params.componentType ) {
          component_type = deriveComponentType(params.componentType)

          if(component_type) {

            if (component_type == "TitleInstance") {
              QueryBuilder typeQuery = QueryBuilders.boolQuery()

              typeQuery.should(QueryBuilders.matchQuery('componentType', "JournalInstance"))
              typeQuery.should(QueryBuilders.matchQuery('componentType', "DatabaseInstance"))
              typeQuery.should(QueryBuilders.matchQuery('componentType', "BookInstance"))

              typeQuery.minimumNumberShouldMatch(1)

              exactQuery.must(typeQuery)
            }
            else {
              suggestQuery.must(QueryBuilders.matchQuery('componentType', component_type))
            }

            if( component_type == 'Org' && params.role ) {
              suggestQuery.must(QueryBuilders.matchQuery('roles', params.role))
            }
          }
          else {
            errors['componentType'] = "Requested component type ${v} does not exist"
          }
        }

        if ( params.status ) {
          acceptedStatus = params.list('status')
        }

        if ( acceptedStatus.size() > 0 ) {

          QueryBuilder statusQuery = QueryBuilders.boolQuery()

          acceptedStatus.each {
            statusQuery.should(QueryBuilders.matchQuery('status', it))
          }

          statusQuery.minimumNumberShouldMatch(1)

          suggestQuery.must(statusQuery)
        }

        else {
          suggestQuery.must(QueryBuilders.matchQuery('status', 'Current'))
        }

        SearchRequestBuilder es_request =  esclient.prepareSearch("suggest")

        es_request.setIndices(grailsApplication.config.globalSearch.indices)
        es_request.setTypes(grailsApplication.config.globalSearch.types)
        es_request.setQuery(suggestQuery)

        setQueryMax(errors, result, 10)
        setQueryFrom(errors, result, 0)
        setQueryOffset(errors, result, 0)
        if (result.offset != offsetDefault){
          es_request.setFrom(result.offset)
        }
        if (result.max != maxDefault){
          es_request.setSize(result.max)
        }

        def search = es_request.execute().actionGet()

        if (search.hits.maxScore == Float.NaN) { //we cannot parse NaN to json so set to zero...
          search.hits.maxScore = 0
        }

        result.count = search.hits.totalHits
        result.records = []

        search.hits.each { r ->
          def response_record = [:]
          response_record.id = r.id
          response_record.score = r.score

          r.source.each { field, val ->
            response_record."${field}" = val
          }

          result.records.add(response_record)
        }

      }
      else{
        errors['fatal'] = "No query parameter 'q=' provided"
      }

    }finally {
      if (errors) {
        response.status = 400
        result = [:]
        result.errors = errors
      }
    }

    render result as JSON
  }

  /*
   * Alternate method to setQueryFrom, reading from params.offset instead of from params.from
   */
  private void setQueryOffset(LinkedHashMap errors, LinkedHashMap result, def defaultValue) {
    setQueryParameterAsInt('offset', 'offset', defaultValue, errors, result)
  }

  /*
   * Alternate method to setQueryOffset, reading from params.from instead of from params.offset
   */
  private void setQueryFrom(LinkedHashMap errors, LinkedHashMap result, def defaultValue) {
    setQueryParameterAsInt('from', 'offset', defaultValue, errors, result)
  }

  private void setQueryMax(LinkedHashMap errors, LinkedHashMap result, def defaultValue) {
    setQueryParameterAsInt('max', 'max', defaultValue, errors, result)
  }

  private void setQueryParameterAsInt(def param, def resultField, def defaultValue,
                                      LinkedHashMap errors, LinkedHashMap result){
    Integer value = convertToInt(param, errors)
    if (value != null) {
      result."$resultField" = value
    }
    else if (defaultValue != null) {
      result."$resultField" = defaultValue
    }
  }

  private Integer convertToInt(def param, LinkedHashMap errors){
    if (params."$param") {
      def value = null
      try {
        value = params."$param" as Integer
        return value
      } catch (all) {
        errors."$param" = "Could not convert ${params."$param"} to Int."
      }
    }
    return null
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

  def find() {
    def result = [:]
    def esclient = ESWrapperService.getClient()
    def search_action = null
    def errors = [:]


    try {

      QueryBuilder exactQuery = QueryBuilders.boolQuery()

      def singleParams = [:]
      def linkedFieldParams = [:]
      def unknown_fields = []
      def other_fields = ["controller","action","max","offset","from"]
      def id_params = [:]
      def orgRoleParam = ""
      def providerParam = ""
      def hostPlatformId = null
      def nominalPlatformId = null
      def genericPlatformId = null
      def pkgListStatus = ""
      def pkgNameSort = false
      def acceptedStatus = []
      def component_type = null

      params.each { k, v ->
        if ( k == 'componentType' && v instanceof String ) {

          component_type = deriveComponentType(v)

          if(!component_type) {
            errors['componentType'] = "Requested component type ${v} does not exist"
          }
        }

        else if (params.componentType == 'Package' && k == 'sort' && v == 'name') {
          pkgNameSort = true
        }

        else if (k == 'role' && v instanceof String ) {
          orgRoleParam = v
        }

        else if ( (k == 'publisher' || k == 'currentPublisher') && v instanceof String) {
          linkedFieldParams['publisher'] = v
        }

        else if ( k == 'cpname' && v instanceof String) {
          singleParams['cpname'] = v
        }

        else if ( k == 'provider' && v instanceof String) {
          linkedFieldParams['provider'] = v
        }

        else if ( (k == 'linkedPackage' || k == 'tippPackage') && v instanceof String) {
          linkedFieldParams['tippPackage'] = v
        }

        else if (k == 'hostPlatform' && v instanceof String) {
          hostPlatformId = v
        }

        else if (k == 'nominalPlatform' && v instanceof String) {
          nominalPlatformId = v
        }

        else if (k == 'platform' && v instanceof String) {
          genericPlatformId = v
        }

        else if (k == 'listStatus' && v instanceof String) {
          pkgListStatus = v
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

        else if ( k == "id" && v instanceof String) {
          singleParams['_id'] = v
        }

        else if ( k == "uuid" && v instanceof String ) {
          singleParams['uuid'] = v
        }

        else if (!other_fields.contains(k)){
          unknown_fields.add(k)
        }
      }

      if(unknown_fields.size() > 0){
        errors['unknown_params'] = unknown_fields
      }

      if ( pkgListStatus ) {
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

      if ( orgRoleParam ) {
        if ( component_type && component_type == 'Org') {
          singleParams['roles'] = orgRoleParam
        }
        else {
          errors['role'] = "To filter by Org Roles, please add filter componentType=Org to the query"
        }
      }

      if ( hostPlatformId ) {
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

      if ( nominalPlatformId ) {
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

      if ( genericPlatformId ) {
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

        es_request.setIndices(grailsApplication.config.globalSearch.indices)
        es_request.setTypes(grailsApplication.config.globalSearch.types)
        es_request.setQuery(exactQuery)

        setQueryMax(errors, result, null)
        setQueryFrom(errors, result, null)
        setQueryOffset(errors, result, null)

        if (result.max) {
          es_request.setSize(result.max)
        }
        if (result.offset) {
          es_request.setFrom(result.offset)
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

    }finally {
      if (errors) {
        result = [:]
        response.status = 400
        result.errors = errors
      }
    }

    render result as JSON
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

  private def buildQuery(params) {

    StringWriter sw = new StringWriter()

    if ( params?.q != null )
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

  /**
   * show : Returns a simplified JSON serialization of a domain class object
   * @param oid : The OID ("<FullyQualifiedClassName>:<PrimaryKey>") of the object
   * @param withCombos : Also return combo properties for KBComponents
  **/

  def show() {
    def result = ['result':'OK', 'params': params]
    if (params.oid || params.id) {
      def obj = genericOIDService.resolveOID(params.oid ?: params.id)

      if ( obj?.isReadable() ) {

        if(obj.class in KBComponent) {

          result.resource = obj.getAllPropertiesWithLinks(params.withCombos ? true : false)

          result.resource.combo_props = obj.allComboPropertyNames
        }
        else if (obj.class.name == 'org.gokb.cred.User'){

          result.resource = ['id': obj.id, 'username': obj.username, 'displayName': obj.displayName, 'curatoryGroups': obj.curatoryGroups]
        }
        else {
          result.resource = obj
        }
      }
      else if (!obj) {
        result.error = "Object ID could not be resolved!"
        result.result = 'ERROR'
      }
      else {
        result.error = "Access to object was denied!"
        result.result = 'ERROR'
      }
    }
    else {
      result.result = 'ERROR'
      result.error = 'No object id supplied!'
    }

    render result as JSON
  }


  def private doQuery (qbetemplate, params, result) {
    log.debug("doQuery ${result}");
    def target_class = grailsApplication.getArtefact("Domain",qbetemplate.baseclass);
    com.k_int.HQLBuilder.build(grailsApplication, qbetemplate, params, result, target_class, genericOIDService)
    def resultrows = []

    log.debug("process recset..");
    int seq = result.offset
    result.recset.each { rec ->
      // log.debug("process rec..");
      def response_row = [:]
      response_row['__oid'] = rec.class.name+':'+rec.id
      response_row['__seq'] = seq++
      qbetemplate.qbeConfig.qbeResults.each { r ->
        response_row[r.heading] = groovy.util.Eval.x(rec, 'x.' + r.property)
      }
      resultrows.add(response_row);
    }
    resultrows
  }

  private static final def CAPABILITIES = [
    "core"                : true,
    "project-mamangement" : true,
    "cell-level-edits"    : true,
    "es-recon"            : true,
    "macros"              : true,
  ]

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  def esconfig () {

    // If etag matches then we can just return the 304 to denote that the resource is unchanged.
    render grailsApplication.config.searchApi as JSON
  }

  private static final Closure SERVER_VERSION_ETAG_DSL = {
    def capabilities = getCapabilities()

    // ETag DSL must return a String and not a GString due to GStringImpl.equals(String) failing even if their character sequences are equal.
    // See: https://jira.grails.org/browse/GPCACHEHEADERS-14
    "${capabilities.app.version}${capabilities.app.buildNumber}".toString()
  }

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  def bulkLoadUsers() {

    log.debug("bulkLoadUsers");

    def result = [:]

    if ( request.method=='POST') {
      log.debug("Handling post")
      User.withNewSession() {

        if ( request instanceof MultipartHttpServletRequest ) {
          def users_stream = request.getFile("users")?.inputStream
          result.loaderResult = tsvSuperlifterService.load(users_stream,
                                                           User.tsv_dataload_config,
                                                           params.dryRun=='Y'?true:false)
        }
      }
    }

    render result as JSON
  }

  private final def checkAlias = { def criteria, Map aliasStack, String dotNotationString, int joint_type = CriteriaSpecification.INNER_JOIN ->
    def str = aliasStack[dotNotationString]
    if (!str) {

      // No alias found for exact match.
      // Start from the front and build up aliases.
      String[] props = dotNotationString.split("\\.")
      String propStr = "${props[0]}"
      String alias = aliasStack[propStr];
      int counter = 1
      while (alias && counter < props.length) {
        str = "${alias}"
        String test = propStr + ".${props[counter]}"

        alias = aliasStack[test]
        if (alias) {
          propStr += test
        }
        counter ++
      }

      // At this point we should have a dot notated alias string, where the aliases already been created for this query.
      // Any none existent aliases will need creating but also adding to the map for traversing.
      if (counter <= props.length) {
        // The counter denotes how many aliases were present, so we should start at the counter and create the missing
        // aliases.
        propStr = null
        for (int i=(counter-1); i<props.length; i++) {
          String aliasVal = alias ? "${alias}.${props[i]}" : "${props[i]}"
          alias = "alias${aliasStack.size()}"

          // Create the alias.
          log.debug ("Creating alias: ${aliasVal} ->  ${alias}")
          criteria.createAlias(aliasVal, alias, joint_type)

          // Add to the map.
          propStr = propStr ? "${propStr}.${props[i]}" : "${props[i]}"
          aliasStack[propStr] = alias
          log.debug ("Added quick string: ${propStr} -> ${alias}")
        }
      }

      // Set the string to the alias we ended on.
      str = alias
    }

    str
  }

  private Closure theQueryCriteria = {  String term, match_in, filters, boolean unique, crit = null ->
    final Map<String, String> aliasStack = [:]

    and {
      if (term && match_in) {
        // Add a condition for each parameter we wish to search.

        or {
          match_in.each { String propname ->

            // Split at the dot.
            String[] levels = propname.split("\\.")

            String propName
            if (levels.length > 1) {

              // Optional joins use LEFT_JOIN
              String aliasName = checkAlias ( delegate, aliasStack, levels[0..(levels.size() - 2)].join('.'), CriteriaSpecification.LEFT_JOIN)
              String finalPropName = levels[levels.size()-1]
              String op = finalPropName == 'id' ? 'eq' : 'ilike'
              String toFind = finalPropName == 'id' ? "${term}".toLong() : "%${term}%"

              log.debug ("Testing  ${aliasName}.${finalPropName} ${op} ${toFind}")
              "${op}" "${aliasName}.${finalPropName}", toFind
            } else {
              String op = propname == 'id' ? 'eq' : 'ilike'
              String toFind = propname == 'id' ? "${term}".toLong() : "%${term}%"

              log.debug ("Testing  ${propname} ${op} ${toFind}")
              "${op}" "${propname}", toFind
            }
          }
        }
      }

      // Filters...
      if (filters) {
        filters.eachWithIndex { String filter, idx ->
          String[] parts =  filter.split("\\=")

          if ( parts.length == 2 && parts[0].length() > 0 && parts[1].length() > 0 ) {

            // The prop name.
            String propname = parts[0]
            String op = "eq"

            if (propname.startsWith("!")) {
              propname = propname.substring(1)
              op = "ne"
            }

            // Split at the dot.
            String[] levels = propname.split("\\.")

            String propName
            if (levels.length > 1) {
              String aliasName = checkAlias ( delegate, aliasStack, levels[0..(levels.size() - 2)].join('.') )
              String finalPropName = levels[levels.size()-1]

              log.debug ("Testing  ${aliasName}.${finalPropName} ${op == 'eq' ? '=' : '!='} ${parts[1]}")
              "${op}" "${aliasName}.${finalPropName}", finalPropName == 'id' ? parts[1].toLong() : parts[1]
            } else {
              log.debug ("Testing  ${propname} ${op == 'eq' ? '=' : '!='} ${parts[1]}")
              "${op}" propname, parts[1] == 'id' ? parts[1].toLong() : parts[1]
            }
          }
        }
      }
    }
    if (unique) {
      projections {
        distinct('id')
      }
    }
  }

  private Closure lookupCriteria = { String term, match_in, filters, attr = [], boolean unique = true ->
    final Map<String, String> aliasStack = [:]

    if (unique) {

      // Use the closure as a subquery so we can return unique ids.
      // We need to deal directly with Hibernate here.
      ExtendedHibernateDetachedCriteria subQ = new ExtendedHibernateDetachedCriteria(targetClass.createCriteria().buildCriteria (theQueryCriteria.curry(term, match_in, filters, unique)))

      criteria.add(Subqueries.propertyIn('id', subQ));
    } else {

      // Execute the queryCriteria in this context.
      (theQueryCriteria.rehydrate(delegate, owner, thisObject))(term, match_in, filters, unique)
    }

    // If we have a list of return attributes then we should add projections here.
    if (attr) {
      resultTransformer CriteriaSpecification.ALIAS_TO_ENTITY_MAP
      projections {
        attr.each { String propname ->

          // Split at the dot.
          String[] levels = propname.split("\\.")

          String propName
          if (levels.length > 1) {
            String aliasName = checkAlias (delegate, aliasStack, levels[0..(levels.size() - 2)].join('.'), CriteriaSpecification.LEFT_JOIN )
            String finalPropName = levels[levels.size()-1]

            String[] propAliasParts = finalPropName.split("\\:")
            finalPropName = propAliasParts[0]
            String propAlias = propAliasParts.length > 1 ? propAliasParts[1] : propAliasParts[0]

            log.debug ("Returning ${aliasName}.${finalPropName} as ${propAlias}")
            property "${aliasName}.${finalPropName}", "${propAlias}"
          } else {
            String[] propAliasParts = propname.split("\\:")
            String finalPropName = propAliasParts[0]
            String propAlias = propAliasParts.length > 1 ? propAliasParts[1] : propAliasParts[0]

            log.debug ("Returning ${finalPropName} as ${propAlias}")
            property "${finalPropName}", "${propAlias}"
          }
        }
      }
    }
  }

  @Secured(['ROLE_SUPERUSER', 'ROLE_REFINEUSER', 'IS_AUTHENTICATED_FULLY'])
  synchronized def lookup () {
    long start = System.currentTimeMillis()
    String classType = GrailsNameUtils.getClassNameRepresentation(params.type)
    Class<? extends KBComponent> c = grailsApplication.getClassLoader().loadClass(
      "org.gokb.cred.${params.type}"
    )

    // Get the "term" parameter for performing a search.
    def term = params.term

    // Results per page.
    def perPage = Math.min(params.int('perPage') ?: 10, 10)

    // Object attributes to search.
    def match_in = ["name"]

    // Lists from jQuery come through with brackets...
    match_in += params.list("match")
    match_in += params.list("match[]")

    // Ensure we only include the none label part.
    match_in = match_in.collect { "${it}".split("\\:")[0] }

    def filters = [
      "!status.id=${RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_RETIRED).id}",
      "!status.id=${RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED).id}"
    ]
    filters += params.list("filters")

    // Attributes to return.
    List attr = ["name:label", 'id']
    attr += params.list("attr")
    attr += params.list("attr[]")

    // Page number.
    def page = params.int("page")

    // If we have a page then we should add a max and offset.
    def query_params = ["max": (perPage)]
    if (page) {
      query_params["offset"] = ((page - 1) * perPage)
    }

    // Build the detached criteria.
    def results = c.createCriteria().list (query_params, lookupCriteria.curry(term, match_in, filters, attr))

    def resp
    if (page) {
      // Return the page of results with a total.
      resp = [
        "total" : results.totalCount,
        "list"  : results.collect {
          it.value = "${it.label}::{${classType}:${it.id}}"
          it
        } as LinkedHashSet
      ]
    } else {
      // Just return the formatted results.
      resp = results.collect {
        it.value = "${it.label}::{${classType}:${it.id}}"
        it
      } as LinkedHashSet
    }

    // Return the response.
    apiReturn (resp)
    log.debug "lookup took ${System.currentTimeMillis() - start} milliseconds"
  }
}
