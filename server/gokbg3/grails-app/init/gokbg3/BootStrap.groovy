package gokbg3;

import grails.util.GrailsNameUtils;

import grails.core.GrailsClass
import grails.core.GrailsApplication
import grails.converters.JSON


import java.lang.reflect.Method

import org.gokb.GOKbTextUtils

import javax.servlet.http.HttpServletRequest

import org.gokb.DomainClassExtender
import org.gokb.ESWrapperService
import org.gokb.ComponentStatisticService
import org.gokb.cred.*
import org.gokb.refine.RefineProject
import org.gokb.validation.types.*

import com.k_int.apis.A_Api;

import static org.springframework.security.acls.domain.BasePermission.ADMINISTRATION
import static org.springframework.security.acls.domain.BasePermission.DELETE
import static org.springframework.security.acls.domain.BasePermission.READ
import static org.springframework.security.acls.domain.BasePermission.WRITE
import static org.springframework.security.acls.domain.BasePermission.CREATE

import org.springframework.security.core.context.SecurityContextHolder as SCH
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils

import org.elasticsearch.client.Client
import org.elasticsearch.client.AdminClient
import org.elasticsearch.client.IndicesAdminClient
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequestBuilder
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse
import static org.elasticsearch.common.xcontent.XContentFactory.*
import org.elasticsearch.common.xcontent.XContentBuilder



class BootStrap {

  GrailsApplication grailsApplication
  def aclUtilService
  def gokbAclService
  def ComponentStatisticService
  def ESWrapperService
  //def titleLookupService

  def init = { servletContext ->

    log.debug("\n\nInit\n\n")

    log.info("\n\n\n **WARNING** \n\n\n - Automatic create of component identifiers index is no longer part of the domain model");
    log.info("Create manually with create index norm_id_value_idx on kbcomponent(kbc_normname(64),id_namespace_fk,class)");

    ContentItem.withTransaction() {
      def appname = ContentItem.findByKeyAndLocale('gokb.appname','default') ?: new ContentItem(key:'gokb.appname', locale:'default', content:'GOKb').save(flush:true, failOnError:true)
    }

    KBComponent.withTransaction() {
      cleanUpMissingDomains ()
    }

    // Add our custom metaclass methods for all KBComponents.
    alterDefaultMetaclass()

    // Add Custom APIs.
    addCustomApis()

    // Add a custom check to see if this is an ajax request.
    HttpServletRequest.metaClass.isAjax = {
      'XMLHttpRequest' == delegate.getHeader('X-Requested-With')
    }

    CuratoryGroup.withTransaction() {
      if ( grailsApplication.config.gokb.defaultCuratoryGroup != null ) {

        log.debug("Ensure curatory group: ${grailsApplication.config.gokb?.defaultCuratoryGroup}");

        def local_cg = CuratoryGroup.findByName(grailsApplication.config.gokb?.defaultCuratoryGroup) ?: 
                        new CuratoryGroup(name:grailsApplication.config.gokb?.defaultCuratoryGroup).save(flush:true, failOnError:true);
      }
    }

    // Global System Roles
    KBComponent.withTransaction() {
      def contributorRole = Role.findByAuthority('ROLE_CONTRIBUTOR') ?: new Role(authority: 'ROLE_CONTRIBUTOR', roleType:'global').save(failOnError: true)
      def userRole = Role.findByAuthority('ROLE_USER') ?: new Role(authority: 'ROLE_USER', roleType:'global').save(failOnError: true)
      def editorRole = Role.findByAuthority('ROLE_EDITOR') ?: new Role(authority: 'ROLE_EDITOR', roleType:'global').save(failOnError: true)
      def adminRole = Role.findByAuthority('ROLE_ADMIN') ?: new Role(authority: 'ROLE_ADMIN', roleType:'global').save(failOnError: true)
      def apiRole = Role.findByAuthority('ROLE_API') ?: new Role(authority: 'ROLE_API', roleType:'global').save(failOnError: true)
      def suRole = Role.findByAuthority('ROLE_SUPERUSER') ?: new Role(authority: 'ROLE_SUPERUSER', roleType:'global').save(failOnError: true)

      log.debug("Create admin user...");
      def adminUser = User.findByUsername('admin')
      if ( ! adminUser ) {
        log.error("No admin user found, create")
        adminUser = new User(
            username: 'admin',
            password: 'admin',
            display: 'Admin',
            email: 'admin@localhost',
            enabled: true).save(failOnError: true)
      }

      def ingestAgent = User.findByUsername('ingestAgent')
      if ( ! ingestAgent ) {
        log.error("No ingestAgent user found, create")
        ingestAgent = new User(
            username: 'ingestAgent',
            password: 'ingestAgent',
            display: 'Ingest Agent',
            email: '',
            enabled: false).save(failOnError: true)
      }
      def deletedUser = User.findByUsername('deleted')
      if ( ! deletedUser ) {
        log.error("No deleted user found, create")
        deletedUser = new User(
            username: 'deleted',
            password: 'deleted',
            display: 'Deleted User',
            email: '',
            enabled: false).save(failOnError: true)
      }


      // Make sure admin user has all the system roles.
      [contributorRole,userRole,editorRole,adminRole,apiRole,suRole].each { role ->
        log.debug("Ensure admin user has ${role} role");
        if (!adminUser.authorities.contains(role)) {
          UserRole.create adminUser, role
        }
      }
    }


    if (  grailsApplication.config.decisionSupport ) {
      log.debug("Configuring default decision support parameters");
      DSConfig();
    }

//    String fs = grailsApplication.config.project_dir
//    log.debug("Theme:: ${grailsApplication.config.gokb.theme}");
//
//    log.debug("Make sure project files directory exists, config says it's at ${fs}");
//    File f = new File(fs)
//    if ( ! f.exists() ) {
//      log.debug("Creating upload directory path.")
//      f.mkdirs()
//    }

    
      refdataCats()

      registerDomainClasses()

      migrateDiskFilesToDatabase()

    
    log.info("GoKB missing normalised component names");

      def ctr = 0;
      KBComponent.executeQuery("select kbc.id from KBComponent as kbc where kbc.normname is null and kbc.name is not null").each { kbc_id ->
        KBComponent kbc = KBComponent.get(kbc_id)
        log.debug("Repair component with no normalised name.. ${kbc.class.name} ${kbc.id} ${kbc.name}");
        kbc.generateNormname()
        kbc.save(flush:true, failOnError:true);
        ctr++
      }
      log.debug("${ctr} components updated");

    log.info("GoKB missing normalised identifiers");

      def id_ctr = 0;
      Identifier.executeQuery("select id.id from Identifier as id where id.normname is null and id.value is not null").each { id_id ->
          Identifier i = Identifier.get(id_id)
          i.generateNormname()
          i.save(flush:true, failOnError:true)
          id_ctr++
      }
      log.debug("${id_ctr} identifiers updated");

    log.info("GoKB defaultSortKeys()");
    defaultSortKeys ()

    log.info("GoKB sourceObjects()");
    sourceObjects()

    def isbn_ns = IdentifierNamespace.findByValue('isbn') ?: new IdentifierNamespace(value:'isbn', family:'isxn').save(flush:true, failOnError:true);
    def issn_ns = IdentifierNamespace.findByValue('issn') ?: new IdentifierNamespace(value:'issn', family:'isxn').save(flush:true, failOnError:true);
    def eissn_ns = IdentifierNamespace.findByValue('eissn') ?: new IdentifierNamespace(value:'eissn', family:'isxn').save(flush:true, failOnError:true);
    def issnl_ns = IdentifierNamespace.findByValue('issnl') ?: new IdentifierNamespace(value:'issnl', family:'isxn').save(flush:true, failOnError:true);
    def doi_ns = IdentifierNamespace.findByValue('doi') ?: new IdentifierNamespace(value:'doi').save(flush:true, failOnError:true);

    // log.info("Default batch loader config");
    // defaultBulkLoaderConfig();

    log.debug("Register users and override default admin password");
    registerUsers()
    
    log.debug("Ensuring ElasticSearch index")
    ensureESIndex()

    log.debug("Checking for missing component statistics")
    ComponentStatisticService.updateCompStats()

    log.info("GoKB Init complete");
  }

  def defaultBulkLoaderConfig() {
    // BulkLoaderConfig
    grailsApplication.config.kbart2.mappings.each { k,v ->
      log.debug("Process ${k}");
      def existing_cfg = BulkLoaderConfig.findByCode(k)
      if ( existing_cfg ) {
        log.debug("Got existing config");
      }
      else {
        def cfg = v as JSON
        existing_cfg = new BulkLoaderConfig(code:k, cfg:cfg?.toString()).save(flush:true, failOnError:true)
      }
    }
  }

  def migrateDiskFilesToDatabase() {
    log.info("Migrate Disk Files");
    def baseUploadDir = grailsApplication.config.baseUploadDir ?: '.'

    DataFile.findAll("from DataFile as df where df.fileData is null").each{ df ->
        log.debug("Migrating files for ${df.uploadName}::${df.guid}")
        def sub1 = df.guid.substring(0,2);
        def sub2 = df.guid.substring(2,4);
        def temp_file_name = "${baseUploadDir}/${sub1}/${sub2}/${df.guid}";
        try{
          def source_file = new File(temp_file_name);
          df.fileData = source_file.getBytes()
          if(df.save(flush:true)){
            //success
            source_file.delete()
          }else{
            log.debug("Errors while trying to save DataFile fileData:")
            log.debug(df.errors)
          }
        }catch(Exception e){
          log.error("Exception while migrating files to database. File ${temp_file_name}",e)
        }
    }
  }

  def cleanUpMissingDomains () {

    def domains = KBDomainInfo.createCriteria().list { ilike ('dcName', 'org.gokb%') }.each { d ->
      try {

        // Just try reading the class.
        Class c = Class.forName(d.dcName)
        // log.debug ("Looking for ${d.dcName} found class ${c}.")
        
      } catch (ClassNotFoundException e) {
        d.delete(flush:true)
        log.info ("Deleted domain object for ${d.dcName} as the Class could not be found." )
      }
    }
  }


  private void addCustomApis() {

    log.debug("Extend Domain classes.")
    (grailsApplication.getArtefacts("Domain")*.clazz).each {Class<?> c ->

      // SO: Changed this to use the APIs 'applicableFor' method that is used to check whether,
      // to add to the class or not. This defaults to "true". Have overriden on the GrailsDomainHelperApi utils
      // and moved the selective code there. This means that *ALL* domain classes will still receive the methods in the
      // SecurityApi.
      // II: has this caused projects under org.gokb.refine to no longer be visible? Not sure how to fix it.

      // log.debug("Considering ${c}")
      grailsApplication.config.apiClasses.each { String className ->
        // log.debug("Adding methods to ${c.name} from ${className}");
        // Add the api methods.
        A_Api.addMethods(c, Class.forName(className))
      }
    }
  }

  def registerDomainClasses() {

    def std_domain_type = RefdataCategory.lookupOrCreate('DCType', 'Standard').save(flush:true, failOnError:true)
    grailsApplication.domainClasses.each { dc ->
      // log.debug("Ensure ${dc.name} has entry in KBDomainInfo table");
      def dcinfo = KBDomainInfo.findByDcName(dc.clazz.name)
      if ( dcinfo == null ) {
        dcinfo = new KBDomainInfo(dcName:dc.clazz.name, displayName:dc.name, type:std_domain_type);
        dcinfo.save(flush:true);
      }
    }
  }

  def alterDefaultMetaclass = {

    // Inject helpers to Domain classes.
    grailsApplication.domainClasses.each {GrailsClass domainClass ->

      // Extend the domain class.
      DomainClassExtender.extend (domainClass)

    }
  }

  def assertPublisher(name) {
    def p = Org.findByName(name)
    if ( !p ) {
      def content_provider_role = RefdataCategory.lookupOrCreate('Org Role','Content Provider');
      p = new Org(name:name)
      p.tags.add(content_provider_role);
      p.save(flush:true);
    }

  }

  def defaultSortKeys () {
    def vals = RefdataValue.executeQuery("select o from RefdataValue o where o.sortKey is null or trim(o.sortKey) = ''")

    // Default the sort key to 0.
    vals.each {
      it.sortKey = "0"
      it.save(flush:true, failOnError:true)
    }

    // Now we should also do the same for the Domain objects.
    vals = KBDomainInfo.executeQuery("select o from KBDomainInfo o where o.dcSortOrder is null or trim(o.dcSortOrder) = ''")

    // Default the sort key to 0.
    vals.each {
      it.dcSortOrder = "0"
      it.save(flush:true, failOnError:true)
    }
  }


  def destroy = {
  }


  def refdataCats() {
    RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT, '0').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED, '3').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_EXPECTED, '1').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_RETIRED, '2').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate(KBComponent.RD_EDIT_STATUS, KBComponent.EDIT_STATUS_APPROVED).save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate(KBComponent.RD_EDIT_STATUS, KBComponent.EDIT_STATUS_IN_PROGRESS).save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate(KBComponent.RD_EDIT_STATUS, KBComponent.EDIT_STATUS_REJECTED).save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.Format", "Digitised").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.Format", "Electronic").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.Format", "Print").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.DelayedOA", "No").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.DelayedOA", "Unknown").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.DelayedOA", "Yes").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.HybridOA", "No").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.HybridOA", "Unknown").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.HybridOA", "Yes").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.Primary", "Yes").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.Primary", "No").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "Complimentary").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "Limited Promotion").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "Paid").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "OA").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "Opt Out Promotion").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "Uncharged").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "Unknown").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.CoverageDepth", "Fulltext").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.CoverageDepth", "Selected Articles").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.CoverageDepth", "Abstracts").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("Package.Scope", "Aggregator").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.Scope", "Back File").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.Scope", "Front File").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.Scope", "Master File").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.ListStatus", "Checked").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.ListStatus", "In Progress").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.Breakable", "No").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.Breakable", "Yes").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.Breakable", "Unknown").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.Consistent", "No").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.Consistent", "Yes").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.Consistent", "Unknown").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.Fixed", "No").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.Fixed", "Yes").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.Fixed", "Unknown").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("Package.PaymentType", "Complimentary").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.PaymentType", "Limited Promotion").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.PaymentType", "Paid").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.PaymentType", "Opt Out Promotion").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.PaymentType", "Uncharged").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.PaymentType", "Unknown").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("Package.LinkType", "Parent").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.LinkType", "Previous").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("Package.Global", "Consortium").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.Global", "Global").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Package.Global", "Other").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("Platform.AuthMethod", "IP").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Platform.AuthMethod", "Shibboleth").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Platform.AuthMethod", "User Password").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Platform.AuthMethod", "Unknown").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("Platform.Role", "Admin").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Platform.Role", "Host").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("Platform.Software", "Atupon").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("Platform.Service", "Highwire").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("TitleInstance.Medium", "A & I Database").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.Medium", "Audio").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.Medium", "Book").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.Medium", "Database").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.Medium", "Dataset").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.Medium", "Film").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.Medium", "Image").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.Medium", "Journal").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("TitleInstance.OAStatus", "Unknown").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.OAStatus", "Full OA").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.OAStatus", "Hibrid OA").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.OAStatus", "No OA").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("TitleInstance.PureOA", "Yes").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.PureOA", "No").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.PureOA", "Unknown").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("TitleInstance.ContinuingSeries", "Yes").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.ContinuingSeries", "No").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.ContinuingSeries", "Unknown").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("TitleInstance.ReasonRetired", "Ceased").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.ReasonRetired", "Paused").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("Tipp.StatusReason", "Xfer Out").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Tipp.StatusReason", "Xfer In").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("Tipp.LinkType", "Comes With").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Tipp.LinkType", "Parent").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Tipp.LinkType", "Previous").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Translated").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Absorbed").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.Rel", "In Series").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Merged").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Renamed").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Split").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Supplement").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Transferred").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Unknown").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('Org.Mission','Academic').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Org.Mission','Commercial').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Org.Mission','Community Agency').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Org.Mission','Consortium').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('Org.Role','Licensor').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Org.Role','Licensee').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Org.Role','Broker').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Org.Role','Vendor').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Org.Role','Content Provider').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Org.Role','Platform Provider').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Org.Role','Issuing Body').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Org.Role','Publisher').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Org.Role','Imprint').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('Country','Afghanistan').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Albania').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Algeria').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','American Samoa').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Andorra').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Angola').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Anguilla').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Antigua and Barbuda').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Argentina').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Armenia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Aruba').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Australia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Austria').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Azerbaijan').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Bahamas, The').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Bahrain').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Bangladesh').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Barbados').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Belarus').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Belgium').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Belize').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Benin').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Bermuda').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Bhutan').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Bolivia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Bosnia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Botswana').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Bougainville').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Brazil').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','British Indian Ocean').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','British Virgin Islands').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Brunei').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Bulgaria').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Burkina Faso').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Burundi').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Cambodia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Cameroon').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Canada').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Cape Verde Islands').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Cayman Islands').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Central African Republic').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Chad').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Chile').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','China, Hong Kong').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','China, Macau').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','China, People’s Republic').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','China, Taiwan').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Colombia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Comoros').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Congo, Democratic Republic of').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Congo, Republic of').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Cook Islands').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Costa Rica').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Cote d’Ivoire').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Croatia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Cuba').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Cyprus').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Czech Republic').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Denmark').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Djibouti').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Dominica').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Dominican Republic').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Ecuador').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Egypt').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','El Salvador').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Equatorial Guinea').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Eritrea').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Estonia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Ethiopia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Faeroe Islands').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Falkland Islands').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Federated States of Micronesia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Fiji').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Finland').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','France').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','French Guiana').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','French Polynesia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Gabon').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Gambia, The').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Georgia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Germany').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Ghana').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Gibraltar').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Greece').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Greenland').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Grenada').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Guadeloupe').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Guam').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Guatemala').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Guinea').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Guinea-Bissau').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Guyana').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Haiti').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Holy See (Vatican City State)').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Honduras').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Hungary').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Iceland').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','India').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Indonesia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Iran').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Iraq').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Ireland').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Israel').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Italy').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Jamaica').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Japan').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Jordan').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Kazakhstan').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Kenya').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Kiribati').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Korea, Democratic People’s Rep').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Korea, Republic of').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Kosovo').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Kuwait').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Kyrgyzstan').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Laos').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Latvia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Lebanon').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Lesotho').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Liberia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Libya').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Liechtenstein').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Lithuania').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Luxembourg').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Macedonia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Madagascar').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Malawi').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Malaysia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Maldives').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Mali').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Malta').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Martinique').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Mauritania').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Mauritius').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Mayotte').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Mexico').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Moldova').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Monaco').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Mongolia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Montenegro').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Montserrat').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Morocco Mozambique').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Myanmar').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Namibia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Nauru').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Nepal').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Netherlands').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Netherlands Antilles').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','New Caledonia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','New Zealand').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Nicaragua').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Niger').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Nigeria').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Norway').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Oman').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Pakistan').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Palestine').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Panama').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Papua New Guinea').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Paraguay').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Peru').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Philippines').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Poland').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Portugal').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Puerto Rico').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Qatar').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Réunion').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Romania').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Russia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Rwanda').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Saint Barthelemy').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Saint Helena').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Saint Kitts & Nevis').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Saint Lucia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Saint Martin').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Saint Pierre & Miquelon').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Saint Vincent').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Samoa').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','San Marino').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Sao Tomé & Principe').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Saudi Arabia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Senegal').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Serbia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Seychelles').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Sierra Leone').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Singapore').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Slovakia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Slovenia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Solomon Islands').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Somalia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','South Africa').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Spain').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Sri Lanka').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Sudan').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Suriname').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Swaziland').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Sweden').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Switzerland').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Syria').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Tajikistan').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Tanzania').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Thailand').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Timor Leste').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Togo').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Tokelau Islands').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Tonga').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Trinidad & Tobago').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Tunisia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Turkey').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Turkmenistan').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Turks & Caicos Islands').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Tuvalu').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Uganda').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Ukraine').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','United Arab Emirates').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','United Kingdom of GB & NI').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','United States of America').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Uruguay').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','US Virgin Islands').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Uzbekistan').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Vanuatu').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Venezuela').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Vietnam').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Wallis & Futuna Islands').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Yemen').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Zambia').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Country','Zimbabwe').save(flush:true, failOnError:true)
    //    RefdataCategory.lookupOrCreate("Combo.Type", "Content Provider").save()
    RefdataCategory.lookupOrCreate("Combo.Status", Combo.STATUS_ACTIVE).save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Combo.Status", Combo.STATUS_DELETED).save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Combo.Status", Combo.STATUS_SUPERSEDED).save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate("Combo.Status", Combo.STATUS_EXPIRED).save(flush:true, failOnError:true)

    //    RefdataCategory.lookupOrCreate('ComboType','Unknown').save()
    //    RefdataCategory.lookupOrCreate('ComboType','Previous').save()
    //    RefdataCategory.lookupOrCreate('ComboType','Model').save()
    //    RefdataCategory.lookupOrCreate('ComboType','Parent').save()
    //    RefdataCategory.lookupOrCreate('ComboType','Translated').save()
    //    RefdataCategory.lookupOrCreate('ComboType','Absorbed').save()
    //    RefdataCategory.lookupOrCreate('ComboType','Merged').save()
    //    RefdataCategory.lookupOrCreate('ComboType','Renamed').save()
    //    RefdataCategory.lookupOrCreate('ComboType','Split').save()
    //    RefdataCategory.lookupOrCreate('ComboType','Transferred').save()

    RefdataCategory.lookupOrCreate('License.Type','Template').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('License.Type','Other').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('KBComponentVariantName.VariantType','Misspelling').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('KBComponentVariantName.VariantType','Authorized').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('KBComponentVariantName.VariantType','Acronym').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('KBComponentVariantName.VariantType','Minor Change').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('KBComponentVariantName.VariantType','Nickname').save(flush:true, failOnError:true)



    RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale','en_US').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale','en_GB').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale','en').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale','de').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale','es').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale','fr').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale','it').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale','ru').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale','pt').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale','la').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('KBComponentVariantName.Status', KBComponent.STATUS_CURRENT).save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('KBComponentVariantName.Status', KBComponent.STATUS_DELETED).save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('KBComponentVariantName.Status', KBComponent.STATUS_EXPECTED).save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('KBComponentVariantName.Status', KBComponent.STATUS_RETIRED).save(flush:true, failOnError:true)

    // Review Request
    RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Open').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Closed').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Deleted').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'RR Standard Desc 1').save(flush:true, failOnError:true)


    RefdataCategory.lookupOrCreate('Activity.Status', 'Active').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Activity.Status', 'Complete').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Activity.Status', 'Abandoned').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('Activity.Type', 'TitleTransfer').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('YN', 'Yes').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('YN', 'No').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('DCType', 'Admin', "100").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('DCType', 'Standard', "200").save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('DCType', 'Support', "300").save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('License.Category', 'Content').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('License.Category', 'Software').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('Source.DataSupplyMethod', 'eMail').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Source.DataSupplyMethod', 'HTTP Url').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Source.DataSupplyMethod', 'FTP').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Source.DataSupplyMethod', 'Other').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('Source.DataFormat', 'KBART').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Source.DataFormat', 'Proprietary').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('RDFDataType', 'uri').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('RDFDataType', 'string').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('ingest.filetype','kbart2').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('ingest.filetype','ingram').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('ingest.filetype','ybp').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('ingest.filetype','cufts').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('Platform.Authentication','Unknown').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('Platform.Roles','Host').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('Combo.Type','KBComponent.Ids').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Combo.Type','TitleInstance.Tipps').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Combo.Type','Package.Tipps').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Combo.Type','Platform.HostedTipps').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('MembershipRole','Administrator').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('MembershipRole','Member').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('MembershipStatus','Approved').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('MembershipStatus','Pending').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('MembershipStatus','Rejected/Revoked').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('Price.type','list').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Price.type','perpetual').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Price.type','topup').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Price.type','on-off').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Price.type','subscription').save(flush:true, failOnError:true)

    RefdataCategory.lookupOrCreate('Currency','EUR').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Currency','GBP').save(flush:true, failOnError:true)
    RefdataCategory.lookupOrCreate('Currency','USD').save(flush:true, failOnError:true)

    log.debug("Deleting any null refdata values");
    RefdataValue.executeUpdate('delete from RefdataValue where value is null');
  }

  def sourceObjects() {
    log.debug("Lookup or create source objects");
    def ybp_source = Source.findByName('YBP') ?: new Source(name:'YBP').save(flush:true, failOnError:true);
    def cup_source = Source.findByName('CUP') ?: new Source(name:'CUP').save(flush:true, failOnError:true);
    def wiley_source = Source.findByName('WILEY') ?: new Source(name:'WILEY').save(flush:true, failOnError:true);
    def cufts_source = Source.findByName('CUFTS') ?: new Source(name:'CUFTS').save(flush:true, failOnError:true);
    def askews_source = Source.findByName('ASKEWS') ?: new Source(name:'ASKEWS').save(flush:true, failOnError:true);
    def ebsco_source = Source.findByName('EBSCO') ?: new Source(name:'EBSCO').save(flush:true, failOnError:true);
  }

  def DSConfig() {

    [ 
      'accessdl':'Access - Download', 
      'accessol':'Access - Read Online',
      'accbildl':'Accessibility - Download', 
      'accbilol':'Accessibility - Read Online', 
      'device':'Device Requirements for Download',
      'drm':'DRM', 
      'format':'Format', 
      'lic':'Licensing',
      'other':'Other',
      'ref':'Referencing',
    ].each { k,v ->
      def dscat = DSCategory.findByCode(k) ?: new DSCategory(code:k, description: v).save(flush:true, failOnError: true)
    }

    [ 
      [ 'format',     'Downloadable PDF', '', '' ],
      [ 'format',     'Embedded PDF', '', '' ],
      [ 'format',     'ePub', '', '' ],
      [ 'format',     'OeB', '', '' ],
      [ 'accessol',   'Book Navigation', '', '' ],
      [ 'accessol',   'Table of contents navigation', '', '' ],
      [ 'accessol',   'Pagination', '', '' ],
      [ 'accessol',   'Page Search', '', '' ],
      [ 'accessol',   'Search Within Book', '', '' ],
      [ 'accessdl',   'Download Extent', '', '' ],
      [ 'accessdl',   'Download Time', '', '' ],
      [ 'accessdl',   'Download Reading View Navigation', '', '' ],
      [ 'accessdl',   'Table of Contents Navigation', '', '' ],
      [ 'accessdl',   'Pagination', '', '' ],
      [ 'accessdl',   'Page Search', '', '' ],
      [ 'accessdl',   'Search Within Book', '', '' ],
      [ 'accessdl',   'Read Aloud or Listen Option', '', '' ],
      [ 'device',     'General', '', '' ],
      [ 'device',     'Android', '', '' ],
      [ 'device',     'iOS', '', '' ],
      [ 'device',     'Kindle Fire', '', '' ],
      [ 'device',     'PC', '', '' ],
      [ 'drm',        'Copying', '', '' ],
      [ 'drm',        'Printing', '', '' ],
      [ 'accbilol',   'Dictionary', '', '' ],
      [ 'accbilol',   'Text Resize', '', '' ],
      [ 'accbilol',   'Change Reading Colour', '', '' ],
      [ 'accbilol',   'Read aloud or Listen Option', '', '' ],
      [ 'accbilol',   'Integrated Help', '', '' ],
      [ 'accbildl',   'Copying', '', '' ],
      [ 'accbildl',   'Printing', '', '' ],
      [ 'accbildl',   'Add Notes', '', '' ],
      [ 'accbildl',   'Dictionary', '', '' ],
      [ 'accbildl',   'Text Resize', '', '' ],
      [ 'accbildl',   'Change Reading Colour', '', '' ],
      [ 'accbildl',   'Integrated Help', '', '' ],
      [ 'accbildl',   'Other Accessibility features or Support', '', '' ],
      [ 'ref',        'Export to bibliographic software', '', '' ],
      [ 'ref',        'Sharing / Social Media', '', '' ],
      [ 'other',      'Changes / Redevelopment in the near future', '', '' ],
      [ 'lic',        'Number of users', '', '' ],
      [ 'lic',        'Credit Payment Model', '', '' ],
      [ 'lic',        'Publishers Included', '', '' ] 
    ].each { crit ->
      def cat = DSCategory.findByCode(crit[0]);
      if ( cat ) {
        def c = DSCriterion.findByOwnerAndTitle(cat, crit[1]) ?: new DSCriterion(
                                                                                 owner:cat,
                                                                                 title:crit[1],
                                                                                 description:crit[2],
                                                                                 explanation:crit[3]).save(flush:true, failOnError: true)
      }
      else {
        log.error("Unable to locate category: ${crit[0]}");
      }
    }

    //log.debug(titleLookupService.getTitleFieldForIdentifier([[ns:'isbn',value:'9780195090017']],'publishedFrom'));
    //log.debug(titleLookupService.getTitleFieldForIdentifier([[ns:'isbn',value:'9780195090017']],'publishedTo'));
  }

  def registerUsers() {

    grailsApplication.config.sysusers.each { su ->
      log.debug("test ${su.name} ${su.pass} ${su.display} ${su.roles}");
      def user = User.findByUsername(su.name)
      if ( user ) {
        if ( user.password != su.pass ) {
          log.debug("Hard change of user password from config ${user.password} -> ${su.pass}");
          user.password = su.pass;
          user.save(failOnError: true)
        }
        else {
          log.debug("${su.name} present and correct");
        }
      }
      else {
        log.debug("Create user...");
        user = new User(
                        username: su.name,
                        password: su.pass,
                        display: su.display,
                        email: su.email,
                        enabled: true).save(failOnError: true)
      }

      log.debug("Add roles for ${su.name}");
      su.roles.each { r ->
        def role = Role.findByAuthority(r)
        if ( ! ( user.authorities.contains(role) ) ) {
          log.debug("  -> adding role ${role}");
          UserRole.create user, role
        }
        else {
          log.debug("  -> ${role} already present");
        }
      }
    }
  }
  
  def ensureESIndex() {
    def indexName = grailsApplication.config.gokb.es.index ?: (grailsApplication.config.gokb_es_index ?: "gokbg3")
    log.debug("ensureESIndex for ${indexName}");
    def esclient = ESWrapperService.getClient()
    IndicesAdminClient adminClient = esclient.admin().indices();
    
    if (!adminClient.prepareExists(indexName).execute().actionGet().isExists()) {
      log.debug("ES index ${indexName} did not exist, creating..")
      
      CreateIndexRequestBuilder createIndexRequestBuilder = adminClient.prepareCreate(indexName);
      
      log.debug("Adding index setttings..")
      createIndexRequestBuilder.setSettings(indexSettings());
      log.debug("Adding index mappings..")
      createIndexRequestBuilder.addMapping("component", indexMapping());
      
      CreateIndexResponse indexResponse = createIndexRequestBuilder.execute().actionGet();
      
      if ( indexResponse.isAcknowledged() ) {
        log.debug("Index ${indexName} successfully created!")
      }else{
        log.debug("Index creation failed: ${indexResponse}")
      }
    }
    else{
      log.debug("ES index ${indexName} already exists..")
      // Validate settings & mappings
    }
  }
  
  def indexSettings() {
    // get from File?
    
    XContentBuilder settings = jsonBuilder()
      .startObject()
        .field("number_of_shards", 1) 
        .startObject("analysis")
          .startObject("filter")
            .startObject("autocomplete_filter") 
              .field("type","edge_ngram")
              .field("min_gram", 1)
              .field("max_gram", 20)
            .endObject()
          .endObject()
          .startObject("analyzer")
            .startObject("autocomplete")
              .field("type","custom")
              .field("tokenizer","standard")
              .startArray("filter")
                .value("lowercase")
                .value("autocomplete_filter") 
              .endArray()
            .endObject()
          .endObject()
        .endObject()
      .endObject()
      
    return settings
  }
  
  def indexMapping() {
    // get from File?
    
    XContentBuilder mapping = jsonBuilder()
      .startObject()
        .startObject("component")
          .startArray("dynamic_templates")
            .startObject()
              .startObject("provider")
                .field("match", "provider")
                .field("match_mapping_type", "string")
                .startObject("mapping")
                  .field("type", "keyword")
                .endObject()
              .endObject()
            .endObject()
            .startObject()
              .startObject("cpname")
                .field("match", "cpname")
                .field("match_mapping_type", "string")
                .startObject("mapping")
                  .field("type", "keyword")
                .endObject()
              .endObject()
            .endObject()
            .startObject()
              .startObject("publisher")
                .field("match", "publisher")
                .field("match_mapping_type", "string")
                .startObject("mapping")
                  .field("type", "keyword")
                .endObject()
              .endObject()
            .endObject()
            .startObject()
              .startObject("listStatus")
                .field("match", "listStatus")
                .field("match_mapping_type", "string")
                .startObject("mapping")
                  .field("type", "keyword")
                .endObject()
              .endObject()
            .endObject()
            .startObject()
              .startObject("package")
                .field("match", "tippPackage")
                .field("match_mapping_type", "string")
                .startObject("mapping")
                  .field("type", "keyword")
                .endObject()
              .endObject()
            .endObject()
            .startObject()
              .startObject("title")
                .field("match", "tippTitle")
                .field("match_mapping_type", "string")
                .startObject("mapping")
                  .field("type", "keyword")
                .endObject()
              .endObject()
            .endObject()
            .startObject()
              .startObject("hostPlatform")
                .field("match", "hostPlatform")
                .field("match_mapping_type", "string")
                .startObject("mapping")
                  .field("type", "keyword")
                .endObject()
              .endObject()
            .endObject()
            .startObject()
              .startObject("roles")
                .field("match", "roles")
                .field("match_mapping_type", "string")
                .startObject("mapping")
                  .field("type", "keyword")
                .endObject()
              .endObject()
            .endObject()
            .startObject()
              .startObject("curGroups")
                .field("match", "curatoryGroups")
                .field("match_mapping_type", "string")
                .startObject("mapping")
                  .field("type", "keyword")
                .endObject()
              .endObject()
            .endObject()
            .startObject()
              .startObject("nominalPlatform")
                .field("match", "nominalPlatform")
                .field("match_mapping_type", "string")
                .startObject("mapping")
                  .field("type", "keyword")
                .endObject()
              .endObject()
            .endObject()
            .startObject()
              .startObject("otherUuids")
                .field("match", "*Uuid")
                .field("match_mapping_type", "string")
                .startObject("mapping")
                  .field("type", "keyword")
                .endObject()
              .endObject()
            .endObject()
          .endArray()
          .startObject("properties")
            .startObject("name")
              .field("type", "text")
              .field("copy_to", "suggest")
              .startObject("fields")
                .startObject("name")
                  .field("type", "text")
                .endObject()
                .startObject("altname")
                  .field("type", "text")
                .endObject()
              .endObject()
            .endObject()
            .startObject("identifiers")
              .field("type", "nested")
              .startObject("properties")
                .startObject("namespace")
                  .field("type","keyword")
                .endObject()
                .startObject("value")
                  .field("type","keyword")
                .endObject()
              .endObject()
            .endObject()
            .startObject("sortname") 
              .field("type","keyword")  // RS5 replaces string-not_analyzed with keyword, and string-analyzed with text
            .endObject()
            .startObject("componentType") 
              .field("type","keyword") 
            .endObject()
            .startObject("uuid")
              .field("type","keyword")
            .endObject()
            .startObject("status")
              .field("type","keyword")
            .endObject()
            .startObject("suggest")
              .field("type","text")
              .field("analyzer","autocomplete")
              .field("search_analyzer","standard")
            .endObject()
        .endObject()
      .endObject()
    .endObject()
      
    return mapping
  }
}
