package org.gokb

import org.gokb.refine.*;
import org.gokb.cred.*;
import org.springframework.transaction.TransactionStatus
import org.apache.commons.compress.compressors.gzip.*
import org.apache.commons.compress.archivers.tar.*
import org.apache.commons.compress.archivers.*
import org.codehaus.groovy.grails.orm.hibernate.HibernateSession
import grails.converters.JSON
import grails.gorm.DetachedCriteria
import grails.orm.HibernateCriteriaBuilder
import java.text.SimpleDateFormat


class IngestService {

  // Automatically injected services from grails-app/services
  def grailsApplication
  def titleLookupService
  def sessionFactory
  def propertyInstanceMap = org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin.PROPERTY_INSTANCE_MAP
  def possible_date_formats = [
    new SimpleDateFormat('dd/MM/yyyy'),
    new SimpleDateFormat('yyyy/MM/dd'),
    new SimpleDateFormat('dd/MM/yy'),
    new SimpleDateFormat('yyyy/MM'),
    new SimpleDateFormat('yyyy')
  ];


  static String PUBLICATION_TITLE = 'publicationtitle'
  static String DATE_FIRST_PACKAGE_ISSUE = 'datefirstpackageissue'
  static String VOLUME_FIRST_PACKAGE_ISSUE = 'volumefirstpackageissue'
  static String NUMBER_FIRST_PACKAGE_ISSUE = 'numberfirstpackageissue'
  static String DATE_LAST_PACKAGE_ISSUE = 'datelastpackageissue'
  static String VOLUME_LAST_PACKAGE_ISSUE = 'volumelastpackageissue'
  static String NUMBER_LAST_PACKAGE_ISSUE = 'numberlastpackageissue'

  static String PRINT_IDENTIFIER = 'title.identifier.issn'
  static String ONLINE_IDENTIFIER = 'title.identifier.eissn'
  static String HOST_PLATFORM_NAME = 'platform.host.name'
  static String HOST_PLATFORM_URL = 'platform.host.url'
  static String HOST_PLATFORM_BASE_URL = 'platform.host.base.url'

  static String COVERAGE_DEPTH = 'coveragedepth'
  static String COVERAGE_NOTES = 'coveragenotes'
  static String EMBARGO_INFO = 'kbartembargo'

  static String PACKAGE_NAME = 'package.name'
  static String PUBLISHER_NAME = 'org.publisher.name'

  /**
   *  Validate a parsed project. 
   *  @param project_data Parsed map of project data
   */
  def validate(project_data) {
    log.debug("Validate");

    def result = [:]
    result.status = true
    result.messages = []

    if ( project_data?.processingCompleted ) {
      log.debug("Processing of ingest file completed ok, validating");
    }
    else {
      log.debug("Processing of ingest file failed, unable to vlidate.");
      result.messages.add([text:'Unable to process ingest file at this time']);
      return result
    }

    def col_positions = [:]
    project_data.columnDefinitions?.each { cd ->
      log.debug("Assigning col ${cd.name} to position ${cd.cellIndex}");
      col_positions[cd.name?.toLowerCase()] = cd.cellIndex;
    }

    if ( col_positions[PRINT_IDENTIFIER] == null )
      result.messages.add(
		[text:"Import does not specify a ${PRINT_IDENTIFIER} column", type:"missing_column", col: "${PRINT_IDENTIFIER}"]
	  );

    if ( col_positions[ONLINE_IDENTIFIER] == null )
      result.messages.add([text:"Import does not specify an ${ONLINE_IDENTIFIER} column", type:"missing_column", col: "${ONLINE_IDENTIFIER}"]);

    if ( col_positions[PUBLICATION_TITLE] == null )
      result.messages.add([text:"Import does not specify a ${PUBLICATION_TITLE} column", type:"missing_column", col: "${PUBLICATION_TITLE}"]);

    if ( col_positions[HOST_PLATFORM_NAME] == null )
      result.messages.add([text:"Import does not specify a ${HOST_PLATFORM_NAME} column", type:"missing_column", col: "${HOST_PLATFORM_NAME}"]);

    if ( col_positions[HOST_PLATFORM_URL] == null )
      result.messages.add([text:"Import does not specify a ${HOST_PLATFORM_URL} column", type:"missing_column", col: "${HOST_PLATFORM_URL}"]);
      
    if ( col_positions[PUBLISHER_NAME] == null )
      result.messages.add([text:"Import does not specify a ${PUBLISHER_NAME} column", type:"missing_column", col: "${PUBLISHER_NAME}"]);

    if ( result.messages.size() > 0 ) {
      log.error("validation has messages: a failure: ${result.messages}");
      result.status = false;
    }
    else {
      log.debug("No messages, file valid");
      result.messages.add([text:'Checked in file passes GoKB validation step, proceed to ingest']);
    }

    result
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
    
    // Read in the column positions.
    def col_positions = [:]
    project_data.columnDefinitions.each { cd ->
      col_positions[cd.name?.toLowerCase()] = cd.cellIndex;
    }

    // Track any additional title identifiers.
    def additional_identifiers = []
    project_data.columnDefinitions?.each { cd ->
      def cn = cd.name?.toLowerCase()
      if (cn.startsWith('title.identifier.') ) {
        def idparts = cn.split('.')
        if ( idparts.size == 3 ) {
          if ( ( idparts[2] == 'issn' ) || (idparts[2] == 'eissn') ) {
            // Skip issn/eissn.
          }
          else {
            additional_identifiers.add([type:idparts[2],colno:cd.cellIndex])
          }
        }
      }
    }

    log.debug("Using col positions: ${col_positions}, additional identifiers: ${additional_identifiers}")
    
    // If a project id has been supplied
    if (project_id != null) {
      log.debug("Using refine project id ${project_id}.")
    
      // Check the package.
      RefineProject project = RefineProject.get(project_id)
      
      // The provider.
      Org provider = project.provider
      
      // Combine to create the package Identifier.
      def pkg_identifier = "${provider.name}:${project_id}"
      
      log.debug("Checking for existing package with name ${pkg_identifier}, for provider ${provider.name}.")
      
      // Try and find a package for the provider with the name entered.
      def q = Package.createCriteria()
      def pkg = q.get {
        ids {
          and {
            namespace {
              eq ("value", 'gokb-pkgid')
            }
            eq ("value", pkg_identifier)
          }
        }
      }
      
      // Package found.
      if (!pkg) newPkgs ++
      
    } else {
      log.debug("No refine project id supplied. Assuming new package.")
      newPkgs ++
    }
      
    // Add to the results.
    result << [ type : "packages", "new" : newPkgs, "updated" : (1 - newPkgs) ]
    
    // Create the set for the platforms.
    Set platformNames   = []
    
    // Go through each row and build up the tipp criteria.
    existingTitles = TitleInstance.createCriteria().get {
      ids {
        
        or {
      
          project_data.rowData.each { datarow ->
            if ( datarow.cells[col_positions[PUBLICATION_TITLE]] ) {
              
              def host_platform_name = jsonv(datarow.cells[col_positions[HOST_PLATFORM_NAME]])
              def host_norm_platform_name = host_platform_name ? host_platform_name.toLowerCase().trim() : null;
  
              // Just add the normname to the platforms list.
              platformNames << host_norm_platform_name
              
              // (e)issns.
              def issn    = jsonv(datarow.cells[col_positions[PRINT_IDENTIFIER]])
              def eissn   = jsonv(datarow.cells[col_positions[ONLINE_IDENTIFIER]])
              
              // issn query.
              if (issn != null) {
                and {
                  eq ("value", issn)
                  namespace {
                    eq ("value", "issn") 
                  }
                }
              }
              
              // eissn query
              if (eissn != null) {
                and {
                  eq ("value", eissn)
                  namespace {
                    eq ("value", "eissn")
                  }
                }
              }
      
              // Each additional identifier type.
              additional_identifiers.each { ai ->
                and {
                  eq ("value", datarow.cells[ai.colno])
                  namespace {
                    eq ("value", ai.type)
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
      }
      
      projections {
        countDistinct("id")
      }
    }
    
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
    
    // Return the result.
    result
  }

  /**
   *  Ingest a parsed project. 
   *  @param project_data Parsed map of project data
   */
  def ingest(project_data, project_id) {
	// Return result.
	def result = [:]
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
        project.save(failOnError:true)
		
		log.debug ("Updated the project.")
		
		// Flush the status.
		status.flush()
		
		log.debug ("Forcibly flushed the session.")
      }
  
      def col_positions = [:]
      project_data.columnDefinitions.each { cd ->
        col_positions[cd.name?.toLowerCase()] = cd.cellIndex;
      }

      // Track any additional title identifiers.
      def additional_identifiers = []
      project_data.columnDefinitions?.each { cd ->
        def cn = cd.name?.toLowerCase()
        if (cn.startsWith('title.identifier.') ) {
          def idparts = cn.split('.')
          if ( idparts.size == 3 ) {
            if ( ( idparts[2] == 'issn' ) || (idparts[2] == 'eissn') ) {
              // Skip issn/eissn.
            }
            else {
              additional_identifiers.add([type:idparts[2],colno:cd.cellIndex])
            }
          }
        }
      }

      // Extract any gokb scoped fields we are going to store as extra properties
      def gokb_additional_props = []
      project_data.columnDefinitions?.each { cd ->
        def cn = cd.name?.toLowerCase()
        if (cn.startsWith('gokb.') ) {
          def prop_name = cn.substring(5,cn.length());
          def prop_defn = AdditionalPropertyDefinition.findBypropertyName(prop_name) ?: new AdditionalPropertyDefinition(propertyName:prop_name).save();
          gokb_additional_props.add([name:prop_name, col:cd.cellIndex, pd:prop_defn]);
        }
      }
  
      log.debug("Using col positions: ${col_positions}, additional identifiers: ${additional_identifiers}");
  
      int ctr = 0
      boolean row_level_problems = false
	  
      // Go through each row.
      project_data.rowData.each { datarow ->
		
		// Transaction for each row.
		RefineProject.withNewTransaction { TransactionStatus status ->
		
          log.debug("Row ${ctr} ${datarow}");
          if ( datarow.cells[col_positions[PUBLICATION_TITLE]] ) {
  
            try {
  			
              def extra_ids = []
              additional_identifiers.each { ai ->
                extra_ids.add([type:ai.type, value:datarow.cells[ai.colno]])
              }
  
              // Title Instance
              log.debug("Looking up title...(extra ids: ${extra_ids})")
              def title_info = titleLookupService.find(jsonv(datarow.cells[col_positions[PUBLICATION_TITLE]]),
                                                       jsonv(datarow.cells[col_positions[PRINT_IDENTIFIER]]),
                                                       jsonv(datarow.cells[col_positions[ONLINE_IDENTIFIER]]),
                                                       extra_ids,
                                                       jsonv(datarow.cells[col_positions[PUBLISHER_NAME]]));
  
              // Platform.
              def host_platform_url = jsonv(datarow.cells[col_positions[HOST_PLATFORM_URL]])
              def host_platform_name = jsonv(datarow.cells[col_positions[HOST_PLATFORM_NAME]])
              def host_norm_platform_name = host_platform_name ? host_platform_name.toLowerCase().trim() : null;
  
              if ( host_platform_name == null ) {
                throw new Exception("Host platform name is null. Col is ${col_positions[HOST_PLATFORM_NAME]}. Datarow was ${datarow}");
              }
  
              log.debug("Looking up platform...(${host_platform_url},${host_platform_name},${host_norm_platform_name})");
              // def platform_info = Platform.findByPrimaryUrl(host_platform_url) 
              def platform_info = Platform.findByNormname(host_norm_platform_name)
              if ( !platform_info ) {
                // platform_info = new Platform(primaryUrl:host_platform_url, name:host_platform_name, normname:host_norm_platform_name)
                platform_info = new Platform(
  				name:host_platform_name,
  				normname:host_norm_platform_name,
  				primaryUrl:getRowValue(datarow,col_positions,HOST_PLATFORM_BASE_URL)
  			  )
                if (! platform_info.save(failOnError:true) ) {
                  platform_info.errors.each { e ->
                    log.error(e);
                  }
                }
              }
      
            // Does the row specify a package identifier?
            // TODO: This needs to lookup a column instead of just using null.
  			def pkg_identifier_from_row = null
  
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
					coverageDepth:getRowValue(datarow,col_positions,COVERAGE_DEPTH),
					coverageNote:getRowValue(datarow,col_positions,COVERAGE_NOTES),
					url:host_platform_url
				)
  
  			  	// Add each property in turn.
				gokb_additional_props.each { apd ->
                                  def ap = new new KBComponentAdditionalProperty(
                                          propertyDefn:apd.pd,
                                          apValue:getRowValue(datarow,apd.col))
				  tipp.additionalProperties.add (ap)
				}
                
  			  	// Save the tipp.
				tipp.save(failOnError:true)
              }
              else {
  			  // Found the tipp.
                log.debug("TIPP already present");
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
//		  status.flush()
      	}
      }

      if ( row_level_problems ) {
        log.error("\n\n\n***** There were row level exceptions *****\n\n\n");
      }


      def project_info = RefineProject.get(project.id)
      project_info.progress = 100;
      project_info.save(failOnError:true)
    }
    catch ( Exception e ) {
      log.error("Problem processing project ingest.",e);
      result.messages.add([text:"Problem processing project ingest. ${e}"])
      project_info.progress = 100;
      project_info.save(failOnError:true);
      //ToDo: Steve.. can you figure out a way to log the exception and pass it back to refine?
    }
    finally {
      log.debug("Ingest complete");
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
    if ( project.provider ) {
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
      log.error("Provider not set, cannot establish rules!");
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
    
    // Read the project.
    RefineProject project = RefineProject.get(project_id)
    
    // The provider.
    Org provider = project.provider
	
    // If identifier supplied, then use that. Otherwise, generate.
    def pkg_identifier = "${identifier}"
    
    if (pkg_identifier == null || pkg_identifier == "") {
      
      // Derive the package identifier from the project info.
      pkg_identifier = "${provider.name}:${project.id}"
    }
	
	// Try and find a package for the provider with the name entered.
	def q = ComboCriteria.createFor(Package.createCriteria())
	def pkg = q.get {
      ids {
        and {
          namespace {
            eq ("value", 'gokb-pkgid')
          }
          eq ("value", pkg_identifier)
        }
      }
	}
	
	// Package found?
    if (!pkg) {
      log.debug("New package with identifier ${pkg_identifier} for ${provider.name}");
	  
      // Create a new package.
      pkg = new Package(
		  name:       (provider.name),
		  provider:   (provider),
		  lastProject:project
	  )
      
      // Add a new identifier to the package.
      def new_identifier = new Identifier (
          namespace : new IdentifierNamespace (value: 'gokb-pkgid').save(failOnError:true),
          value : pkg_identifier
        ).save()

      pkg.ids.add(new_identifier)
    
      // Save the package.
      pkg.save(failOnError:true)
    }
    else {
      log.debug("Got existing package ${pkg.id}");
    }
    pkg
  }
}
