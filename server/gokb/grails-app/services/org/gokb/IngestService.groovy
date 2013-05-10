package org.gokb

import org.gokb.refine.*;
import org.gokb.cred.*;
import org.apache.commons.compress.compressors.gzip.*
import org.apache.commons.compress.archivers.tar.*
import org.apache.commons.compress.archivers.*
import grails.converters.JSON
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
      log.debug("Processing of ingest file completed ok, validating");
      result.messages.add([text:'Unable to process ingest file at this time']);
      return result
    }

    def print_identifier_col = null;
    def online_identifier_col = null;
    def publication_title_col = null;
    def platform_host_name_col = null;
    def platform_host_url_col = null;

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
   *  Ingest a parsed project. 
   *  @param project_data Parsed map of project data
   */
  def ingest(project_data, project_id) {
    try {
      log.debug("Ingest");
  
      def project = RefineProject.get(project_id);

      def result = [:]
      result.status = project_data ? true : false
      result.messages = []
  
      project.progress = 0;
      project.save(flush:true, failOnError:true)
  
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
          def prop_defn = AdditionalPropertyDefinition.findBypropertyName(prop_name) ?: new AdditionalPropertyDefinition(propertyName:prop_name);
          gokb_additional_props.add([name:prop_name, col:cd.cellIndex, pd:prop_defn]);
        }
      }
  
      log.debug("Using col positions: ${col_positions}, additional identifiers: ${additional_identifiers}");
  
      int ctr = 0
      project_data.rowData.each { datarow ->
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
				primaryUrl:host_platform_url
			  )
              if (! platform_info.save(failOnError:true, flush:true) ) {
                platform_info.errors.each { e ->
                  log.error(e);
                }
              }
            }
    
            // Does the row specify a package?
//            def pkg_name_from_row = getRowValue(datarow,col_positions,PACKAGE_NAME) ?: "${project.id}" //:${project.provider.name}
			def pkg_name_from_row = "${project.id}"
//            def pkg_id = "${project.provider.name}:${pkg_name_from_row}"
            def pkg = getOrCreatePackage(pkg_name_from_row, project);
  
            // TIPP
//            def tipp = TitleInstancePackagePlatform.findByTitleAndPkgAndPlatform(title_info, pkg, platform_info)
			def crit = ComboCriteria.createFor(TitleInstancePackagePlatform.createCriteria())
            def tipp = crit.get {
              and {
				crit.add ("title", "eq", title_info)
                crit.add ("pkg", "eq", pkg)
                crit.add ("hostPlatform", "eq", platform_info)
              }
            }
            
            if ( !tipp ) {
              log.debug("Create new tipp");
              tipp = new TitleInstancePackagePlatform(title:title_info,
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
                                                      hostPlatformURL:host_platform_url)

              gokb_additional_props.each { apd ->
                tipp.additionalProperties.add (
				  new KBComponentAdditionalProperty(
					propertyDefn:apd.pd,
                    apValue:getRowValue(datarow,apd.col)
				  )
				)
              }
              
  
              if ( !tipp.save(failOnError:true,flush:true) ) {
                tipp.errors.each { e ->
                  log.error("problem saving tipp ${e}");
                }
              }
  
  
            // publication_title print_identifier online_identifier date_first_issue_online num_first_vol_online num_first_issue_online date_last_issue_online num_last_vol_online num_last_issue_online title_id embargo_info coverage_depth coverage_notes publisher_name DOI platform_name platform_role platform_title_url platform_name2 platform_role2 platform_title_url2
  
            }
            else {
              log.debug("TIPP already present");
            }
  
            // Every 25 records we clear up the gorm object cache - Pretty nasty performance hack, but it stops the VM from filling with
            // instances we've just looked up.
            if ( ctr % 25 == 0 ) {
              cleanUpGorm()
//              pkg = Package.findByIdentifier("project:${project.id}");
			  
              // Update project progress indicator, save in db so any observers can see progress
              def project_info = RefineProject.get(project.id)
              project_info.progress = ( ctr / project_data.rowData.size() * 100 )
              project_info.save(flush:true, failOnError:true)
            }

          } 
          catch ( Exception e ) {
            log.error("Row level exception",e)
            result.messages.add([text:"Problem processing row ${e}"])
          }
        }
        else {
          log.debug("Row ${ctr} seems to be a null row. Skipping");
          result.messages.add([text:"Row ${ctr} seems to be a null row. Skipping"]);
        }
        ctr++
      }

      def project_info = RefineProject.get(project.id)
      project_info.progress = 100;
      project_info.save(flush:true, failOnError:true);
    }
    catch ( Exception e ) {
      log.error("Problem processing project ingest.",e);
      result.messages.add([text:"Problem processing project ingest. ${e}"])
      project_info.progress = 100;
      project_info.save(flush:true, failOnError:true);
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
    def session = sessionFactory.currentSession
    session.flush()
    session.clear()
    propertyInstanceMap.get().clear()
  }

  def parseDate(datestr) {
    def parsed_date = null;
    if ( datestr && ( datestr.length() > 0 ) )
    for(Iterator i = possible_date_formats.iterator(); ( i.hasNext() && ( parsed_date == null ) ); ) {
      try {
        parsed_date = i.next().parse(datestr);
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
            if ( rule_in_db.save(flush:true, failOnError:true) ) {
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

  def getOrCreatePackage(String name, RefineProject project) {
	//TODO: Need to sort this identifier out.
//    def pkg = Package.findByIdentifier(identifier);
	
	// The provider.
	Org provider = project.provider
	
	// Create the package name.
	def pkg_name = "${provider.name}:${name}"
	
	// Try and find a package for the provider with the name entered.
	def q = ComboCriteria.createFor(Package.createCriteria())
	def pkg = q.get {
	  q.add ("name", "eq", pkg_name)
	  q.add ("provider", "eq", provider)
	}
	
	// Package found.
    if (!pkg) {
      log.debug("New package with name ${name} for ${provider.name}");
	  pkg = new Package(
		  name:(pkg_name),
		  provider: (provider),
		  packageStatus:RefdataCategory.lookupOrCreate("Pkg.Status", "Current"),
		  packageScope:RefdataCategory.lookupOrCreate("Pkg.Scope", "Front File"),
		  breakable:RefdataCategory.lookupOrCreate("Pkg.Breakable", "Y"),
//		  parent:RefdataCategory.lookupOrCreate("Pkg.Parent", "N"),
		  global:RefdataCategory.lookupOrCreate("Pkg.Global", "Y"),
		  fixed:RefdataCategory.lookupOrCreate("Pkg.Fixed", "Y"),
		  consistent:RefdataCategory.lookupOrCreate("Pkg.Consisitent", "N"),
		  lastProject:project
	  ).save(failOnError:true, flush:true)

      // create a Combo linking this package to it's content provider
//      def cp_combo = new Combo(fromComponent:project.provider,
//                               toComponent:pkg,
//                               type:RefdataCategory.lookupOrCreate("Combo.Type", "ContentProvider"),
//                               status:RefdataCategory.lookupOrCreate("Combo.Status", "Active"))
//
//
//      cp_combo.save()
    }
    else {
      log.debug("Got existing package ${pkg.id}");
    }
    pkg
  }
}
