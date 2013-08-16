package org.gokb

import static java.util.UUID.randomUUID
import grails.converters.JSON

import org.gokb.cred.*
import org.gokb.refine.RefineOperation
import org.gokb.refine.RefineProject
import grails.plugins.springsecurity.Secured
import grails.util.GrailsNameUtils
import org.codehaus.groovy.grails.commons.ApplicationHolder

/**
 * TODO: Change methods to abide by the RESTful API, and implement GET, POST, PUT and DELETE with proper response codes.
 * 
 * @author Steve Osguthorpe
 */

class ApiController {
  
  private static final Closure TRANSFORMER_PROJECT = {
	
	// Treat as refine project.
	RefineProject proj = it as RefineProject
	
	// Populate the map.
	TreeMap props = ["id" : proj.id] // Id is not included in properties...
	
	// Go through defined properties.
	proj.properties.each { k,v ->
	  if (v instanceof User) {
		User u = v as User
		props[k] = [
		  "id" 			: "${u.id}",
		  "email" 		: "${u.email}",
		  "displayName" 	: "${u.displayName}"
		]
	  } else {
		props[k] = v
	  }
	}

	return props
  }

  def ingestService
  def grailsApplication
  def springSecurityService

  /**
   * Before interceptor to check the current version of the refine
   * plugin that is being used.
   */

  def beforeInterceptor = [action: this.&versionCheck]

  // defined with private scope, so it's not considered an action
  private versionCheck() {
	if ( params.skipVC ) {
	}
	else {
	  def gokbVersion = request.getHeader("GOKb-version")
          def serv_url = ApplicationHolder.application.config.serverUrl ?: 'http://gokb.kuali.org/extension/latest.zip'

	  if (gokbVersion != grailsApplication.config.refine_min_version) {
		apiReturn([errorType : "versionError"], "You are using an out of date version of the GOKb extension. " +
		"Please download and install the latest version from <a href='${serv_url}/extension/latest.zip' >${serv_url}/extension/latest.zip</a>." +
		"<br />You will need to restart refine and clear your browser cache after installing the new extension.",
		"error")
		return false
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
  private def apiReturn = {result, String message = "", String status = "success" ->
	def data = [
	  code    : (status),
	  result    : (result),
	  message    : (message),
	]

	def json = data as JSON
	log.debug (json)
	render json
	//    render (text: "${params.callback}(${json})", contentType: "application/javascript", encoding: "UTF-8")
  }

  def index() {
  }

  //  @Secured(["ROLE_USER"])
  def describe() {
	apiReturn(RefineOperation.findAll ())
  }
  
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def checkMD5() {
	
	def metadata = JSON.parse(params.get("md"));
	
	// The parameters.
	log.debug(metadata);
	def md5 = metadata.customMetadata.hash;
	long pId = params.long("project");
	
	// RefineProject
	RefineProject rp = RefineProject.createCriteria().get {
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
  
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def checkSkippedTitles() {
	
	long pId = params.long("project");
	
	// RefineProject
	RefineProject rp = RefineProject.get(pId)
	
	// The result is the list of titles if we have a project.
	def result = rp?.getSkippedTitles() ?: []
	
	// Return the result.
	apiReturn(result)
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def estimateDataChanges() {
	log.debug("Try to estimate what changes will occur in CRED for data in zip file.")
	def f = request.getFile('dataZip')
	def result = [:]

	if (f && !f.empty) {

	  log.debug ("Got file saving and parsing.")
	  // Save the file temporarily...
	  def temp_data_zipfile
	  try {

		// Temporary file.
		temp_data_zipfile = File.createTempFile(
			Long.toString(System.nanoTime()) + '_gokb_','_refinedata.zip',null
			)
		f.transferTo(temp_data_zipfile)
		def parsed_project_file = ingestService.extractRefineDataZip(temp_data_zipfile)

		log.debug("Try and predetermine the changes.");
		result = ingestService.estimateChanges(parsed_project_file, params.projectID)

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

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def saveOperations() {
	// Get the operations as a list.

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

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def projectList() {
	apiReturn (RefineProject.findAll().collect(TRANSFORMER_PROJECT))
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
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

		  // Set the checkout details.
//		  def chOut = (params.checkOutName ?: "No Name Given") +
//			  " (" + (params.checkOutEmail ?: "No Email Given") + ")"
//		  project.setCheckedOutBy(chOut)
		  project.setLastCheckedOutBy(user)
		  project.setProjectStatus(RefineProject.Status.CHECKED_OUT)
//		  project.setCheckedIn(false)
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

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def projectCheckin() {

	// Get the current user from the security service.
	User user = springSecurityService.currentUser
	
	log.debug ("User ${user.getUsername()} attempting to check-in a project.")
	
	def f = request.getFile('projectFile')

	if (f && !f.empty) {
	  
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

		project.possibleRulesString = suggestRulesFromParsedData (parsed_project_file, project.provider) as JSON

		// Make sure we null the progress...
		project.setProgress(null)

		// Save and flush the project
		project.save(flush:true, failOnError:true)

		if (params.ingest) {
		  // Try and ingest the project too!
		  projectIngest(project,parsed_project_file)
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

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  private def projectIngest (RefineProject project, parsed_data) {
	log.debug("projectIngest....");

	if (project.getProjectStatus() == RefineProject.Status.CHECKED_IN) {
	  
	  log.debug("Validate the project");
	  def validationResult = ingestService.validate(parsed_data)
	  project.lastValidationResult = validationResult.messages

	  if ( validationResult.status == true ) {
		ingestService.extractRules(parsed_data, project)
		doIngest(parsed_data, project);
	  }
	  else {
		log.debug("validation failed, not ingesting");
	  }
	} else {
	  log.debug ("Attempted to ingest checked-out project.")
	}
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def projectDataValid() {

	log.debug("Try to validate data in zip file.")
	def f = request.getFile('dataZip')
	def validationResult = [:]

	if (f && !f.empty) {

	  // Save the file temporarily...
	  def temp_data_zipfile
	  try {
		temp_data_zipfile = File.createTempFile(
			Long.toString(System.nanoTime()) + '_gokb_','_refinedata.zip',null
		)
		f.transferTo(temp_data_zipfile)
		def parsed_project_file = ingestService.extractRefineDataZip(temp_data_zipfile)

		log.debug("Validate the data in the zip");
		validationResult = ingestService.validate(parsed_project_file)

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

	apiReturn ( validationResult )
  }

  private def doIngest(parsed_data, project) {
	log.debug("ingesting refine project.. kicking off background task");


	// Create a new session to run the ingest service in asynchronous.

	runAsync ({projData, Long projId ->
	  RefineProject.withNewSession {

		// Fire the ingest of the project id.
		ingestService.ingest(projData, projId)


	  }

	  log.debug ("Finished data insert.")
	}.curry(parsed_data, project.id))
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
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
      default:
        break;
    }
    apiReturn(result)
  }
  
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def projectIngestProgress() {
	if (params.projectID) {

	  // Get the project.
	  def project = RefineProject.get(params.projectID)

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
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
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
			label:'Provider',
			type:'refdata',
			refdataType:'cp',
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
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def suggestRulesFromData() {

	log.debug ("Attempting to get rule suggestions from data zip.")

	def f = request.getFile('dataZip')
	def rules = [:]
	if (f && !f.empty) {
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
		def parsed_project_file = ingestService.extractRefineDataZip(temp_data_zipfile)
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
  
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def lookup() {
	
	// Get the "term" parameter for performing a search.
	def term = params.term

	// Should take a type parameter and do the right thing.
	try {
	  Class<? extends KBComponent> c = grailsApplication.getClassLoader().loadClass(
	  	"org.gokb.cred.${GrailsNameUtils.getClassNameRepresentation(params.type)}"
	  )
	  apiReturn ( c.createCriteria().listDistinct {
		if (term) {
		  ilike "name", "%${term}%"
		}
	  }.collect { KBComponent comp -> 
	    [ "value" : "${comp.name}::{${c.getSimpleName()}:${comp.id}}", "label" : (comp.name) ]
	  })
	  
	} catch (Throwable t) {
	  /* Just return an empty list. */
	  apiReturn ([])
	}	
  }
}
