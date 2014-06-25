import grails.util.GrailsNameUtils;

import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass

import javax.servlet.http.HttpServletRequest

import org.gokb.DomainClassExtender
import org.gokb.IngestService
import org.gokb.cred.*
import org.gokb.refine.RefineProject
import org.gokb.validation.Validation
import org.gokb.validation.types.*

class BootStrap {

  def grailsApplication

  def init = { servletContext ->

    log.debug("Init");

    // Add a custom check to see if this is an ajax request.
    HttpServletRequest.metaClass.isAjax = {
      'XMLHttpRequest' == delegate.getHeader('X-Requested-With')
    }

    // Global System Roles
    def contributorRole = Role.findByAuthority('ROLE_CONTRIBUTOR') ?: new Role(authority: 'ROLE_CONTRIBUTOR', roleType:'global').save(failOnError: true)
    def userRole = Role.findByAuthority('ROLE_USER') ?: new Role(authority: 'ROLE_USER', roleType:'global').save(failOnError: true)
    def editorRole = Role.findByAuthority('ROLE_EDITOR') ?: new Role(authority: 'ROLE_EDITOR', roleType:'global').save(failOnError: true)
    def adminRole = Role.findByAuthority('ROLE_ADMIN') ?: new Role(authority: 'ROLE_ADMIN', roleType:'global').save(failOnError: true)
    def apiRole = Role.findByAuthority('ROLE_API') ?: new Role(authority: 'ROLE_API', roleType:'global').save(failOnError: true)

    log.debug("Create admin user...");
    def adminUser = User.findByUsername('admin')
    if ( ! adminUser ) {
      log.error("No admin user found, create");
      adminUser = new User(
          username: 'admin',
          password: 'admin',
          display: 'Admin',
          email: 'admin@localhost',
          enabled: true).save(failOnError: true)
    }

    [contributorRole,userRole,editorRole,adminRole,apiRole].each { role ->
      if (!adminUser.authorities.contains(role)) {
        UserRole.create adminUser, role
      }
    }

    String fs = grailsApplication.config.project_dir

    log.debug("Make sure project files directory exists, config says it's at ${fs}");
    File f = new File(fs)
    if ( ! f.exists() ) {
      log.debug("Creating upload directory path.")
      f.mkdirs()
    }

    refdataCats()

    registerDomainClasses()

    addValidationRules()
    
    failAnyIngestingProjects()

    // Add our custom metaclass methods for all KBComponents.
    alterDefaultMetaclass();
  }
  
  def failAnyIngestingProjects() {
    log.debug("Failing any projects stuck on Ingesting on server start.");
    RefineProject.findAllByProjectStatus (RefineProject.Status.INGESTING)?.each {
      
      it.setProjectStatus(RefineProject.Status.INGEST_FAILED)
      it.save(flush:true)
    }
  }

  def registerDomainClasses() {
    def std_domain_type = RefdataCategory.lookupOrCreate('DCType', 'Standard').save()
    grailsApplication.domainClasses.each { dc ->
      log.debug("Ensure ${dc.name} has entry in KBDomainInfo table");
      def dcinfo = KBDomainInfo.findByDcName(dc.clazz.name)
      if ( dcinfo == null ) {
        dcinfo = new KBDomainInfo(dcName:dc.clazz.name, displayName:dc.name, type:std_domain_type);
        dcinfo.save(flush:true);
      }
    }
  }

  def alterDefaultMetaclass = {

    // Inject helpers to Domain classes.
    grailsApplication.domainClasses.each {DefaultGrailsDomainClass domainClass ->

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

  def addValidationRules() {

    // Get the config for the validation.
    grailsApplication.config.validation.rules.each { String columnName, ruleDefs ->
      ruleDefs.each { ruleDef ->

        // Any extra args?
        def args = ruleDef.args
        if (args == null) {
          args = []
        }

        // Add the (columnName, severity) default args.
        args = [(columnName), (ruleDef.severity)] + args

        // Add the rule now we have the args build.
        Validation.addRule(ruleDef.type, (args as Object[]))
      }
    }
  }


  def destroy = {
  }


  def refdataCats() {
    RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT).save()
    RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED).save()
    RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_EXPECTED).save()
    RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_RETIRED).save()

    RefdataCategory.lookupOrCreate(KBComponent.RD_EDIT_STATUS, KBComponent.EDIT_STATUS_APPROVED).save()
    RefdataCategory.lookupOrCreate(KBComponent.RD_EDIT_STATUS, KBComponent.EDIT_STATUS_IN_PROGRESS).save()
    RefdataCategory.lookupOrCreate(KBComponent.RD_EDIT_STATUS, KBComponent.EDIT_STATUS_REJECTED).save()

    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.Format", "Digitised").save()
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.Format", "Electronic").save()
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.Format", "Print").save()

    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.DelayedOA", "No").save()
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.DelayedOA", "Unknown").save()
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.DelayedOA", "Yes").save()

    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.HybridOA", "No").save()
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.HybridOA", "Unknown").save()
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.HybridOA", "Yes").save()

    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.Primary", "Yes").save()
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.Primary", "No").save()

    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "Complimentary").save()
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "Limited Promotion").save()
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "Paid").save()
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "OA").save()
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "Opt Out Promotion").save()
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "Uncharged").save()
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "Unknown").save()

    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.CoverageDepth", "Fulltext").save()
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.CoverageDepth", "Selected Articles").save()
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.CoverageDepth", "Abstracts").save()

    RefdataCategory.lookupOrCreate("Package.Scope", "Aggregator").save()
    RefdataCategory.lookupOrCreate("Package.Scope", "Back File").save()
    RefdataCategory.lookupOrCreate("Package.Scope", "Front File").save()
    RefdataCategory.lookupOrCreate("Package.Scope", "Master File").save()
    RefdataCategory.lookupOrCreate("Package.ListStatus", "Checked").save()
    RefdataCategory.lookupOrCreate("Package.ListStatus", "In Progress").save()
    RefdataCategory.lookupOrCreate("Package.Breakable", "No").save()
    RefdataCategory.lookupOrCreate("Package.Breakable", "Yes").save()
    RefdataCategory.lookupOrCreate("Package.Breakable", "Unknown").save()
    RefdataCategory.lookupOrCreate("Package.Consistent", "No").save()
    RefdataCategory.lookupOrCreate("Package.Consistent", "Yes").save()
    RefdataCategory.lookupOrCreate("Package.Consistent", "Unknown").save()
    RefdataCategory.lookupOrCreate("Package.Fixed", "No").save()
    RefdataCategory.lookupOrCreate("Package.Fixed", "Yes").save()
    RefdataCategory.lookupOrCreate("Package.Fixed", "Unknown").save()

    RefdataCategory.lookupOrCreate("Package.PaymentType", "Complimentary").save()
    RefdataCategory.lookupOrCreate("Package.PaymentType", "Limited Promotion").save()
    RefdataCategory.lookupOrCreate("Package.PaymentType", "Paid").save()
    RefdataCategory.lookupOrCreate("Package.PaymentType", "Opt Out Promotion").save()
    RefdataCategory.lookupOrCreate("Package.PaymentType", "Uncharged").save()
    RefdataCategory.lookupOrCreate("Package.PaymentType", "Unknown").save()

    RefdataCategory.lookupOrCreate("Package.LinkType", "Parent").save()
    RefdataCategory.lookupOrCreate("Package.LinkType", "Previous").save()

    RefdataCategory.lookupOrCreate("Package.Global", "Consortium").save()
    RefdataCategory.lookupOrCreate("Package.Global", "Global").save()
    RefdataCategory.lookupOrCreate("Package.Global", "Other").save()

    RefdataCategory.lookupOrCreate("Platform.AuthMethod", "IP").save()
    RefdataCategory.lookupOrCreate("Platform.AuthMethod", "Shibboleth").save()
    RefdataCategory.lookupOrCreate("Platform.AuthMethod", "User Password").save()
    RefdataCategory.lookupOrCreate("Platform.AuthMethod", "Unknown").save()

    RefdataCategory.lookupOrCreate("Platform.Role", "Admin").save()
    RefdataCategory.lookupOrCreate("Platform.Role", "Host").save()

    RefdataCategory.lookupOrCreate("Platform.Software", "Atupon").save()

    RefdataCategory.lookupOrCreate("Platform.Service", "Highwire").save()

    RefdataCategory.lookupOrCreate("TitleInstance.Medium", "A & I Database").save()
    RefdataCategory.lookupOrCreate("TitleInstance.Medium", "Audio").save()
    RefdataCategory.lookupOrCreate("TitleInstance.Medium", "Book").save()
    RefdataCategory.lookupOrCreate("TitleInstance.Medium", "Dataset").save()
    RefdataCategory.lookupOrCreate("TitleInstance.Medium", "Film").save()
    RefdataCategory.lookupOrCreate("TitleInstance.Medium", "Image").save()
    RefdataCategory.lookupOrCreate("TitleInstance.Medium", "Journal").save()

    RefdataCategory.lookupOrCreate("TitleInstance.PureOA", "Yes").save()
    RefdataCategory.lookupOrCreate("TitleInstance.PureOA", "No").save()
    RefdataCategory.lookupOrCreate("TitleInstance.PureOA", "Unknown").save()

    RefdataCategory.lookupOrCreate("TitleInstance.ContinuingSeries", "Yes").save()
    RefdataCategory.lookupOrCreate("TitleInstance.ContinuingSeries", "No").save()
    RefdataCategory.lookupOrCreate("TitleInstance.ContinuingSeries", "Unknown").save()

    RefdataCategory.lookupOrCreate("TitleInstance.ReasonRetired", "Ceased").save()
    RefdataCategory.lookupOrCreate("TitleInstance.ReasonRetired", "Paused").save()

    RefdataCategory.lookupOrCreate("Tipp.StatusReason", "Xfer Out").save()
    RefdataCategory.lookupOrCreate("Tipp.StatusReason", "Xfer In").save()

    RefdataCategory.lookupOrCreate("Tipp.LinkType", "Comes With").save()
    RefdataCategory.lookupOrCreate("Tipp.LinkType", "Parent").save()
    RefdataCategory.lookupOrCreate("Tipp.LinkType", "Previous").save()

    RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Translated").save()
    RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Absorbed").save()
    RefdataCategory.lookupOrCreate("TitleInstance.Rel", "In Series").save()
    RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Merged").save()
    RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Renamed").save()
    RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Split").save()
    RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Supplement").save()
    RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Transferred").save()
    RefdataCategory.lookupOrCreate("TitleInstance.Rel", "Unknown").save()

    RefdataCategory.lookupOrCreate('Org.Mission','Academic').save()
    RefdataCategory.lookupOrCreate('Org.Mission','Commercial').save()
    RefdataCategory.lookupOrCreate('Org.Mission','Community Agency').save()
    RefdataCategory.lookupOrCreate('Org.Mission','Consortium').save()

    RefdataCategory.lookupOrCreate('Org.Role','Licensor').save()
    RefdataCategory.lookupOrCreate('Org.Role','Licensee').save()
    RefdataCategory.lookupOrCreate('Org.Role','Broker').save()
    RefdataCategory.lookupOrCreate('Org.Role','Vendor').save()
    RefdataCategory.lookupOrCreate('Org.Role','Content Provider').save()
    RefdataCategory.lookupOrCreate('Org.Role','Platform Provider').save()
    RefdataCategory.lookupOrCreate('Org.Role','Issuing Body').save()
    RefdataCategory.lookupOrCreate('Org.Role','Publisher').save()
    RefdataCategory.lookupOrCreate('Org.Role','Imprint').save()

    RefdataCategory.lookupOrCreate('Country','Afghanistan').save()
    RefdataCategory.lookupOrCreate('Country','Albania').save()
    RefdataCategory.lookupOrCreate('Country','Algeria').save()
    RefdataCategory.lookupOrCreate('Country','American Samoa').save()
    RefdataCategory.lookupOrCreate('Country','Andorra').save()
    RefdataCategory.lookupOrCreate('Country','Angola').save()
    RefdataCategory.lookupOrCreate('Country','Anguilla').save()
    RefdataCategory.lookupOrCreate('Country','Antigua and Barbuda').save()
    RefdataCategory.lookupOrCreate('Country','Argentina').save()
    RefdataCategory.lookupOrCreate('Country','Armenia').save()
    RefdataCategory.lookupOrCreate('Country','Aruba').save()
    RefdataCategory.lookupOrCreate('Country','Australia').save()
    RefdataCategory.lookupOrCreate('Country','Austria').save()
    RefdataCategory.lookupOrCreate('Country','Azerbaijan').save()
    RefdataCategory.lookupOrCreate('Country','Bahamas, The').save()
    RefdataCategory.lookupOrCreate('Country','Bahrain').save()
    RefdataCategory.lookupOrCreate('Country','Bangladesh').save()
    RefdataCategory.lookupOrCreate('Country','Barbados').save()
    RefdataCategory.lookupOrCreate('Country','Belarus').save()
    RefdataCategory.lookupOrCreate('Country','Belgium').save()
    RefdataCategory.lookupOrCreate('Country','Belize').save()
    RefdataCategory.lookupOrCreate('Country','Benin').save()
    RefdataCategory.lookupOrCreate('Country','Bermuda').save()
    RefdataCategory.lookupOrCreate('Country','Bhutan').save()
    RefdataCategory.lookupOrCreate('Country','Bolivia').save()
    RefdataCategory.lookupOrCreate('Country','Bosnia').save()
    RefdataCategory.lookupOrCreate('Country','Botswana').save()
    RefdataCategory.lookupOrCreate('Country','Bougainville').save()
    RefdataCategory.lookupOrCreate('Country','Brazil').save()
    RefdataCategory.lookupOrCreate('Country','British Indian Ocean').save()
    RefdataCategory.lookupOrCreate('Country','British Virgin Islands').save()
    RefdataCategory.lookupOrCreate('Country','Brunei').save()
    RefdataCategory.lookupOrCreate('Country','Bulgaria').save()
    RefdataCategory.lookupOrCreate('Country','Burkina Faso').save()
    RefdataCategory.lookupOrCreate('Country','Burundi').save()
    RefdataCategory.lookupOrCreate('Country','Cambodia').save()
    RefdataCategory.lookupOrCreate('Country','Cameroon').save()
    RefdataCategory.lookupOrCreate('Country','Canada').save()
    RefdataCategory.lookupOrCreate('Country','Cape Verde Islands').save()
    RefdataCategory.lookupOrCreate('Country','Cayman Islands').save()
    RefdataCategory.lookupOrCreate('Country','Central African Republic').save()
    RefdataCategory.lookupOrCreate('Country','Chad').save()
    RefdataCategory.lookupOrCreate('Country','Chile').save()
    RefdataCategory.lookupOrCreate('Country','China, Hong Kong').save()
    RefdataCategory.lookupOrCreate('Country','China, Macau').save()
    RefdataCategory.lookupOrCreate('Country','China, People’s Republic').save()
    RefdataCategory.lookupOrCreate('Country','China, Taiwan').save()
    RefdataCategory.lookupOrCreate('Country','Colombia').save()
    RefdataCategory.lookupOrCreate('Country','Comoros').save()
    RefdataCategory.lookupOrCreate('Country','Congo, Democratic Republic of').save()
    RefdataCategory.lookupOrCreate('Country','Congo, Republic of').save()
    RefdataCategory.lookupOrCreate('Country','Cook Islands').save()
    RefdataCategory.lookupOrCreate('Country','Costa Rica').save()
    RefdataCategory.lookupOrCreate('Country','Cote d’Ivoire').save()
    RefdataCategory.lookupOrCreate('Country','Croatia').save()
    RefdataCategory.lookupOrCreate('Country','Cuba').save()
    RefdataCategory.lookupOrCreate('Country','Cyprus').save()
    RefdataCategory.lookupOrCreate('Country','Czech Republic').save()
    RefdataCategory.lookupOrCreate('Country','Denmark').save()
    RefdataCategory.lookupOrCreate('Country','Djibouti').save()
    RefdataCategory.lookupOrCreate('Country','Dominica').save()
    RefdataCategory.lookupOrCreate('Country','Dominican Republic').save()
    RefdataCategory.lookupOrCreate('Country','Ecuador').save()
    RefdataCategory.lookupOrCreate('Country','Egypt').save()
    RefdataCategory.lookupOrCreate('Country','El Salvador').save()
    RefdataCategory.lookupOrCreate('Country','Equatorial Guinea').save()
    RefdataCategory.lookupOrCreate('Country','Eritrea').save()
    RefdataCategory.lookupOrCreate('Country','Estonia').save()
    RefdataCategory.lookupOrCreate('Country','Ethiopia').save()
    RefdataCategory.lookupOrCreate('Country','Faeroe Islands').save()
    RefdataCategory.lookupOrCreate('Country','Falkland Islands').save()
    RefdataCategory.lookupOrCreate('Country','Federated States of Micronesia').save()
    RefdataCategory.lookupOrCreate('Country','Fiji').save()
    RefdataCategory.lookupOrCreate('Country','Finland').save()
    RefdataCategory.lookupOrCreate('Country','France').save()
    RefdataCategory.lookupOrCreate('Country','French Guiana').save()
    RefdataCategory.lookupOrCreate('Country','French Polynesia').save()
    RefdataCategory.lookupOrCreate('Country','Gabon').save()
    RefdataCategory.lookupOrCreate('Country','Gambia, The').save()
    RefdataCategory.lookupOrCreate('Country','Georgia').save()
    RefdataCategory.lookupOrCreate('Country','Germany').save()
    RefdataCategory.lookupOrCreate('Country','Ghana').save()
    RefdataCategory.lookupOrCreate('Country','Gibraltar').save()
    RefdataCategory.lookupOrCreate('Country','Greece').save()
    RefdataCategory.lookupOrCreate('Country','Greenland').save()
    RefdataCategory.lookupOrCreate('Country','Grenada').save()
    RefdataCategory.lookupOrCreate('Country','Guadeloupe').save()
    RefdataCategory.lookupOrCreate('Country','Guam').save()
    RefdataCategory.lookupOrCreate('Country','Guatemala').save()
    RefdataCategory.lookupOrCreate('Country','Guinea').save()
    RefdataCategory.lookupOrCreate('Country','Guinea-Bissau').save()
    RefdataCategory.lookupOrCreate('Country','Guyana').save()
    RefdataCategory.lookupOrCreate('Country','Haiti').save()
    RefdataCategory.lookupOrCreate('Country','Holy See (Vatican City State)').save()
    RefdataCategory.lookupOrCreate('Country','Honduras').save()
    RefdataCategory.lookupOrCreate('Country','Hungary').save()
    RefdataCategory.lookupOrCreate('Country','Iceland').save()
    RefdataCategory.lookupOrCreate('Country','India').save()
    RefdataCategory.lookupOrCreate('Country','Indonesia').save()
    RefdataCategory.lookupOrCreate('Country','Iran').save()
    RefdataCategory.lookupOrCreate('Country','Iraq').save()
    RefdataCategory.lookupOrCreate('Country','Ireland').save()
    RefdataCategory.lookupOrCreate('Country','Israel').save()
    RefdataCategory.lookupOrCreate('Country','Italy').save()
    RefdataCategory.lookupOrCreate('Country','Jamaica').save()
    RefdataCategory.lookupOrCreate('Country','Japan').save()
    RefdataCategory.lookupOrCreate('Country','Jordan').save()
    RefdataCategory.lookupOrCreate('Country','Kazakhstan').save()
    RefdataCategory.lookupOrCreate('Country','Kenya').save()
    RefdataCategory.lookupOrCreate('Country','Kiribati').save()
    RefdataCategory.lookupOrCreate('Country','Korea, Democratic People’s Rep').save()
    RefdataCategory.lookupOrCreate('Country','Korea, Republic of').save()
    RefdataCategory.lookupOrCreate('Country','Kosovo').save()
    RefdataCategory.lookupOrCreate('Country','Kuwait').save()
    RefdataCategory.lookupOrCreate('Country','Kyrgyzstan').save()
    RefdataCategory.lookupOrCreate('Country','Laos').save()
    RefdataCategory.lookupOrCreate('Country','Latvia').save()
    RefdataCategory.lookupOrCreate('Country','Lebanon').save()
    RefdataCategory.lookupOrCreate('Country','Lesotho').save()
    RefdataCategory.lookupOrCreate('Country','Liberia').save()
    RefdataCategory.lookupOrCreate('Country','Libya').save()
    RefdataCategory.lookupOrCreate('Country','Liechtenstein').save()
    RefdataCategory.lookupOrCreate('Country','Lithuania').save()
    RefdataCategory.lookupOrCreate('Country','Luxembourg').save()
    RefdataCategory.lookupOrCreate('Country','Macedonia').save()
    RefdataCategory.lookupOrCreate('Country','Madagascar').save()
    RefdataCategory.lookupOrCreate('Country','Malawi').save()
    RefdataCategory.lookupOrCreate('Country','Malaysia').save()
    RefdataCategory.lookupOrCreate('Country','Maldives').save()
    RefdataCategory.lookupOrCreate('Country','Mali').save()
    RefdataCategory.lookupOrCreate('Country','Malta').save()
    RefdataCategory.lookupOrCreate('Country','Martinique').save()
    RefdataCategory.lookupOrCreate('Country','Mauritania').save()
    RefdataCategory.lookupOrCreate('Country','Mauritius').save()
    RefdataCategory.lookupOrCreate('Country','Mayotte').save()
    RefdataCategory.lookupOrCreate('Country','Mexico').save()
    RefdataCategory.lookupOrCreate('Country','Moldova').save()
    RefdataCategory.lookupOrCreate('Country','Monaco').save()
    RefdataCategory.lookupOrCreate('Country','Mongolia').save()
    RefdataCategory.lookupOrCreate('Country','Montenegro').save()
    RefdataCategory.lookupOrCreate('Country','Montserrat').save()
    RefdataCategory.lookupOrCreate('Country','Morocco Mozambique').save()
    RefdataCategory.lookupOrCreate('Country','Myanmar').save()
    RefdataCategory.lookupOrCreate('Country','Namibia').save()
    RefdataCategory.lookupOrCreate('Country','Nauru').save()
    RefdataCategory.lookupOrCreate('Country','Nepal').save()
    RefdataCategory.lookupOrCreate('Country','Netherlands').save()
    RefdataCategory.lookupOrCreate('Country','Netherlands Antilles').save()
    RefdataCategory.lookupOrCreate('Country','New Caledonia').save()
    RefdataCategory.lookupOrCreate('Country','New Zealand').save()
    RefdataCategory.lookupOrCreate('Country','Nicaragua').save()
    RefdataCategory.lookupOrCreate('Country','Niger').save()
    RefdataCategory.lookupOrCreate('Country','Nigeria').save()
    RefdataCategory.lookupOrCreate('Country','Norway').save()
    RefdataCategory.lookupOrCreate('Country','Oman').save()
    RefdataCategory.lookupOrCreate('Country','Pakistan').save()
    RefdataCategory.lookupOrCreate('Country','Palestine').save()
    RefdataCategory.lookupOrCreate('Country','Panama').save()
    RefdataCategory.lookupOrCreate('Country','Papua New Guinea').save()
    RefdataCategory.lookupOrCreate('Country','Paraguay').save()
    RefdataCategory.lookupOrCreate('Country','Peru').save()
    RefdataCategory.lookupOrCreate('Country','Philippines').save()
    RefdataCategory.lookupOrCreate('Country','Poland').save()
    RefdataCategory.lookupOrCreate('Country','Portugal').save()
    RefdataCategory.lookupOrCreate('Country','Puerto Rico').save()
    RefdataCategory.lookupOrCreate('Country','Qatar').save()
    RefdataCategory.lookupOrCreate('Country','Réunion').save()
    RefdataCategory.lookupOrCreate('Country','Romania').save()
    RefdataCategory.lookupOrCreate('Country','Russia').save()
    RefdataCategory.lookupOrCreate('Country','Rwanda').save()
    RefdataCategory.lookupOrCreate('Country','Saint Barthelemy').save()
    RefdataCategory.lookupOrCreate('Country','Saint Helena').save()
    RefdataCategory.lookupOrCreate('Country','Saint Kitts & Nevis').save()
    RefdataCategory.lookupOrCreate('Country','Saint Lucia').save()
    RefdataCategory.lookupOrCreate('Country','Saint Martin').save()
    RefdataCategory.lookupOrCreate('Country','Saint Pierre & Miquelon').save()
    RefdataCategory.lookupOrCreate('Country','Saint Vincent').save()
    RefdataCategory.lookupOrCreate('Country','Samoa').save()
    RefdataCategory.lookupOrCreate('Country','San Marino').save()
    RefdataCategory.lookupOrCreate('Country','Sao Tomé & Principe').save()
    RefdataCategory.lookupOrCreate('Country','Saudi Arabia').save()
    RefdataCategory.lookupOrCreate('Country','Senegal').save()
    RefdataCategory.lookupOrCreate('Country','Serbia').save()
    RefdataCategory.lookupOrCreate('Country','Seychelles').save()
    RefdataCategory.lookupOrCreate('Country','Sierra Leone').save()
    RefdataCategory.lookupOrCreate('Country','Singapore').save()
    RefdataCategory.lookupOrCreate('Country','Slovakia').save()
    RefdataCategory.lookupOrCreate('Country','Slovenia').save()
    RefdataCategory.lookupOrCreate('Country','Solomon Islands').save()
    RefdataCategory.lookupOrCreate('Country','Somalia').save()
    RefdataCategory.lookupOrCreate('Country','South Africa').save()
    RefdataCategory.lookupOrCreate('Country','Spain').save()
    RefdataCategory.lookupOrCreate('Country','Sri Lanka').save()
    RefdataCategory.lookupOrCreate('Country','Sudan').save()
    RefdataCategory.lookupOrCreate('Country','Suriname').save()
    RefdataCategory.lookupOrCreate('Country','Swaziland').save()
    RefdataCategory.lookupOrCreate('Country','Sweden').save()
    RefdataCategory.lookupOrCreate('Country','Switzerland').save()
    RefdataCategory.lookupOrCreate('Country','Syria').save()
    RefdataCategory.lookupOrCreate('Country','Tajikistan').save()
    RefdataCategory.lookupOrCreate('Country','Tanzania').save()
    RefdataCategory.lookupOrCreate('Country','Thailand').save()
    RefdataCategory.lookupOrCreate('Country','Timor Leste').save()
    RefdataCategory.lookupOrCreate('Country','Togo').save()
    RefdataCategory.lookupOrCreate('Country','Tokelau Islands').save()
    RefdataCategory.lookupOrCreate('Country','Tonga').save()
    RefdataCategory.lookupOrCreate('Country','Trinidad & Tobago').save()
    RefdataCategory.lookupOrCreate('Country','Tunisia').save()
    RefdataCategory.lookupOrCreate('Country','Turkey').save()
    RefdataCategory.lookupOrCreate('Country','Turkmenistan').save()
    RefdataCategory.lookupOrCreate('Country','Turks & Caicos Islands').save()
    RefdataCategory.lookupOrCreate('Country','Tuvalu').save()
    RefdataCategory.lookupOrCreate('Country','Uganda').save()
    RefdataCategory.lookupOrCreate('Country','Ukraine').save()
    RefdataCategory.lookupOrCreate('Country','United Arab Emirates').save()
    RefdataCategory.lookupOrCreate('Country','United Kingdom of GB & NI').save()
    RefdataCategory.lookupOrCreate('Country','United States of America').save()
    RefdataCategory.lookupOrCreate('Country','Uruguay').save()
    RefdataCategory.lookupOrCreate('Country','US Virgin Islands').save()
    RefdataCategory.lookupOrCreate('Country','Uzbekistan').save()
    RefdataCategory.lookupOrCreate('Country','Vanuatu').save()
    RefdataCategory.lookupOrCreate('Country','Venezuela').save()
    RefdataCategory.lookupOrCreate('Country','Vietnam').save()
    RefdataCategory.lookupOrCreate('Country','Wallis & Futuna Islands').save()
    RefdataCategory.lookupOrCreate('Country','Yemen').save()
    RefdataCategory.lookupOrCreate('Country','Zambia').save()
    RefdataCategory.lookupOrCreate('Country','Zimbabwe').save()
    //    RefdataCategory.lookupOrCreate("Combo.Type", "Content Provider").save()
    RefdataCategory.lookupOrCreate("Combo.Status", "Active").save()
    RefdataCategory.lookupOrCreate("Combo.Status", "Deleted").save()

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

    RefdataCategory.lookupOrCreate('License.Type','Template').save()
    RefdataCategory.lookupOrCreate('License.Type','Other').save()

    RefdataCategory.lookupOrCreate('KBComponentVariantName.VariantType','Misspelling').save()
    RefdataCategory.lookupOrCreate('KBComponentVariantName.VariantType','Authorized').save()
    RefdataCategory.lookupOrCreate('KBComponentVariantName.VariantType','Acronym').save()
    RefdataCategory.lookupOrCreate('KBComponentVariantName.VariantType','Minor Change').save()
    RefdataCategory.lookupOrCreate('KBComponentVariantName.VariantType','Nickname').save()



    RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale','EN-us').save()
    RefdataCategory.lookupOrCreate('KBComponentVariantName.Locale','EN-gb').save()

    RefdataCategory.lookupOrCreate('KBComponentVariantName.Status', KBComponent.STATUS_CURRENT).save()
    RefdataCategory.lookupOrCreate('KBComponentVariantName.Status', KBComponent.STATUS_DELETED).save()
    RefdataCategory.lookupOrCreate('KBComponentVariantName.Status', KBComponent.STATUS_EXPECTED).save()
    RefdataCategory.lookupOrCreate('KBComponentVariantName.Status', KBComponent.STATUS_RETIRED).save()

    // Review Request
    RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Open').save()
    RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Closed').save()
    RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Deleted').save()

    RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'RR Standard Desc 1').save()
    

    RefdataCategory.lookupOrCreate('Activity.Status', 'Active').save()
    RefdataCategory.lookupOrCreate('Activity.Status', 'Complete').save()
    RefdataCategory.lookupOrCreate('Activity.Status', 'Abandoned').save()

    RefdataCategory.lookupOrCreate('Activity.Type', 'TitleTransfer').save()

    RefdataCategory.lookupOrCreate('YN', 'Yes').save()
    RefdataCategory.lookupOrCreate('YN', 'No').save()

    RefdataCategory.lookupOrCreate('DCType', 'Admin', "100").save()
    RefdataCategory.lookupOrCreate('DCType', 'Standard', "200").save()
    RefdataCategory.lookupOrCreate('DCType', 'Support', "300").save()

    RefdataCategory.lookupOrCreate('License.Category', 'Content').save()
    RefdataCategory.lookupOrCreate('License.Category', 'Software').save()

    RefdataCategory.lookupOrCreate('Source.DataSupplyMethod', 'eMail').save()
    RefdataCategory.lookupOrCreate('Source.DataSupplyMethod', 'HTTP Url').save()
    RefdataCategory.lookupOrCreate('Source.DataSupplyMethod', 'FTP').save()
    RefdataCategory.lookupOrCreate('Source.DataSupplyMethod', 'Other').save()

    RefdataCategory.lookupOrCreate('Source.DataFormat', 'KBART').save()
    RefdataCategory.lookupOrCreate('Source.DataFormat', 'Proprietary').save()

  }
}
