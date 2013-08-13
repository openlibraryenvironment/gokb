package org.gokb

import grails.converters.JSON
import grails.gorm.DetachedCriteria

import java.security.MessageDigest
import java.text.SimpleDateFormat

import org.apache.commons.compress.archivers.*
import org.apache.commons.compress.archivers.tar.*
import org.apache.commons.compress.compressors.gzip.*
import org.gokb.cred.*
import org.gokb.refine.*
import org.gokb.validation.Validation
import org.springframework.transaction.TransactionStatus


class IngestService {

  // Automatically injected services from grails-app/services
  def grailsApplication
  def titleLookupService
  def componentLookupService
  def sessionFactory
  def propertyInstanceMap = org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin.PROPERTY_INSTANCE_MAP
  def possible_date_formats = [
	new SimpleDateFormat('yyyy-MM-dd'), // Default format Owen is pushing ATM.
	new SimpleDateFormat('yyyy/MM/dd'),
	new SimpleDateFormat('dd/MM/yyyy'),
	new SimpleDateFormat('dd/MM/yy'),
	new SimpleDateFormat('yyyy/MM'),
	new SimpleDateFormat('yyyy')
  ];


  public static final String PUBLICATION_TITLE = 'publicationtitle'
  public static final String DATE_FIRST_PACKAGE_ISSUE = 'datefirstpackageissue'
  public static final String VOLUME_FIRST_PACKAGE_ISSUE = 'volumefirstpackageissue'
  public static final String NUMBER_FIRST_PACKAGE_ISSUE = 'numberfirstpackageissue'
  public static final String DATE_LAST_PACKAGE_ISSUE = 'datelastpackageissue'
  public static final String VOLUME_LAST_PACKAGE_ISSUE = 'volumelastpackageissue'
  public static final String NUMBER_LAST_PACKAGE_ISSUE = 'numberlastpackageissue'

  public static final String IDENTIFIER_PREFIX = 'title.identifier'
  public static final String PRINT_IDENTIFIER = "${IDENTIFIER_PREFIX}.issn"
  public static final String ONLINE_IDENTIFIER = "${IDENTIFIER_PREFIX}.eissn"
  public static final String HOST_PLATFORM_NAME = 'platform.host.name'
  public static final String HOST_PLATFORM_URL = 'platform.host.url'
  public static final String HOST_PLATFORM_BASE_URL = 'platform.host.base.url'

  public static final String COVERAGE_DEPTH = 'coveragedepth'
  public static final String COVERAGE_NOTES = 'coveragenotes'
  public static final String EMBARGO_INFO = 'kbartembargo'

  public static final String PACKAGE_NAME = 'package.name'
  public static final String PUBLISHER_NAME = 'org.publisher.name'

  /**
   *  Validate a parsed project. 
   *  @param project_data Parsed map of project data
   */
  def validate(project_data) {
	log.debug("Validate");

	def result = Validation.doValidate(project_data)
//	def result = [:]
//	result.status = true
//	result.messages = []
//
//	if ( project_data?.processingCompleted ) {
//	  log.debug("Processing of ingest file completed ok, validating");
//	}
//	else {
//	  log.debug("Processing of ingest file failed, unable to vlidate.");
//	  result.messages.add([text:'Unable to process ingest file at this time']);
//	  return result
//	}
//
//	def col_positions = [:]
//	project_data.columnDefinitions?.each { cd ->
//	  log.debug("Assigning col ${cd.name} to position ${cd.cellIndex}");
//	  col_positions[cd.name?.toLowerCase()] = cd.cellIndex;
//	}
//
//	if ( col_positions[PRINT_IDENTIFIER] == null )
//	  result.messages.add(
//		  [text:"Import does not specify a ${PRINT_IDENTIFIER} column", type:"missing_column", col: "${PRINT_IDENTIFIER}"]
//		  );
//
//	if ( col_positions[ONLINE_IDENTIFIER] == null )
//	  result.messages.add([text:"Import does not specify an ${ONLINE_IDENTIFIER} column", type:"missing_column", col: "${ONLINE_IDENTIFIER}"]);
//
//	if ( col_positions[PUBLICATION_TITLE] == null )
//	  result.messages.add([text:"Import does not specify a ${PUBLICATION_TITLE} column", type:"missing_column", col: "${PUBLICATION_TITLE}"]);
//
//	if ( col_positions[HOST_PLATFORM_NAME] == null )
//	  result.messages.add([text:"Import does not specify a ${HOST_PLATFORM_NAME} column", type:"missing_column", col: "${HOST_PLATFORM_NAME}"]);
//
//	if ( col_positions[HOST_PLATFORM_URL] == null )
//	  result.messages.add([text:"Import does not specify a ${HOST_PLATFORM_URL} column", type:"missing_column", col: "${HOST_PLATFORM_URL}"]);
//
////	if ( col_positions[PUBLISHER_NAME] == null )
////	  result.messages.add([text:"Import does not specify a ${PUBLISHER_NAME} column", type:"missing_column", col: "${PUBLISHER_NAME}"]);
//    if ( col_positions[PACKAGE_NAME] == null )
//      result.messages.add([text:"Import does not specify a ${PACKAGE_NAME} column", type:"missing_column", col: "${PACKAGE_NAME}"]);
//      
//    // Check the cell content here...
//    validateContent (project_data, col_positions, result)
    
	if ( result.messages?.size() > 0 ) {
	  log.error("validation has messages: a failure: ${result.messages}");
	  result.status = false;
	}
	else {
	  log.debug("No messages, file valid");
//	  result.messages.add([text:'Checked in file passes GoKB validation step, proceed to ingest']);
	}

	result
  }
  
  /**
   * Do some validation on the content here.
   */
  def validateContent (project_data, col_positions, result) {
    
    // Only check the content if the status is correct.
    if (result.status) {
    
      // Go through the data and see whether each row is valid.
      def rowCount = 1
      
      // Keep track of package ids in this doc.
      Set packageIdentifiers = []
      project_data.rowData.each { datarow ->
      
        // Check the presence of the name first.
        def pkg_name_pos = col_positions[PACKAGE_NAME]
        
        if (pkg_name_pos != null) {
          
          // Check the value of package name here.
          def value = getRowValue(datarow,col_positions,PACKAGE_NAME)
          if (!value || value == "") {
            result.messages.add([text:"Row ${rowCount} contains no data for column ${PACKAGE_NAME}", type:"data_invalid", col: "${PACKAGE_NAME}"]);
          } else {
            // Add to the list of package ids.
            packageIdentifiers << value.toString()
          }
        }
        rowCount ++
      }
      
      // Check existing packages.
      if (packageIdentifiers) {
		def q = ComboCriteria.createFor(Package.createCriteria())
		def existingPkgs = q.list {
		  and {
			  q.add ("ids.namespace.value", "eq", 'gokb-pkgid')
			  q.add ("ids.value", "in", [packageIdentifiers])
		  }
		}
        
        if (existingPkgs) {
          // Get the package ids that cause the issue.
          Set offendingIds = []
          existingPkgs.each {pkg ->
            pkg.ids.each {Identifier theId ->
              if (packageIdentifiers.contains(theId.value)) offendingIds << theId.value
            }
          }
          
          // Add a message.
          result.messages.add([text:"Data present in column \"${PACKAGE_NAME}\" would result in an attemped package update.", type:"data_invalid", col: "${PACKAGE_NAME}", vals: (offendingIds)]);
        }
      }
    }
  }

  /**
   * Estimate the number of each component that would be Created/Updated as a result of ingesting this data.
   */
  def estimateChanges(project_data, project_id = null) {

	// The result object.
	def result = []

	// Default all our counters.
	long ctr             = 0
	long titleRows       = 0
	long existingTitles  = 0
	long newPkgs         = 0
	long existingPlats   = 0
	long newPubs		 = 0
	long existingPubs	 = 0

	// Read in the column positions.
	def col_positions = [:]
	project_data.columnDefinitions.each { cd ->
	  col_positions[cd.name?.toLowerCase()] = cd.cellIndex;
	}

	// All identifiers for this title.
	def identifiers = []
	project_data.columnDefinitions?.each { cd ->
	  def cn = cd.name?.toLowerCase()
	  if (cn.startsWith(IDENTIFIER_PREFIX) ) {
		def idparts = cn.split(/\./)
		if ( idparts.length == 3 ) {
//		  if ( ( idparts[2] == 'issn' ) || (idparts[2] == 'eissn') ) {
//			// Skip issn/eissn.
//		  }
//		  else {
//			identifiers.add([type:idparts[2],colno:cd.cellIndex])
//		  }
		  
		  identifiers.add([type:idparts[2],colno:cd.cellIndex])
		}
	  }
	}

	log.debug("Using col positions: ${col_positions}, additional identifiers: ${identifiers}")
	
	// Combine to create the package Identifier.
	def default_pkg_identifier = null
	
	// If a project id has been supplied
	if (project_id != null) {
	  log.debug("Using refine project id ${project_id}.")

	  // Check the package.
	  RefineProject project = RefineProject.get(project_id)
	  
	  // Check that the project exists...
	  if (project) {
		
		log.debug("Refine project exists. Use to generate the default pkg_id.")

		// The provider.
		Org provider = project.provider

		// Set the default pkg id to use when no value supplied.
		default_pkg_identifier = "${provider.name}:${project_id}"
	  }
	} 
	
	// Could not create default package id. Assume same new package for each blank row.
	if (!default_pkg_identifier) {
	  log.debug("No refine project id supplied. Assuming blank rows are added to the same new package.")
	}

	// Create the sets for processing after run through.
	Set platformNames   	= []
	Set packageIdentifiers 	= []
	Set publisherIds		= []

	log.debug("Finding existing titles...");

	// Go through each row and build up the tipp criteria.
	def tiCrit = ComboCriteria.createFor(TitleInstance.createCriteria())
	existingTitles = tiCrit.get {
	  
	  or {

		project_data.rowData.each { datarow ->
		  if ( datarow.cells[col_positions[PUBLICATION_TITLE]] ) {

			def host_platform_name = jsonv(datarow.cells[col_positions[HOST_PLATFORM_NAME]])
			def host_norm_platform_name = host_platform_name ? host_platform_name.toLowerCase().trim() : null;

			// Just add the normname to the platforms list.
			platformNames << host_norm_platform_name
			
			// Package ID
			def pkg_id	= getRowValue(datarow,col_positions,PACKAGE_NAME)
			pkg_id = pkg_id?.trim()
			if (!pkg_id || pkg_id == "") {
			  pkg_id = default_pkg_identifier
			}
			
			packageIdentifiers << pkg_id.toString()
			
			// Lookup a publisher ID if present.
			def pub_id = componentLookupService.lookupComponent ( getRowValue(datarow,col_positions,PUBLISHER_NAME) )
			if (pub_id) publisherIds << pub_id

//			// (e)issns.
//			def issn    = jsonv(datarow.cells[col_positions[PRINT_IDENTIFIER]])
//			def eissn   = jsonv(datarow.cells[col_positions[ONLINE_IDENTIFIER]])
//
//			// issn query.
//			if (issn != null) {
//			  and {
//				tiCrit.add ("ids.namespace.value", "eq", 'issn')
//				tiCrit.add ("ids.value", "eq", issn)
//			  }
//			}
//
//			// eissn query
//			if (eissn != null) {
//			  and {
//				tiCrit.add ("ids.namespace.value", "eq", 'eissn')
//				tiCrit.add ("ids.value", "eq", eissn)
//			  }
//			}

			// Each identifier type.
			identifiers.each { ai ->
			  
			  // The value.
			  def val = jsonv(datarow.cells[ai.colno])
			  
			  if (val) {
				and {
				  tiCrit.add ("ids.namespace.value", "eq", ai.type)
				  tiCrit.add ("ids.value", "eq", val)
				}
			  }
			}

			// increment the titleRows counter.
			titleRows ++
		  }

		  // Increment the row counter.
		  ctr ++
		}
	  }

	  projections {
		countDistinct("id")
	  }
	}
	
	// Try and find a package for the provider with the name entered.
	def q = ComboCriteria.createFor(Package.createCriteria())
	def existingPkgs = q.get {
	  and {
  	    q.add ("ids.namespace.value", "eq", 'gokb-pkgid')
  	    q.add ("ids.value", "in", [packageIdentifiers])
	  }
	  
	  projections {
	   countDistinct ("id")
	  }
	}

	// New packages.
	newPkgs = packageIdentifiers.size() - existingPkgs
	result << [ type : "packages", "new" : (newPkgs), "updated" : existingPkgs ]
	

	// We should now have a query that we can execute to determine (roughly) how many Tipps will be added.
	result << [ type : "titles", "new" : (titleRows - existingTitles), "updated" : existingTitles ]

	// Host platform criteria...
	DetachedCriteria platCrit = new DetachedCriteria(Platform).build {
	  distinct ("id")
	  'in' ("normname", platformNames)
	}

	// Run a count.
	existingPlats = platCrit.count()
	result << [ type : "platforms", "new" : (platformNames.size() - existingPlats), "updated" : existingPlats ]
	
	// Distinct listed publishers.
	if (publisherIds.size() > 0) {
	  def publishers = Org.createCriteria().listDistinct {
		'in' ("id", publisherIds)
	  }
	  
	  // Check ones that haven't yet published.
	  publishers.each { Org publisher ->
		if (publisher.getPublishedTitles().size() == 0) {
		  newPubs ++
		}
	  }
	}
	
	// Existing publishers
	existingPubs = publisherIds.size() - newPubs
	result << [ type : "publishers", "new" : newPubs, "updated" : existingPubs ]

	log.debug("Estimate changes complete...${result}");
	// Return the result.
	result
  }


//  def getIdentifierComponent(namespace, value) {
//	def result = null
//	def pkg_identifier_ns = IdentifierNamespace.findByValue('gokb-pkgid') ?: new IdentifierNamespace(value:'gokb-pkgid').save()
//	result = Identifier.findByNamespaceAndValue(pkg_identifier_ns, value)
//	result;
//  }

  /**
   *  Ingest a parsed project. 
   *  @param project_data Parsed map of project data
   */
  def ingest(project_data, project_id) {
	// Return result.
	def result = [:]
	Set<String> skipped_titles = []
	try {
	  log.debug("Ingest");

	  def project

	  // Update the project record.
	  RefineProject.withNewTransaction { TransactionStatus status ->

		log.debug ("Trying to update the refine project in a new transaction.")
		project = RefineProject.get(project_id)

		log.debug ("Project ${project}")
		result.status = project_data ? true : false
		result.messages = []

		project.progress = 0
		project.setProjectStatus(RefineProject.Status.INGESTING)
		project.save(failOnError:true)
		
		// Clear the skipped_titles
		project.getSkippedTitles().clear()

		log.debug ("Updated the project.")

		// Flush the status.
		status.flush()

		log.debug ("Forcibly flushed the session.")
	  }

	  def col_positions = [:]
	  project_data.columnDefinitions.each { cd ->
		col_positions[cd.name?.toLowerCase()] = cd.cellIndex;
	  }

	  // Group all identifiers together.
	  def identifiers = []
	  project_data.columnDefinitions?.each { cd ->
		def cn = cd.name?.toLowerCase()
		if (cn.startsWith(IDENTIFIER_PREFIX) ) {
		  String[] idparts = cn.split(/\./)
		  if ( idparts.length == 3 ) {
//			if ( ( idparts[2] == 'issn' ) || (idparts[2] == 'eissn') ) {
//			  // Skip issn/eissn.
//			}
//			else {
//			  identifiers.add([type:idparts[2],colno:cd.cellIndex])
//			}
			
			// Add all identifiers. Class 1 IDs are handled later on in the process.
			identifiers.add([type:idparts[2],colno:cd.cellIndex])
		  }
		}
	  }

	  def gokb_additional_props = []
	  // Extract any gokb scoped fields we are going to store as extra properties
	  RefineProject.withNewTransaction { TransactionStatus status ->

		project_data.columnDefinitions?.each { cd ->
		  def cn = cd.name?.toLowerCase()
		  if (cn.startsWith('gokb.') ) {
			def prop_name = cn.substring(5,cn.length());
			def prop_defn = AdditionalPropertyDefinition.findBypropertyName(prop_name) ?: new AdditionalPropertyDefinition(propertyName:prop_name).save(flush:true);
			gokb_additional_props.add([name:prop_name, col:cd.cellIndex, pd:prop_defn]);
		  }
		}
	  }

	  log.debug("Using col positions: ${col_positions}, additional identifiers: ${identifiers}");

	  int ctr = 0
	  boolean row_level_problems = false

	  // Go through each row.
	  project_data.rowData.each { datarow ->

		// Transaction for each row.
		RefineProject.withNewTransaction { TransactionStatus status ->

		  log.debug("Row ${ctr} ${datarow}");
		  if ( datarow.cells[col_positions[PUBLICATION_TITLE]] ) {

			try {

			  def ids = []
			  identifiers.each { ai ->
				// The value.
				def val = jsonv(datarow.cells[ai.colno])
				if (val) {
				  ids.add([type:ai.type, value:(val)])
				}
			  }

			  // Title Instance
			  log.debug("Looking up title...(ids: ${ids})")
//			  def title_info = titleLookupService.find(
//				  jsonv(datarow.cells[col_positions[PUBLICATION_TITLE]]),
//				  jsonv(datarow.cells[col_positions[PRINT_IDENTIFIER]]),
//				  jsonv(datarow.cells[col_positions[ONLINE_IDENTIFIER]]),
//				  ids,
//				  getRowValue(datarow,col_positions,PUBLISHER_NAME));
			  
			  // Lookup the title.
			  def title_info = titleLookupService.find(
				jsonv(datarow.cells[col_positions[PUBLICATION_TITLE]]),
				getRowValue(datarow,col_positions,PUBLISHER_NAME),
				ids);
			  
			  // If we match a title then ingest...
			  if (title_info != null) {

				// Platform.
				def host_platform_url = jsonv(datarow.cells[col_positions[HOST_PLATFORM_URL]])
				def host_platform_name = jsonv(datarow.cells[col_positions[HOST_PLATFORM_NAME]])
//				def host_norm_platform_name = GOKbTextUtils.normaliseString(host_platform_name);

				if ( host_platform_name == null ) {
				  throw new Exception("Host platform name is null. Col is ${col_positions[HOST_PLATFORM_NAME]}. Datarow was ${datarow}");
				}
				
				def platform_info = componentLookupService.lookupComponent(host_platform_name)

//				log.debug("Looking up platform...(${host_platform_url},${host_platform_name},${host_norm_platform_name})");
//				
//				// def platform_info = Platform.findByPrimaryUrl(host_platform_url)
//				def platform_info =  Platform.findByNormname(host_norm_platform_name)
//				if ( !platform_info ) {
//				  log.debug("Creating a new platform... ${host_platform_name}/${host_norm_platform_name}");
//				  // platform_info = new Platform(primaryUrl:host_platform_url, name:host_platform_name, normname:host_norm_platform_name)
//				  platform_info = new Platform(
//					name:host_platform_name,
//					normname:host_norm_platform_name,
//					primaryUrl:getRowValue(datarow,col_positions,HOST_PLATFORM_BASE_URL)
//				  )
//
//				  if (! platform_info.save(failOnError:true) ) {
//					platform_info.errors.each { e ->
//					  log.error(e);
//					}
//				  }
//				}
	
				// Does the row specify a package identifier?
				def pkg_identifier_from_row = getRowValue(datarow,col_positions,PACKAGE_NAME)

				// The package.
				def pkg = getOrCreatePackage(pkg_identifier_from_row, project.id);

				// Try and lookup a tipp.
				def crit = ComboCriteria.createFor(TitleInstancePackagePlatform.createCriteria())
				def tipp = crit.get {
				  and {
					crit.add ("title", "eq", title_info)
					crit.add ("pkg", "eq", pkg)
					crit.add ("hostPlatform", "eq", platform_info)
				  }
				}

				// We have a Tipp.
				if ( !tipp ) {
				  log.debug("Create new tipp")
				  tipp = new TitleInstancePackagePlatform(
					  title:title_info,
					  pkg:pkg,
					  hostPlatform:platform_info,
					  startDate:parseDate(getRowValue(datarow,col_positions,DATE_FIRST_PACKAGE_ISSUE)),
					  startVolume: getRowValue(datarow,col_positions,VOLUME_FIRST_PACKAGE_ISSUE),
					  startIssue:getRowValue(datarow,col_positions,NUMBER_FIRST_PACKAGE_ISSUE),
					  endDate:parseDate(getRowValue(datarow,col_positions,DATE_LAST_PACKAGE_ISSUE)),
					  endVolume:getRowValue(datarow,col_positions,VOLUME_LAST_PACKAGE_ISSUE),
					  endIssue:getRowValue(datarow,col_positions,NUMBER_LAST_PACKAGE_ISSUE),
					  embargo:getRowValue(datarow,col_positions,EMBARGO_INFO),

					  //TODO: Coverage depth now defaults only for this phase. Commenting out for now.
					  //					coverageDepth:getRowValue(datarow,col_positions,COVERAGE_DEPTH),
					  coverageNote:getRowValue(datarow,col_positions,COVERAGE_NOTES),
					  url:host_platform_url
					  )

				  // Add each property in turn.
				  gokb_additional_props.each { apd ->
					// Done this way because I was worried about the prop defn crossing the transaction start boundary above
					def prop_defn = AdditionalPropertyDefinition.findBypropertyName(apd.prop_name)
					if ( prop_defn != null ) {
					  def ap = new KBComponentAdditionalProperty( propertyDefn:prop_defn, apValue:getRowValue(datarow,apd.col))
					  tipp.additionalProperties.add (ap)
					}
					else {
					  log.error("Unable to locate property definition with name ${apd.prop_name}");
					}
				  }

				  // Save the tipp.
				  tipp.save(failOnError:true)
				}
				else {
				  // Found the tipp.
				  log.debug("TIPP already present");
				}

			  } else {
			  
			  	// Skip this row. Need to log this and save against the project.
				log.debug("Row ${ctr} has been skipped as the data needs to be rectified in the system before it can be ingested.")
				
				// We store a hash of title joined with package. This isn't ideal.
				// TODO:Review this.
				
				def val = (
				  (getRowValue(datarow, col_positions, PUBLICATION_TITLE) ?: "") +
				  (getRowValue(datarow, col_positions, PACKAGE_NAME) ?: "")
				)
				
				skipped_titles << val.toString()
			  }
			  
			  // Every 25 records we clear up the gorm object cache - Pretty nasty performance hack, but it stops the VM from filling with
			  // instances we've just looked up.
			  if ( ctr % 25 == 0 ) {

				// Clean up the GORM.
				cleanUpGorm()

				// Update project progress indicator, save in db so any observers can see progress
				def project_info = RefineProject.get(project.id)
				project_info.progress = ( ctr / project_data.rowData.size() * 100 )
				project_info.save(failOnError:true)
			  }
			}
			catch ( Exception e ) {
			  log.error("Row level exception",e)
			  result.messages.add([text:"Problem processing row ${e}"])
			  row_level_problems = true

			  // Rollback the transaction.
			  status.setRollbackOnly()
			}
		  }
		  else {
			log.debug("Row ${ctr} seems to be a null row. Skipping");
			result.messages.add([text:"Row ${ctr} seems to be a null row. Skipping"]);
		  }
		  ctr++

		  // Forcibly flush the session.
		  //      status.flush()
		}
	  }

	  if ( row_level_problems ) {
		log.error("\n\n\n***** There were row level exceptions *****\n\n\n");
	  }

	  // Wrap in with transaction.
	  RefineProject.withNewTransaction { TransactionStatus status ->
		
    	  // Update the project file.
    	  def project_info = RefineProject.load(project.id)
    
    	  // If any rows with data have been skipped then we need to set them against the,
    	  // project here, for reporting back into refine.
    	  if (skipped_titles) {
    
    		// Partially ingested
    		project_info.setProjectStatus (RefineProject.Status.PARTIALLY_INGESTED)
    
    	  } else {
    
    		// Set to ingested.
    		project_info.setProjectStatus (RefineProject.Status.INGESTED)
    	  }
    
    	  // Update the skipped rows and the progress.
    	  project_info.getSkippedTitles().addAll(skipped_titles)
    	  project_info.progress = 100;
    
    	  // Save the project.
    	  project_info.save(failOnError:true, flush:true)
		  
		  // Force the session to flush
		  status.flush()
	  }
	}
	catch ( Exception e ) {
	  def project_info = RefineProject.get(project_id)
	  log.error("Problem processing project ingest.",e);
	  result.messages.add([text:"Problem processing project ingest. ${e}"])
	  project_info.progress = 100;
	  project_info.setProjectStatus (RefineProject.Status.INGEST_FAILED)
	  project_info.save(failOnError:true);
	  // ToDo: Steve.. can you figure out a way to log the exception and pass it back to refine?
	}
	finally {
	  log.debug("Ingest process completed");
	}

	result
  }

  def getRowValue(datarow, col_positions, colname) {
	def result = null
	if ( col_positions[colname] != null ) {
	  result = jsonv(datarow.cells[col_positions[colname]])
	}
	result
  }

  def jsonv(v) {
	def result = null
	if ( v ) {
	  if ( !v.equals(null) ) {
		result = "${v.v}"
	  }
	}
	result
  }


  /**
   *  Read an uploaded refine .tar.gz file, uncompress and create a map containing all the data. This is in memory,
   *  but our package files should never be large enough to cause a problem.
   *  @param zipFilename .tar.gz file to extract.
   *  @return Map containing parsed project data
   */
  def extractRefineproject(String zipFilename) {
	def result = null;

	try {
	  def full_filename = grailsApplication.config.project_dir + zipFilename

	  log.debug("Extract ${full_filename}");

	  FileInputStream fin = new FileInputStream(full_filename);
	  GzipCompressorInputStream gzIn = new GzipCompressorInputStream(fin);
	  TarArchiveInputStream tin = new TarArchiveInputStream(gzIn)
	  TarArchiveEntry ae = tin.getNextTarEntry()
	  while ( ae ) {
		log.debug("Processing archive entry: ${ae} ${ae.name} isFile:${ae.isFile()}");
		switch ( ae.name ) {
		  case 'metadata.json':
			log.debug("Handle metadata");
			break;
		  case 'data.zip':
			def temp_data_zipfile
			try {
			  log.debug("Handle Data.. create zipfile. need to copy ${ae.getSize()} bytes from tin to a buffer and re-read as a zip file");

			  // Copy bytes from tar stream into temp zip file
			  temp_data_zipfile = File.createTempFile('gokb_','_refinedata.zip',null)
			  FileOutputStream fos = new FileOutputStream(temp_data_zipfile);
			  int bytes_to_read = ae.getSize()
			  byte[] buffer = new byte[4096]
			  while (bytes_to_read) {
				int bytes_read = tin.read(buffer,0,4096)
				log.debug("Copying ${bytes_read} bytes to temp file");
				fos.write(buffer, 0, bytes_read)
				bytes_to_read -= bytes_read
			  }
			  fos.flush()
			  fos.close();

			  result = extractRefineDataZip(temp_data_zipfile)
			}
			finally {
			  if ( temp_data_zipfile ) {
				try {
				  temp_data_zipfile.delete();
				}
				catch ( Throwable t ) {
				}
			  }
			}
			break;
		  default:
			break;
		}

		ae = tin.getNextTarEntry()
	  }
	  tin.close();
	  gzIn.close();
	  fin.close();
	}
	catch ( Exception e ) {
	  log.error("Unexpected error trying to extract refine data.",e);
	  e.printStackTrace();
	}

	result
  }

  def extractRefineDataZip (def zip_file) {

	def result=null

	// Open temp zip file as a zip object
	if ( zip_file ) {
	  java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip_file)
	  log.debug("Getting data.txt")
	  java.util.zip.ZipEntry ze = zf.getEntry('data.txt')
	  if ( ze ) {
		log.debug("Got data.txt")
		result = [:]
		result.processingCompleted = false;
		processData(result, zf.getInputStream(ze));
	  }
	  else {
		log.error("Problem getting data.txt");
	  }
	}
	else {
	  log.debug("extractRefineDataZip: zip file is null");
	}

	result
  }

  def processData(result, is) {
	log.debug("processing refine data.txt");
	def bis = new BufferedReader(new InputStreamReader(is));

	// First line is the refine version
	result.refineVersion = bis.readLine()
	log.debug("Reported refine version is ${result.refineVersion}");

	// 2.5

	// Header info
	result.columnModel=valuePart(bis.readLine())
	result.maxCellIndex=valuePart(bis.readLine())
	result.keyColumnIndex=valuePart(bis.readLine())
	result.columnCount=Integer.decode(valuePart(bis.readLine()))

	log.debug("Setting up column definitions");
	result.columnDefinitions = []
	for ( int i=0; i<result.columnCount; i++ ) {
	  log.debug("Reading column ${i}");
	  result.columnDefinitions.add(JSON.parse(bis.readLine()));
	}

	result.columnGroupCount = Integer.decode(valuePart(bis.readLine()))
	for (int i=0; i<result.columnGroupCount; i++ ) {
	  // Skipping column group info
	  def row = bis.readLine()
	}

	if ( bis.readLine() != '/e/' ) {
	  log.error("Unexpected row!");
	}

	result.history = valuePart(bis.readLine())

	result.pastEntryCount = Integer.decode(valuePart(bis.readLine()))
	result.pastEntryList = []
	for (int i=0; i<result.pastEntryCount; i++ ) {
	  // Skipping past entry
	  result.pastEntryList.add(JSON.parse(bis.readLine()));
	}

	result.futureEntryCount = Integer.decode(valuePart(bis.readLine()))
	for (int i=0; i<result.futureEntryCount; i++ ) {
	  // Skipping future entry
	  def row = bis.readLine()
	}

	if ( bis.readLine() != '/e/' ) {
	  log.error("Unexpected row!");
	}
	else {
	  log.debug("Encountered second /e/ in correct position, in to overlay model and rowCount");
	}

	def next_param = bis.readLine()
	if ( next_param.startsWith('overlayModel') ) {
	  result.overlayModel = valuePart(bis.readLine())
	  next_param = bis.readLine()
	}

	if (  next_param.startsWith('rowCount') ) {
	  result.rowCount = Integer.decode(valuePart(next_param))
	}
	else {
	  log.error("expected row count, got row ${next_param}");
	  result.rowCount = 0;
	}

	result.rowData = []

	for (int i=0; i<result.rowCount; i++ ) {
	  def row = bis.readLine()
	  // log.debug("Row ${i}");
	  result.rowData.add(JSON.parse(row))
	  // Skipping row
	}

	result.processingCompleted = true;

	bis.close();
  }

  def valuePart(s) {
	int equalsPos = s.indexOf('=');
	def result = s.substring(equalsPos+1, s.length());
	log.debug("valuePart(${s})=${result}");
	result;
  }

  def cleanUpGorm() {
	log.debug("Clean up GORM");

	// Get the current session.
	def session = sessionFactory.currentSession

	// flush and clear the session.
	session.flush()
	session.clear()

	// Clear the property instance map.
	propertyInstanceMap.get().clear()
  }

  def parseDate(datestr) {
	def parsed_date = null;
	if ( datestr && ( datestr.length() > 0 ) )
	  for(Iterator i = possible_date_formats.iterator(); ( i.hasNext() && ( parsed_date == null ) ); ) {
		try {
		  parsed_date = i.next().parse(datestr.replaceAll('-','/'));
		}
		catch ( Exception e ) {
		}
	  }
	parsed_date
  }

  def extractRules(parsed_data, project) {
	if ( ( project.provider != null ) &&
	( parsed_data != null ) ) {
	  log.debug("extracting rules, provider is ${project.provider.id}");
	  def provider = Org.get(project.provider.id)
	  // finally, rules extraction
	  parsed_data.pastEntryList.each { r ->
		log.debug("Consider rule: ${r}");
		def fingerprint = null
		def scope = "provider" // default scope to provider
		if ( r.operation ) {
		  switch ( r.operation.op ) {
			case 'core/column-rename':
			// Column Rename
			  fingerprint = "${r.operation.op}:${r.operation.oldColumnName}"
			  break;
			case 'core/text-transform':
			case 'core/mass-edit':
			  fingerprint = "${r.operation.op}:${r.operation.columnName}"
			  break
			default:
			  log.debug("Generic rules handling");
			  break;
		  }
		}

		if ( fingerprint ) {
		  def rule_in_db = Rule.findByScopeAndProviderAndFingerprint('provider',provider,fingerprint)
		  if ( !rule_in_db ) {
			rule_in_db = new Rule(
				scope:scope,
				provider: provider,
				fingerprint: fingerprint,
				ruleJson: "${r.operation as JSON}",
				description: "${r.operation.description}"
				)
			if ( rule_in_db.save(failOnError:true) ) {
			}
			else {
			  rule_in_db.errors.each { e ->
				log.error("${e}");
			  }
			}
		  }
		}
	  }
	}
	else {
	  log.error("Provider or parsed data not set, cannot establish rules!");
	}
  }


  /**
   *  Look at the project header, extract fingerprints, try to find matching rules that could be applied
   *  to this upload
   */

  def findRules(parsed_project_file, provider) {

	def result = []
	def extracted_fingerprints = []

	// Iterate over columns
	parsed_project_file.columnDefinitions.each { cd ->
	  log.debug("Considering column ${cd.name}");
	  def fingerprint="core/column-rename:${cd.name}"
	  extracted_fingerprints.add(fingerprint)
	}

	extracted_fingerprints.each { fp ->
	  if (provider) findRulesByFingerprint('provider',provider,fp,result)
	  findRulesByFingerprint('global',null,fp,result)
	}

	result
  }

  def findRulesByFingerprint(scope,provider,fp,ruleset) {
	def rule_in_db
	if ( provider ) {
	  log.debug("Looking for rules ${scope}:${provider}:${fp}")
	  rule_in_db = Rule.findByScopeAndProviderAndFingerprint(scope,provider,fp)

	} else {
	  rule_in_db = Rule.findByScopeAndFingerprint(scope,fp)
	}
	if ( rule_in_db ) {
	  log.debug("got matching rule ${rule_in_db}");
	  ruleset.add(rule_in_db)
	}
  }

  def getOrCreatePackage(String identifier, project_id) {

	log.debug("getOrCreatePackage(identifier:${identifier},project:${project_id})");

	// Read the project.
	RefineProject project = RefineProject.get(project_id)

	// The provider.
	Org provider = project.provider

	// If identifier supplied, then use that. Otherwise, generate.
	def pkg_identifier = identifier ?: "${provider.name}:${project.id}"

	// Try and find a package for the provider with the name entered.
	log.debug("identifier will be ${pkg_identifier}")
	def pkg = null;

    def q = ComboCriteria.createFor(Package.createCriteria())
    def pkg_list = q.list {
      and {
        q.add ("ids.namespace.value", "eq", 'gokb-pkgid')
        q.add ("ids.value", "eq", pkg_identifier)
      }
    }

    log.debug("Lookup of package with identifier ${pkg_identifier} returns ${pkg_list.size()} entries");

    if ( pkg_list.size() == 0 ) {
      log.debug("New package")
    }
    else if (  pkg_list.size() == 1 ) {
      log.debug("Identified a package")
      pkg = pkg_list.get(0);
    }
    else {
      throw new Exception("Multiple packages with specififed identifier. This should never happen");
    }

	// Package found?
	if (!pkg) {

//	  Package.withNewTransaction { tranStat ->
		log.debug("New package with identifier ${pkg_identifier} for ${provider.name}");

		// Create a new package.
		pkg = new Package(
			provider:   (provider),
			lastProject:project
		)

		// Add a new identifier to the package.
		Identifier new_identifier = Identifier.lookupOrCreateCanonicalIdentifier('gokb-pkgid', pkg_identifier)
		log.debug("create new combo to link package to identifier. pkg=${pkg.id}, new_id:${new_identifier.id}");
		pkg.ids.add (new_identifier)
    
        // Need to set the name to mirror the Identifier.
        pkg.name = new_identifier.value
		
		// Save the package.
		pkg.save(failOnError:true, flush:true)
//	  }
	}
	else {
	  log.debug("Got existing package ${pkg.id}");
	}
	pkg
  }
}
