package org.gokb

import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job
import com.k_int.ExtendedHibernateDetachedCriteria
import com.k_int.TextUtils
import com.k_int.TsvSuperlifterService
import grails.converters.JSON
import grails.util.GrailsNameUtils
import grails.util.Holders
import org.elasticsearch.action.search.*
import org.elasticsearch.index.query.*
import org.gokb.cred.*
import org.gokb.refine.RefineOperation
import org.gokb.refine.RefineProject
import org.gokb.validation.Validation
import org.hibernate.criterion.CriteriaSpecification
import org.hibernate.criterion.Subqueries
import org.springframework.security.access.annotation.Secured
import org.springframework.web.multipart.MultipartHttpServletRequest

import java.security.SecureRandom

import static java.util.UUID.randomUUID
/**
 * TODO: Change methods to abide by the RESTful API, and implement GET, POST, PUT and DELETE with proper response codes.
 *
 * @author Steve Osguthorpe
 */

class ApiController {

  TsvSuperlifterService tsvSuperlifterService
  RefineService refineService
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

  def ingestService
  def grailsApplication
  def springSecurityService
  def componentLookupService
  def genericOIDService
  ConcurrencyManagerService concurrencyManagerService

  /**
   * Before interceptor to check the current version of the refine
   * plugin that is being used.
   */

  def beforeInterceptor = [action: this.&versionCheck, 'except': ['downloadUpdate', 'search', 'capabilities', 'esconfig', 'bulkLoadUsers', 'find', 'suggest']]

  // defined with private scope, so it's not considered an action
  private versionCheck() {
    if ( params.skipVC ) {
    }
    else {
      def gokbVersion = request.getHeader("GOKb-version")
      def serv_url = grailsApplication.config.extensionDownloadUrl ?: 'http://gokb.kuali.org'

      if (gokbVersion != 'development') {
        if (!gokbVersion || TextUtils.versionCompare(gokbVersion, grailsApplication.config.refine_min_version) < 0) {
          apiReturn([errorType : "versionError"], "The refine extension you are using is not compatible with this instance of the service.",
          "error")

          return false
        }
      }
    }
  }

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

  @Secured(['ROLE_SUPERUSER', 'ROLE_REFINEUSER', 'IS_AUTHENTICATED_FULLY'])
  def describe() {
    apiReturn(RefineOperation.findAll ())
  }

  @Secured(['ROLE_SUPERUSER', 'ROLE_REFINEUSER', 'IS_AUTHENTICATED_FULLY'])
  def checkMD5() {

    def md5 = params.get("hash");
    long pId = params.long("project");

    // RefineProject
    def rp = RefineProject.createCriteria().list {
      and {
        ne ("localProjectID", pId)
        eq ("hash", md5)
      }
    }

    // Create the return object.
    def result = [
      "hashCheck" : !rp
    ]

    // Return the result.
    apiReturn(result)
  }

  @Secured(['ROLE_SUPERUSER', 'ROLE_REFINEUSER', 'IS_AUTHENTICATED_FULLY'])
  def checkSkippedTitles() {

    long pId = params.long("project");

    // RefineProject
    RefineProject rp = RefineProject.get(pId)

    // The result is the list of titles if we have a project.
    def result = rp?.getSkippedTitles() ?: []

    // Return the result.
    apiReturn(result)
  }

  @Secured(['ROLE_SUPERUSER', 'ROLE_REFINEUSER', 'IS_AUTHENTICATED_FULLY'])
  def estimateDataChanges() {
    log.debug("Try to estimate what changes will occur in CRED for data in zip file.")
    def f = request.getFile('dataZip')
    def result = [:]

    if (!(f?.empty)) {

      log.debug ("Got file saving and parsing.")
      // Save the file temporarily...
      def temp_data_zipfile
      try {

        // Temporary file.
        temp_data_zipfile = File.createTempFile(
            Long.toString(System.nanoTime()) + '_gokb_','_refinedata.zip',null
            )
        f.transferTo(temp_data_zipfile)
        def parsed_project_file = [:]
        ingestService.extractRefineDataZip(temp_data_zipfile, parsed_project_file)

        log.debug("Try and predetermine the changes.");
        result = ingestService.estimateChanges(parsed_project_file, params.projectID, (params.boolean("incremental") != false))

      } catch (Exception e) {

        apiReturn(e, null, "error")
      } finally {
        if ( temp_data_zipfile ) {
          try {
            temp_data_zipfile.delete();
          }
          catch ( Throwable t ) {
          }
        }
      }
    } else {
      log.debug("No dataZip file request attribute supplied.")
    }

    apiReturn ( result )
  }

  @Secured(['ROLE_SUPERUSER', 'ROLE_REFINEUSER', 'IS_AUTHENTICATED_FULLY'])
  def saveOperations() {
    // Get the operations as a list.
    log.debug("saveOperations");

    // The line below looks like it replaces like with like but because the
    // second parameter is a regex it gets double escaped.
    def ops = params.operations.replaceAll("\\\\\"", "\\\\\"")
    ops = JSON.parse(params.operations)

    // Save each operation to the database
    ops.each {
      try {
        new RefineOperation(
            description : it['operation']['description'],
            operation : new LinkedHashMap(it['operation'])
            ).save(failOnError : true)
      } catch (Exception e) {
        log.error(e)
      }
    }

    apiReturn( null, "Succesfully saved the operations.")
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

  @Secured(['ROLE_SUPERUSER', 'ROLE_REFINEUSER', 'IS_AUTHENTICATED_FULLY'])
  def projectList() {
    long time = System.currentTimeMillis()
    apiReturn (RefineProject.findAll().collect(TRANSFORMER_PROJECT))
    log.debug ("Project list ${System.currentTimeMillis() - time} milliseconds")
  }

  @Secured(['ROLE_SUPERUSER', 'ROLE_REFINEUSER', 'IS_AUTHENTICATED_FULLY'])
  def projectCheckout() {

    // Get the current user from the security service.
    User user = springSecurityService.currentUser

    log.debug ("User ${user.getUsername()} attempting to check-out a project.")
    if (params.projectID) {

      // Get the project.
      def project = RefineProject.get(params.projectID)

      if (project) {

        if (project.getProjectStatus() != RefineProject.Status.CHECKED_OUT) {

          // Get the file and send the file to the client.
          def file = new File(grailsApplication.config.project_dir + project.file)

          // Send the file.
          response.setContentType("application/x-gzip")
          response.setHeader("Content-disposition", "attachment;filename=${file.getName()}")
          response.outputStream << file.newInputStream()

          project.setLastCheckedOutBy(user)
          project.setProjectStatus(RefineProject.Status.CHECKED_OUT)

          project.setLocalProjectID(params.long("localProjectID"))
          return
        } else {
          // Project already checked out.
          log.debug ("Project already checked out")
        }
      }
    }

    // Send 404 if not found.
    response.status = 404;
  }

  @Secured(['ROLE_SUPERUSER', 'ROLE_REFINEUSER', 'IS_AUTHENTICATED_FULLY'])
  def projectCheckin() {

    // Get the current user from the security service.
    User user = springSecurityService.currentUser

    log.debug ("User ${user.getUsername()} attempting to check-in a project.")

    def f = request.getFile('projectFile')

    if (!(f?.empty)) {

      boolean new_project = false;

      // Get the project.
      RefineProject project

      if (params.projectID) {
        log.debug("Lookup existing project: ${params.projectID}");
        project = RefineProject.get(params.projectID)
        // We might end up here if importing a project exported by someone else who is using a different
        // DB.
      } else {
        // Creating new project.
        log.debug("New project");
        project = new RefineProject()
        project.setCreatedBy(user)
        project.setLastCheckedOutBy(user)

        new_project = true
      }

      if (project) {
        // Provider?
        if (params.provider) {
          // Set the org too.
          Org org = Org.get(params.provider)
          if (org) {
            log.debug("Setting provider to ${org.id}.");
            project.provider = org
          }
        }

        if (params.source) {
          // Need to set the source here.
          Source src = componentLookupService.lookupComponent(params.source)

          if (!src.name || src.name == "") {
            // Replace the component regex to just leave the string, and set as the name.
            src.name = params.source.replaceAll("\\:\\:\\{[^\\}]*\\}", "")
          }

          // Set the source of this project.
          project.setSource(src)

          // Save the object.
          src.save (failOnError:true)
          project.save(failOnError:true, flush:true)
        }

        // Generate a filename...
        def fileName = "project-${randomUUID()}.tar.gz"

        // Save the file.
        f.transferTo(new File(grailsApplication.config.project_dir + fileName))

        // Set the file property.
        project.setFile(fileName)

        // Update other project properties.
        if (params.hash) project.setHash(params.hash)
        if (params.description) project.setDescription(params.description)
        if (params.name) project.setName(params.name)
        project.setProjectStatus(RefineProject.Status.CHECKED_IN)

        //		project.save()

        //		project.setLastCheckedOutBy(null)
        project.setLocalProjectID(null)
        project.setModified(new Date())
        project.setModifiedBy(user)
        if (params.notes) project.setNotes(params.notes)

        // Parse the uploaded project.. We do this here because the parsed project data will be needed for
        // suggesting rules or validation.
        log.debug("Parsing refine project");
        def parsed_project_file = ingestService.extractRefineproject(project.file)

        if ( parsed_project_file == null )
          throw new Exception("Problem parsing project file");

        // We now need to save the embeded source-file (if one is present)
        if (new_project) {
          log.debug("First time checking in the project. Let's add the source file.")
          final String source_file_str = parsed_project_file?.metadata?.customMetadata?."source-file"
          if (source_file_str) {

            log.debug("Found source file in metadata. Decoding and adding to project.")
            // We need to decode it (base64).
            byte[] source_tgz = source_file_str.decodeBase64()
            project.setSourceFile(source_tgz)
          }
        }

        project.possibleRulesString = suggestRulesFromParsedData (parsed_project_file, project.provider) as JSON

        // Make sure we null the progress...
        project.setProgress(null)

        // Save and flush the project
        project.save(flush:true, failOnError:true)

        if (params.ingest) {

          // Is this an incremental update.
          boolean incremental = (params.boolean("incremental") != false)

          // Try and ingest the project too!
          projectIngest(project,parsed_project_file,incremental, user)
        }

        // Return the project data.
        apiReturn(project.collect(TRANSFORMER_PROJECT))
        return
      }
    } else if (params.projectID) {

      // Check in with no changes. (In effect we are just removing the lock)
      def project = RefineProject.get(params.projectID)
      if (project) {
        // Remove lock properties and return the project state.
        project.setProjectStatus(RefineProject.Status.CHECKED_IN)
        //		project.setCheckedOutBy(null)
        project.setLocalProjectID(0)
        project.save(flush: true, failOnError: true)

        apiReturn(project.collect(TRANSFORMER_PROJECT))
        return
      }
    }

    // Send 404 if not found.
    response.status = 404;
  }

  @Secured(['ROLE_SUPERUSER', 'ROLE_REFINEUSER', 'IS_AUTHENTICATED_FULLY'])
  private def projectIngest (RefineProject project, parsed_data, boolean incremental, User user) {
    log.debug("projectIngest....");

    if (project.getProjectStatus() == RefineProject.Status.CHECKED_IN) {

      log.debug("Validate the project");
      def validationResult = ingestService.validate(parsed_data)
      project.lastValidationResult = validationResult.messages

      if ( validationResult.status == true ) {
        ingestService.extractRules(parsed_data, project)
        doIngest(parsed_data, project, incremental, user)
      }
      else {
        log.debug("validation failed, not ingesting");
      }
    } else {
      log.debug ("Attempted to ingest checked-out project.")
    }
  }

  @Secured(['ROLE_SUPERUSER', 'ROLE_REFINEUSER', 'IS_AUTHENTICATED_FULLY'])
  def projectDataValid() {

    log.debug("Try to validate data in zip file.")
    def f = request.getFile('dataZip')
    def validationResult = [:]

    if (!(f?.empty)) {

      // Save the file temporarily...
      def temp_data_zipfile
      try {
        temp_data_zipfile = File.createTempFile(
            Long.toString(System.nanoTime()) + '_gokb_','_refinedata.zip',null
            )
        f.transferTo(temp_data_zipfile)
        def parsed_project_file = [:]
        ingestService.extractRefineDataZip(temp_data_zipfile, parsed_project_file)

        log.debug("Validate the data in the zip");
        validationResult = ingestService.validate(parsed_project_file)
      } finally {
        log.debug("validate completed, delete tempfile");
        if ( temp_data_zipfile ) {
          try {
            temp_data_zipfile.delete();
          }
          catch ( Throwable t ) {
          }
        }
      }
    } else {
      log.debug("No dataZip file request attribute supplied.")
    }

    apiReturn ( validationResult )
  }

  private def doIngest(parsed_data, project, boolean incremental, user) {
    log.debug("ingesting refine project.. kicking off background task")

    // The concurrency manager returns a Job that can be used to track progress of this,
    // Background task.
    Job background_job = concurrencyManagerService.createJob { Job job ->
      // Create a new session to run the ingest.

      ingestService.ingest(parsed_data, project.id, incremental, user.id, job)
      log.debug ("Finished data insert.")
    }
    .startOrQueue()
  }

  @Secured(['ROLE_SUPERUSER', 'ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
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

  @Secured(['ROLE_SUPERUSER', 'ROLE_REFINEUSER', 'IS_AUTHENTICATED_FULLY'])
  def projectIngestProgress() {
    if (params.projectID) {

      // Get the project.
      def project = RefineProject.get(params.projectID)

      // Also checking job 1...
      log.debug ("Job 1: " + concurrencyManagerService.getJob(1)?.progress ?: "Undefined")

      if (project) {
        // Return the progress.
        apiReturn ( project.collect(TRANSFORMER_PROJECT) )
        return
      }

      // Return a 404.
      response.status = 404;
    }
  }

  /**
   *   Return a JSON structured array of the fields that should be collected when a project is checked in for the
   *   first time
   */
  @Secured(['ROLE_SUPERUSER', 'ROLE_REFINEUSER', 'IS_AUTHENTICATED_FULLY'])
  def getProjectProfileProperties() {
    def result = [
      [
        type : "fieldset",
        children : [
          [
            type : 'legend',
            text : 'Properties'
          ],
          [
            label:'Source',
            type:'textarea',
            source:'ComponentLookup:Source',
            name:'source',
            create:true,
          ],
          [
            label:'Provider',
            type:'select',
            source:'RefData:cp',
            name:'provider',
          ],
          [
            label:'Name',
            type:'text',
            name: 'name',
          ],
          [
            label:'Description',
            type:'text',
            name: 'description',
          ],
          [
            label:'Notes',
            type:'textarea',
            name: 'notes',
          ]
        ]
      ]
    ]
    apiReturn(result)
  }

  /**
   * Suggest the rules that might apply to the data.txt within this zip file.
   * @param dataZip
   */
  @Secured(['ROLE_SUPERUSER', 'ROLE_REFINEUSER', 'IS_AUTHENTICATED_FULLY'])
  def suggestRulesFromData() {

    log.debug ("Attempting to get rule suggestions from data zip.")

    def f = request.getFile('dataZip')
    def rules = [:]
    if (!(f?.empty)) {
      Org provider = null;
      if (params.providerID) {
        provider = Org.get(params.providerID)
        log.debug("Provider to use in rules is: '" + provider.name + "'")
      }

      def temp_data_zipfile
      try {

        temp_data_zipfile = File.createTempFile(
            Long.toString(System.nanoTime()) + '_gokb_','_refinedata.zip',null
            );
        f.transferTo(temp_data_zipfile)
        def parsed_project_file = [:]
        ingestService.extractRefineDataZip (temp_data_zipfile, parsed_project_file)
        rules = suggestRulesFromParsedData ( parsed_project_file, provider )

      } finally {
        if ( temp_data_zipfile ) {
          try {
            temp_data_zipfile.delete();
          }
          catch ( Throwable t ) {
          }
        }
      }
    } else {
      log.debug("No dataZip file request attribute supplied.")
    }

    apiReturn ( rules )
  }

  private def suggestRulesFromParsedData (parsed_project_file, provider) {
    log.debug ("Suggesting rules from parsed data.")
    try {
      def possible_rules = ingestService.findRules(parsed_project_file, provider )
      return possible_rules
    }
    catch ( Exception e ) {
      log.error("Problem trying to match rules", e)
    }
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

  def checkUpdate () {

    def result = refineService.checkUpdate(params."current-version" ?: request.getHeader("GOKb-version"), springSecurityService?.currentUser?.hasRole("ROLE_REFINETESTER") as boolean)

    // Add the api version to the result. We will actually use this to circumvent a degrade taking place in the refine client.
    result."api-version" = getCapabilities()."app"."version"
    apiReturn (result)
  }

  def downloadUpdate () {
    return downloadUpdateFile (springSecurityService?.currentUser?.hasRole("ROLE_REFINETESTER") as boolean)
  }

  private def downloadUpdateFile(boolean tester = false) {

    // Grab the download.
    def file = refineService.extensionDownloadFile (params."requested-version", tester)
    if (file) {
      // Send the file.
      response.setContentType("application/x-gzip")
      response.setHeader("Content-disposition", "attachment;filename=${file.getName()}")
      response.outputStream << file.newInputStream()
    }

    // Send not found.
    response.status = 404
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

  def suggest() {
    def result = [:]
    def esclient = ESWrapperService.getClient()
    def errors = [:]
    def offsetDefault = 0
    def maxDefault = 10


    result.endpoint = request.forwardURI

    try {

      if ( params.q?.size() > 0 ) {

        QueryBuilder suggestQuery = QueryBuilders.boolQuery()

        suggestQuery.must(QueryBuilders.matchQuery('suggest', params.q))

        if ( params.componentType ) {
          suggestQuery.must(QueryBuilders.matchQuery('componentType', params.componentType))

          if( params.componentType == 'Org' && params.role ) {
            suggestQuery.must(QueryBuilders.matchQuery('roles', params.role))
          }
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

  def find() {
    def result = [:]
    def esclient = ESWrapperService.getClient()
    def search_action = null
    def errors = [:]

    result.endpoint = request.forwardURI

    try {

      if ( !params.q ) {

        QueryBuilder exactQuery = QueryBuilders.boolQuery()

        def singleParams = [:]
        def unknown_fields = []
        def other_fields = ["controller","action","max","offset","from"]
        def id_params = [:]
        def orgRoleParam = ""

        params.each { k, v ->
          if ( k == 'componentType' && v instanceof String ) {
            singleParams['componentType'] = v
          }

          else if( k == 'role' && v instanceof String ) {
            orgRoleParam = v
          }

          else if (k == 'curatoryGroup' && v instanceof String) {
            singleParams['curatoryGroups'] = v
          }

          else if (k == 'label' && v instanceof String) {
            exactQuery.should(QueryBuilders.matchQuery('name', v))
            exactQuery.should(QueryBuilders.matchQuery('altname', v))
            exactQuery.minimumNumberShouldMatch(1)
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
          errors['unknown'] = "Unknown parameter(s): ${unknown_fields}"
        }

        if ( orgRoleParam ) {
          if ( singleParams['componentType'] ) {
            singleParams['roles'] = orgRoleParam
          }
          else {
            errors['role'] = "To filter by Org Roles, please add filter componentType=Org to the query"
          }
        }

        if (singleParams) {
          singleParams.each { k,v ->
            exactQuery.must(QueryBuilders.matchQuery(k,v))
          }
        }

        if (id_params) {
          exactQuery.must(QueryBuilders.nestedQuery("identifiers", addIdQueries(id_params)))
        }



        if( singleParams || params.label || id_params ) {
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

          search_action = es_request.execute()
        }
        else{
          errors['params'] = "No valid parameters found"
        }
      }

      else {
        result.max = params.max ? Integer.parseInt(params.max) : 10;
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

        if ( params.q?.length() > 0) {

          params.q = params.q.replace('[',"(")
          params.q = params.q.replace(']',")")

          def query_str = buildQuery(params)

          search_action = esclient.search {
            indices grailsApplication.config.globalSearch.indices
            types grailsApplication.config.globalSearch.types
            source {
              from = result.offset
              size = result.max
              query {
                query_string (query: query_str)
              }
            }
          }

        }

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
          response_record.score = r.score

          r.source.each { field, val ->
            response_record."${field}" = val
          }

          result.records.add(response_record);
        }
      }

    }finally {
      if (errors) {
        result.errors = errors
      }
    }

    render result as JSON
  }

  private def addIdQueries(params) {

    QueryBuilder idQuery = QueryBuilders.boolQuery()

    params.each { k,v ->
      idQuery.must(QueryBuilders.matchQuery(k, v))
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

  private static def getCapabilities() {

    if (!CAPABILITIES."app") {
      CAPABILITIES."app" = [:]

      Holders.grailsApplication.metadata.each { String k, v ->
        if ( k.startsWith ("app.") ) {

          String prop_name = "${k.substring(4)}"
          if (!prop_name.contains('.')) {
            CAPABILITIES."app"."${prop_name}" = v
          }
        }
      }

      // Also add the required columns here.
      CAPABILITIES."app"."required-cols" = Validation.getRequiredColumns()
    }

    CAPABILITIES
  }

  def capabilities () {

    // If etag matches then we can just return the 304 to denote that the resource is unchanged.
    withCacheHeaders {
      etag ( SERVER_VERSION_ETAG_DSL )
      generate {
        render (getCapabilities() as JSON)
      }
    }
  }


  def esconfig () {

    // If etag matches then we can just return the 304 to denote that the resource is unchanged.
    withCacheHeaders {
      etag ( SERVER_VERSION_ETAG_DSL )
      generate {
        render (grailsApplication.config.searchApi as JSON)
      }
    }
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
      "org.gokb.cred.${classType}"
    )
    log.debug("Starting lookup: ${params}")

    // Get the "term" parameter for performing a search.
    def term = params.term

    if (params.match == 'id') {
      term = params.long('id')
    }

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
