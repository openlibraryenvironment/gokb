package org.gokb

import static java.util.UUID.randomUUID
import grails.converters.JSON
import grails.plugins.springsecurity.Secured
import org.gokb.refine.RefineOperation
import org.gokb.refine.RefineProject
import org.gokb.cred.Org;

/**
 * TODO: Change methods to abide by the RESTful API, and implement GET, POST, PUT and DELETE with proper response codes.
 * 
 * @author Steve Osguthorpe
 */

class ApiController {

  private final String REQUIRED_EXTENSION_VERSION = "0.5"

  def ingestService
  def grailsApplication

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
      if (gokbVersion != REQUIRED_EXTENSION_VERSION) {
        apiReturn([errorType : "versionError"], "You are using an out of date version of the GOKb extension. " +
        "Please download and install the latest version from <a href='http://gokb.k-int.com/extension/latest' >gokb.k-int.com/extension/latest</a>." +
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

  def projectList() {
	apiReturn ( RefineProject.findAll() )
  }

  def projectCheckout() {

	log.debug(params)
	if (params.projectID) {

	  // Get the project.
	  def project = RefineProject.get(params.projectID)

	  if (project) {
		
		if (project.getCheckedIn()) {

    		// Get the file and send the file to the client.
    		def file = new File(grailsApplication.config.project_dir + project.file)
    
    		// Send the file.
    		response.setContentType("application/x-gzip")
    		response.setHeader("Content-disposition", "attachment;filename=${file.getName()}")
    		response.outputStream << file.newInputStream()
    
    		// Set the checkout details.
    		def chOut = (params.checkOutName ?: "No Name Given") +
    			" (" + (params.checkOutEmail ?: "No Email Given") + ")"
    		project.setCheckedOutBy(chOut)
    		project.setCheckedIn(false)
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

  def projectCheckin() {
	
	log.debug(params)
	
	def f = request.getFile('projectFile')

	if (f && !f.empty) {

	  // Get the project.
	  RefineProject project
	  if (params.projectID) {
		project = RefineProject.load(params.projectID)
	  } else {
		// Creating new project.
		project = new RefineProject()
		project.setHash(params.hash ?: null)

		// Set the org too.
		log.debug("Setting provider from submission.");
		Org org = Org.get(params.provider)
		if (org) {
		  project.provider = org
		}
	  }

	  if (project) {

		// A quick hack to set the project provider, this should come from refine, but for testing purposes, we set this to Wiley
//		if ( !project.provider ) {
//		  log.debug("Defaulting in provider, this should be set from the refine project initially. #FixMe");
//		  project.provider = Org.findByName('Wiley') ?: new Org(name:'Wiley').save();
//		}

		// Generate a filename...
		def fileName = "project-${randomUUID()}.tar.gz"

		// Save the file.
		f.transferTo(new File(grailsApplication.config.project_dir + fileName))

		// Set the file property.
		project.setFile(fileName)

		// Update other project properties.
		if (params.description) project.setDescription(params.description)
		if (params.name) project.setName(params.name)
		project.setCheckedIn(true)
		project.setCheckedOutBy(null)
		project.setLocalProjectID(null)
		project.setModified(new Date())
		if (params.notes) project.setNotes(params.notes)
		
	        // Parse the uploaded project.. We do this here because the parsed project data will be needed for
            // suggesting rules or validation.
      	    log.debug("parse refine project");
	        def parsed_project_file = ingestService.extractRefineproject(project.file)
			project.possibleRulesString = suggestRulesFromParsedData (parsed_project_file, project.provider) as JSON
			
		// Make sure we null the progress...
		project.setProgress(null)
		if (params.ingest) {
		  // Try and ingest the project too!
		  projectIngest(project,parsed_project_file)
		}

		// Save and flush.
		project.save(flush: true, failOnError: true)

		// Return the project data.
		apiReturn(project)
		return
	  }
	} else if (params.projectID) {

	  // Check in with no changes. (In effect we are just removing the lock)
	  def project = RefineProject.load(params.projectID)
	  if (project) {
		// Remove lock properties and return the project state.
		project.setCheckedIn(true)
		project.setCheckedOutBy(null)
		project.setLocalProjectID(0)
		project.save(flush: true, failOnError: true)

		apiReturn(project)
		return
	  }
	}

	// Send 404 if not found.
	response.status = 404;
  }

  private def projectIngest (RefineProject project, parsed_data) {

	if (project.getCheckedIn()) {
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
	runAsync {
	  ingestService.ingest(parsed_data, project)
	}
  }

  def refdata() {
	def result = [:];

	// Should take a type parameter and do the right thing. Initially only do one type
	switch ( params.type ) {
	  case 'cp' :
		def oq = Org.createCriteria()
		def orgs = oq.listDistinct {
		  tags {
			owner {
			  eq('desc','Org Role');
			}
			eq('value','Content Provider');
		  }
		  order("name", "asc")
		}
		result.datalist=new java.util.ArrayList()
		orgs.each { o ->
		  result.datalist.add([
			"value" : "${o.id}",
			"name" : (o.name)
		  ])
		}
		break;
	  default:
		break;
	}

	apiReturn(result)
  }
  
  def projectIngestProgress() {
	if (params.projectID) {
	  
	  // Get the project.
	  def project = RefineProject.get(params.projectID)

	  if (project) {
		// Return the progress.
		apiReturn ( [progress : project.getProgress()] )
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
  def suggestRulesFromData() {	
	
	log.debug ("Attempting to get rule suggestions from data zip.")
	
	def f = request.getFile('dataZip')
	def rules = [:]
	if (f && !f.empty) {
	  Org provider = null;
	  if (params.providerID) {
		provider = Org.get(params.providerID)
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
	  log.error("Problem trying to match rules",e)
	}
  }  
}
