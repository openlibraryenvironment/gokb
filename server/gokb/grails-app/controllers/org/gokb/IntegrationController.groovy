package org.gokb

import grails.converters.JSON
import org.springframework.security.access.annotation.Secured;

import org.gokb.cred.*

import au.com.bytecode.opencsv.CSVReader

class IntegrationController {

  def grailsApplication
  def springSecurityService
  def titleLookupService
  def applicationEventService
  def sessionFactory
  def propertyInstanceMap = org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin.PROPERTY_INSTANCE_MAP


  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def index() {
  }
  
  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def assertJsonldPlatform() { 
    def result = [result:'OK']
    def name = request.JSON.'skos:prefLabel'
    def normname = GOKbTextUtils.normaliseString(name)
    def located_entries = KBComponent.findAllByNormname(normname)
    log.debug("assertJsonldPlatform ${name}/${normname}");
    if ( located_entries.size() == 0 ) {
      log.debug("No platform with normname ${normname} - create");
      def new_platform = new org.gokb.cred.Platform(name:name, normname:normname).save()
      result.message="Added new platform"
    }
    else {
      result.message="Entity with that name already exists.."
    }
    render result as JSON
  }

  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def assertJsonldOrg() { 
    // log.debug("assertOrg, request.json = ${request.JSON}");
    def result=[:]
    result.status = true;

    try {

      def name = request.JSON.'skos:prefLabel'

      if ( ( name != null ) && ( name.trim().length() > 0 ) ) {
  
        log.debug("Trying to locate component with ID ${request.JSON.'@id'} name is \"${name}\"");
  
        // Try and match on primary ID
        def located_entries = KBComponent.lookupByIdentifierValue([request.JSON.'@id'.toString()] as String[]);

        if ( located_entries?.size() == 1 ) {
          log.debug("Identified record..");
          enrichJsonLDOrg(located_entries[0], request.JSON)
        }
        else if ( located_entries?.size() == 0 ) {
  
          log.debug("Not identified - try sameAs relations");
  
          if ( request.JSON.'owl:sameAs' != null ) {
            log.debug("Attempt lookup by sameAs : ${request.JSON.'owl:sameAs' as String[]} ");
            located_entries = KBComponent.lookupByIdentifierValue((request.JSON.'owl:sameAs') as String[])
          }
          else {
            log.debug("No owl:sameAs entries found");
          }
  
          if ( located_entries?.size() == 0 ) {
            log.debug("Failed to match on same-as. Attempting primary name match");
            def normname = GOKbTextUtils.normaliseString(name)
            located_entries = KBComponent.findAllByNormname(normname)
            if ( located_entries?.size() == 0 ) {
              log.debug("No match on normalised name ${normname}.. Trying variant names");
              createJsonLDOrg(request.JSON);
            }
            else if ( located_entries?.size() == 1 ) {
               log.debug("Exact match on normalised name ${normname} - good enough");
               enrichJsonLDOrg(located_entries[0], request.JSON)
            }
            else {
              log.error("Multiple matches on normalised name... abandon all hope");
            }
  
          }
          else if ( located_entries?.size() == 1 ) {
             log.debug("Located identifier");
          }
          else {
            log.error("set of SameAs identifiers locate more that one component");
          }
        }
        else {
          log.error("Unique identifier finds multiple components.");
        }
  
        result.status = 'OK'
      }
      else {
        log.error("skipping org [ ${request.JSON.'@id'}] due to null name");
      }
    }
    catch ( Exception e ) {
      log.error("Problem",e)
      result.status = 'ERROR'
    }
    finally {
    }

    render result as JSON
  }

  def createJsonLDOrg(ldjsonorg) {
    log.debug("createJsonLDOrg");
    //             "@id": "http://www.lib.ncsu.edu/ld/onld/00000134" ,
    //         "skos:prefLabel": "A.B. Lundequistska Bokhandeln" ,
    //         "owl:sameAs": [
    //             "http://viaf.org/viaf/152447102" ,
    //             "http://isni-url.oclc.nl/isni/0000000102215732" ,
    //             "http://id.loc.gov/authorities/names/n80148304"
    //         ] ,
    def name = request.JSON.'skos:prefLabel'
    def id = request.JSON.'@id'
    def new_org = new Org(name:name)

    def primary_identifier = Identifier.lookupOrCreateCanonicalIdentifier('global',id)
    new_org.ids.add(primary_identifier)

    request.JSON.'owl:sameAs'?.each {  said ->
   
      // Double check that this identifier is NOT already used
      def existing_usage = KBComponent.lookupByIO('global',said)
      if ( existing_usage == null ) {
        def identifier = Identifier.lookupOrCreateCanonicalIdentifier('global',said)
        new_org.ids.add(identifier)
      }
      else {
        log.error("Not adding identifer to a second item...");
      }
    }

    new_org.save();

    request.JSON.'skos:altLabel'?.each { al ->
      println("checking alt label ${al}");
      new_org.ensureVariantName(al);
    }

    new_org.save();

    if ( request.JSON.'foaf:homepage' != null ) {
      new_org.homepage = request.JSON.'foaf:homepage'
    }

    if ( new_org.save(flush:true, failOnError : true) ) {
      log.debug("Saved ok");
    }
    else {
      log.error("Problem saving new org. ${new_org.errors}");
    }
  }

  def enrichJsonLDOrg(org, jsonld) {
    log.debug("Enrich existing..");
    request.JSON.'skos:altLabel'?.each { al ->
      println("checking alt label ${al}");
      org.ensureVariantName(al);
    }

  }

  /**
   *  assertOrg()
   *  allow an authorized external component to send in a JSON structure following this template:
   *      [
   *         name:National Association of Corrosion Engineers, 
   *         description:National Association of Corrosion Engineers,
   *         parent:
   *         customIdentifers:[[identifierType:"idtype", identifierValue:"value"]], 
   *         combos:[[linkTo:[identifierType:"ncsu-internal", identifierValue:"ncsu:61929"], linkType:"HasParent"]], 
   *         flags:[[flagType:"Org Role", flagValue:"Content Provider"],
   *                [flagType:"Org Role", flagValue:"Publisher"], 
   *                [flagType:"Authorized", flagValue:"N"]]
   *      ]
   *
   */
  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def assertOrg() { 
    log.debug("assertOrg, request.json = ${request.JSON}");
    def result=[:]
    result.status = true;

    try {
      def located_or_new_org = resolveOrgUsingPrivateIdentifiers(request.JSON.customIdentifers);

      if ( located_or_new_org == null ) {
        log.debug("Create new org with identifiers ${request.JSON.customIdentifers} name will be \"${request.JSON.name}\" (${request.JSON.name.length()})");
   
        located_or_new_org = new Org(name:request.JSON.name)

        log.debug("Attempt to save - validate: ${located_or_new_org}");

        if ( located_or_new_org.save(flush:true, failOnError : true) ) {
          log.debug("Saved ok");
        }
        else {
          log.debug("Save failed ${located_or_new_org}");
          result.errors = []
          located_or_new_org.errors.each { e ->
            log.error("Problem saving new org record",e);
            result.errors.add("${e}".toString());
          }
          result.status = false;
          return
        }
        
        // Add parent.
        if (request.JSON.parent) {
          def parentDef = request.JSON.parent;
          log.debug("Adding parent using ${parentDef.identifierType}:${parentDef.identifierValue}");
          def located_component = KBComponent.lookupByIO(parentDef.identifierType,parentDef.identifierValue)
          if (located_component) {
            located_or_new_org.parent = located_component
          }
        }
  
        def identifier_combo_type = RefdataCategory.lookupOrCreate('Combo.Type','Org.Ids');
        // Identifiers
        log.debug("Identifier processing ${request.JSON.customIdentifers}");
        request.JSON.customIdentifers.each { ci ->
          def canonical_identifier = Identifier.lookupOrCreateCanonicalIdentifier(ci.identifierType,ci.identifierValue)
          log.debug("adding identifier(${ci.identifierType},${ci.identifierValue})(${canonical_identifier.id})");
      located_or_new_org.ids.add(canonical_identifier)
        }
    
        // roles
        log.debug("Role Processing: ${request.JSON.flags}");
        request.JSON.roles.each { r ->
          log.debug("Adding role ${r}");
          def role = RefdataCategory.lookupOrCreate("Org.Role", r)
          located_or_new_org.addToRoles(
      role
          )
        }

        // flags
        log.debug("Flag Processing: ${request.JSON.flags}");
        request.JSON.flags.each { f ->
          log.debug("Adding flag ${f.flagType},${f.flagValue}");
          def flag = RefdataCategory.lookupOrCreate(f.flagType,f.flagValue).save()
          located_or_new_org.addToTags(
            flag
          )
        }

        log.debug("Combo processing: ${request.JSON.combos}");

        // combos
        request.JSON.combos.each { c ->
          log.debug("lookup to item using ${c.linkTo.identifierType}:${c.linkTo.identifierValue}");
          def located_component = KBComponent.lookupByIO(c.linkTo.identifierType,c.linkTo.identifierValue)
      
      // Located a component.
          if ( ( located_component != null ) ) {
            def combo = new Combo(
              type:RefdataCategory.lookupOrCreate('Combo.Type',c.linkType),
              fromComponent:located_or_new_org,
              toComponent:located_component,
              startDate:new Date()).save(flush:true,failOnError:true);
          }
          else {
            log.error("Problem resolving from(${located_or_new_org}) or to(${located_component}) org for combo");
          }
        }
        
        log.debug("Attempt to save - validate: ${located_or_new_org}");
        
        if ( located_or_new_org.save(failOnError : true) ) {
          log.debug("Saved ok");
        }
        else {
          log.debug("Save failed ${located_or_new_org}");
          result.errors = []
          located_or_new_org.errors.each { e ->
            log.error("Problem saving new org record",e);
            result.errors.add("${e}".toString());
          }
          result.status = false;
          return
        }

        result.msg="Created new org: ${located_or_new_org.id} ${located_or_new_org.name}";
      }
      else {
        log.debug("Located existing record..");
        result.msg="Located existing record: ${located_or_new_org.id} ${located_or_new_org.name}";
      }
    }
    catch ( Exception e ) {
      log.error("Unexpected error importing org",e)
      result.msg="ERROR: ${e}";
      result.status=false
    }
    render result as JSON
  }


  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  private resolveOrgUsingPrivateIdentifiers(idlist) {
    def located_or_new_org = null;

    // See if we can locate the item using any of the custom identifiers
    idlist.each { ci ->
      log.debug("Attempt lookup of ${ci.identifierType}:${ci.identifierValue}");
      if ( located_or_new_org ) {
        // We've already located an org for this identifier, the new identifier should be new (And therefore added to this org) or
        // resolve to this org. If it resolves to some other org, then there is a conflict and we fail!
        def located_component = KBComponent.lookupByIO(ci.identifierType,ci.identifierValue)
        if ( located_component ) {
          log.debug("Matched something...");
          if ( !located_or_new_org ) {
            located_or_new_org = located_component
          }
          else {
            if ( located_component.id == located_or_new_org.id ) {
              log.debug("Matched an identifier");
            }
            else {
              log.error("**CONFLICT**");
            }
          }
        }
        else {
          // No match.. candidate identifier
          log.debug("No match for ${ci.identifierType}:${ci.identifierValue}");
        }
      }
      else {
        located_or_new_org = KBComponent.lookupByIO(ci.identifierType,ci.identifierValue)
      }
    }
    located_or_new_org
  }

  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def registerVariantName() {
    def result=[:]
    log.debug("registerVariantName ${params} ${request.JSON}");

    // See if we can locate the variant name as a first class component
    
    def variant_org = null;
    if ( request.JSON.variantidns != null && request.JSON.variantidvalue != null ) {
      variant_org = KBComponent.lookupByIO(request.JSON.variantidns,request.JSON.variantidvalue)
      log.debug("Existing variant org[${request.JSON.variantidns}:${request.JSON.variantidvalue}]: ${variant_org}");
    }

    def org_to_update = KBComponent.lookupByIO(request.JSON.idns,request.JSON.idvalue)
    log.debug("Org to update[${request.JSON.idns}:${request.JSON.idvalue}]: ${org_to_update}");

    // Double check that the variant name is not already the primary name, or in the list of variants, if not, add it.
    if ( ( org_to_update ) && ( request.JSON.name?.length() > 0 ) ) {
      boolean found = false
      org_to_update.variantNames.each { vn ->
        if ( vn.variantName == request.JSON.name ) {
          found = true
        }
      }

      if ( !found ) {
        def new_variant_name = new KBComponentVariantName(variantName:request.JSON.name, owner:org_to_update)
        new_variant_name.save();
      }
    }

    // Update any combos that point to the variant so that they now point to the authorized entry

    // Delete any remaining variant org combox
    // Delete the variant org

    render result as JSON
  }

  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def crossReferencePackage() {
    def result = [ 'result' : 'OK' ]
    User user = springSecurityService.currentUser
    if ( request.JSON.packageHeader.name ) {
      def valid = Package.validateDTO(request.JSON.packageHeader)
      if ( valid ) {
        def pkg = Package.upsertDTO(request.JSON.packageHeader)
        def platform_cache = [:]
        log.debug("\n\n\nPackage: ${pkg}");

        // Validate and upsert titles and platforms
        request.JSON.tipps.each { tipp ->

          valid &= TitleInstance.validateDTO(tipp.title);

          if ( !valid ) 
            log.warn("Not valid after title validation ${tipp.title}");

          def ti = TitleInstance.upsertDTO(titleLookupService, tipp.title);
          if ( ti && ( tipp.title.internalId == null ) ) {
            tipp.title.internalId = ti.id;
          }

          valid &= Platform.validateDTO(tipp.platform);
          if ( !valid ) 
            log.warn("Not valid after platform validation ${tipp.platform}");

          if ( valid ) {

            def pl = platform_cache[tipp.platform.name]
            if ( pl == null ) {
              pl = Platform.upsertDTO(tipp.platform);
              platform_cache[tipp.platform.name] = pl
            }

            if ( pl && ( tipp.platform.internalId == null ) ) {
              tipp.platform.internalId = pl.id;
            }
            else {
              log.warn("No platform arising from ${tipp.platform}");
            }
          }
          else {
            log.warn("Skip platform upsert ${tipp.platform} - Not valid after platform check");
          }

          if ( ( tipp.package == null ) && ( pkg.id ) ) {
            tipp.package = [ internalId: pkg.id ]
          }
          else {
            log.warn("No package");
            valid = false
          }
        }

        int tippctr=0;
        if ( valid ) {
          // If valid so far, validate tipps
          log.debug("Validating tipps [${tippctr++}]");
          request.JSON.tipps.each { tipp ->
            def validation_result = TitleInstancePackagePlatform.validateDTO(tipp)
            if ( !validation_result) {
              log.error("TIPP Validation failed on ${tipp}");
            }
          }
        }
        else {
          log.warn("Not validating tipps - failed pre validation");
        }

        tippctr=0
        if ( valid ) {
          def tipp_upsert_start_time = System.currentTimeMillis();
          // If valid, upsert tipps
          request.JSON.tipps.each { tipp ->
            log.debug("Upsert tipp [${tippctr++}] ${tipp}");
            TitleInstancePackagePlatform.upsertDTO(tipp)
          }
          log.debug("Elapsed tipp processing time: ${System.currentTimeMillis()-tipp_upsert_start_time} for ${tippctr} records");
        }
        else {
          log.warn("Not loading tipps - failed validation");
        }
      }
   
    }
    render result as JSON;
  }

  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def crossReferencePlatform() {
    def result = [ 'result' : 'OK' ]
    User user = springSecurityService.currentUser
    if ( ( request.JSON.platformUrl ) &&
         ( request.JSON.platformUrl.trim().length() > 0 ) &&
         ( request.JSON.platformName ) &&
         ( request.JSON.platformName.trim().length() > 0 ) ) {
      def p = Platform.findByPrimaryUrl(request.JSON.platformUrl);
      if ( p == null ) {
        p=new Platform(primaryUrl:request.JSON.platformUrl, name:request.JSON.platformName).save(flush:true, failOnError:true);
      }

      def changed = false;
      changed |= setRefdataIfPresent(request.JSON.authentication, p, 'authentication', 'Platform.AuthMethod')
      changed |= setRefdataIfPresent(request.JSON.software, p, 'software', 'Platform.Software')
      changed |= setRefdataIfPresent(request.JSON.service, p, 'service', 'Platform.Service')

      if ( changed ) {
        p.save(flush:true, failOnError:true);
      }

      result.platform_id = p.id;
    }
    render result as JSON
  }

  /**
   *  Cross reference an incoming title with the database. See an example of calling this controller method
   *  in GOKB_PROJECT slash scripts slash sync_gokb_titles.groovy
   *
   *  Cross reference record::
   *
   *  {
   *    'title':'the_title',
   *    'publisher':'the_publisher',
   *    'identifiers':[
   *      {type:'namespace',value:'value'},
   *      {type:'isbn', value:'1234-5678'}
   *    ]
   *    'type':'Serial'|'Monograph',
   *    'variantNames':[
   *      'Array Of Strings - one for each variant name'
   *    ],
   *    'imprint':'the_publisher',
   *    'publishedFrom':'yyyy-MM-dd' 'HH:mm:ss.SSS',
   *    'publishedTo':'yyyy-MM-dd' 'HH:mm:ss.SSS',
   *    'editStatus':'the_publisher',
   *    'status':'the_publisher',
   *    'historyEvents':[
   *    ]
   *  }
   */
  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def crossReferenceTitle() {
    def result = [ 'result' : 'OK' ]

    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSS");

    log.debug("crossReferenceTitle(${request.JSON.type},$request.JSON.title,${request.JSON.identifiers}},...)");

    try {
  
      User user = springSecurityService.currentUser
      def title = titleLookupService.find(request.JSON.title, 
                                          request.JSON.publisher, 
                                          request.JSON.identifiers, 
                                          user,
                                          null,
                                          request.JSON.type=='Serial' ? 'org.gokb.cred.JournalInstance' : 'org.gokb.cred.BookInstance' )  // project
  
      if ( title ) {
    
        if ( request.JSON.variantNames?.size() > 0 ) {
          request.JSON.variantNames.each { vn ->
            log.debug("Ensure variant name ${vn}");
            title.addVariantTitle(vn);
          }
        }
        
        def title_changed = false;
    
        if ( request.JSON.imprint ) {
          if ( title.imprint?.name == request.JSON.imprint ) {
            // Imprint already set
          }
          else {
            def imprint = Imprint.findByName(request.JSON.imprint) ?: new Imprint(name:request.JSON.imprint).save(flush:true, failOnError:true);
            title.imprint = imprint;
            title_changed = true
          }
        }
    
        title_changed |= setDateIfPresent(request.JSON.publishedFrom, title, 'publishedFrom', sdf)
        title_changed |= setDateIfPresent(request.JSON.publishedTo, title, 'publishedTo', sdf)
        title_changed |= setRefdataIfPresent(request.JSON.editStatus, title, 'editStatus', 'KBComponent.EditStatus')
        title_changed |= setRefdataIfPresent(request.JSON.status, title, 'status', 'KBComponent.Status')
    
        log.debug("Saving title changes");
        title.save(flush:true, failOnError:true);
    
        if ( request.JSON.historyEvents?.size() > 0 ) {
          request.JSON.historyEvents.each { jhe ->
            // 1971-01-01 00:00:00.0
            log.debug("Handling title history");
            try {
              def inlist = []
              def outlist = []
              def cont = true
              jhe.from.each { fhe ->
                def p = titleLookupService.find(fhe.title,
                                                null,
                                                fhe.identifiers,
                                                user,
                                                null,
                                                request.JSON.type=='Serial' ? 'org.gokb.cred.JournalInstance' : 'org.gokb.cred.BookInstance' );
                if ( p ) { inlist.add(p); } else { cont = false; }
              }
              jhe.to.each { fhe ->
                def p =  titleLookupService.find(fhe.title,
                                                             null,
                                                             fhe.identifiers,
                                                             user,
                                                             null,
                                                             request.JSON.type=='Serial' ? 'org.gokb.cred.JournalInstance' : 'org.gokb.cred.BookInstance' );
                if ( p ) { outlist.add(p); } else { cont = false; }
              }
    
              def first = true;
              // See if we can locate an existing ComponentHistoryEvent involving all the titles specified in this event
              def che_check_qry_sw  = new StringWriter();
              def qparams = []
              che_check_qry_sw.write('select che from ComponentHistoryEvent as che where ')
    
              inlist.each { fhe ->
                if ( first ) { first = false; } else { che_check_qry_sw.write(' AND ') }
                che_check_qry_sw.write(' exists ( select chep from ComponentHistoryEventParticipant as chep where chep.event = che and chep.participant = ?) ')
                qparams.add(fhe)
              }
              outlist.each { fhe ->
                if ( first ) { first = false; } else { che_check_qry_sw.write(' AND ') }
                che_check_qry_sw.write(' exists ( select chep from ComponentHistoryEventParticipant as chep where chep.event = che and chep.participant = ?) ')
                qparams.add(fhe)
              }
    
              def che_check_qry = che_check_qry_sw.toString()
              log.debug("Search for existing history event:: ${che_check_qry} ${qparams}");
              def qr = ComponentHistoryEvent.executeQuery(che_check_qry, qparams);
              if ( qr.size() > 0 )
                cont = false;
    
              if ( cont ) {
      
                def he = new ComponentHistoryEvent()
                if ( jhe.date ) {
                  he.eventDate = sdf.parse(jhe.date);
                }
                he.save(flush:true, failOnError:true);
        
                inlist.each {
                  def hep = new ComponentHistoryEventParticipant(event:he, participant:it, participantRole:'in');
                  hep.save(flush:true, failOnError:true);
                }
    
                outlist.each {
                  def hep = new ComponentHistoryEventParticipant(event:he, participant:it, participantRole:'out');
                  hep.save(flush:true, failOnError:true);
                }
              }
              else {
                // Matched an existing TH event, not creating a duplicate
              }
            }
            catch ( Exception e ) {
              log.error("Problem processing title history",e);
            }
          }
        }
  
        result.message = "Created/looked up title ${title.id}"
        result.cls = title.class.name
        result.titleId = title.id
      }
      else {
        result.message = "No title for ${request.JSON}";
        applicationEventService.publishApplicationEvent('CriticalSystemMessages', 'ERROR', [description:"Cross Reference Title failed :${request.JSON}"])
      }
    }
    catch ( Exception e ) {
      log.error("Exception attempting to cross reference title",e);
      result.result="ERROR"
      result.message=e.toString()
      result.baddata=request.JSON
      log.error("Source message causing error (ADD_TO_TEST_CASES): ${request.JSON}");
    }
    finally {
      log.debug("Result of cross ref title: ${result}");
    }

    render result as JSON
  }


  @Secured(['ROLE_API', 'IS_AUTHENTICATED_FULLY'])
  def loadTitleList() {
    def title_file = request.getFile("titleFile")?.inputStream
    char tab = '\t'
    char quote = '"'
    def r = new CSVReader( new InputStreamReader(title_file, java.nio.charset.Charset.forName('UTF-8') ), tab,quote )

    def col_positions = [ 'identifier.pissn':-1, 'identifier.eissn':-1, 'title':-1 ]

    String [] header = r.readNext()
    int ctr = 0
    header.each { 
      col_positions [ it.toLowerCase() ] = ctr++
    }

    if ( ( col_positions.'title' != -1 ) && 
         ( ( col_positions.'identifier.pissn' != -1 ) || 
           ( col_positions.'identifier.eissn' != -1 ) ) ) {

      // So long as we have at least one identifier...
      String [] nl = r.readNext()

      int rowctr = 0;

      while ( nl != null ) {
        try {
          KBComponent.withNewTransaction() {

            def candidate_identifiers = []

            if ( ( col_positions.'identifier.pissn' != -1 ) && 
                 ( nl[col_positions.'identifier.pissn']?.length() > 0 ) && 
                 ( nl[col_positions.'identifier.pissn'].toLowerCase() != 'null' ) ) { 
              candidate_identifiers.add([type:'issn', value:nl[col_positions.'identifier.pissn']]);
            }

            if ( ( col_positions.'identifier.eissn' != -1 ) && 
                 ( nl[col_positions.'identifier.eissn']?.length() > 0 ) && 
                 ( nl[col_positions.'identifier.eissn'].toLowerCase() != 'null' ) ) { 
              candidate_identifiers.add([type:'eissn', value:nl[col_positions.'identifier.eissn']]);
            }

            if ( candidate_identifiers.size() > 0 ) {
              log.debug("Looking up ${candidate_identifiers} - ${nl[col_positions.'title']}");
              def existing_component = titleLookupService.find (nl[col_positions.'title'], null, candidate_identifiers)
            }
            else {
              log.debug("No candidate identifiers: ${nl}");
            }
          }
        }
        catch ( Exception e ) {
          log.error("Unable to process..",e);
        }

        if ( rowctr++ > 100 ) {
          log.debug("CleanUpGorm..");
          rowctr = 0;
          cleanUpGorm()
        }
        nl = r.readNext()
        log.debug("Next row: ${nl}");
      }
    }
    log.debug("Done");
    redirect(action:'index');
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

  private def setDateIfPresent(value, obj, prop, sdf) {
    //request.JSON.title.publishedFrom, title, 'publishedFrom', sdf)
    boolean result = false;
    if ( ( value ) && ( value.toString().trim().length() > 0 ) ) {
      try {
        def pd = sdf.parse(value);
        if (pd) {
          obj[prop]=pd;
          result=true;
        }
      }
      catch(Exception e) {
      }
    }
    result;
  }

  private def setRefdataIfPresent(value, obj, prop, cat) {
    boolean result = false;

    if ( ( value ) && 
         ( value.toString().trim().length() > 0 ) &&
         ( ( obj[prop] == null ) || ( obj[prop].value != value.trim() ) ) ) {
      def v = RefdataCategory.lookupOrCreate(cat,value);
      obj[prop] = v
      result = true;
    }

    result
  }
}
