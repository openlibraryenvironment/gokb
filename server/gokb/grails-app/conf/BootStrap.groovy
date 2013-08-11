import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass
import org.gokb.DomainClassExtender
import org.gokb.IngestService
import org.gokb.cred.*
import org.gokb.validation.Validation
import org.gokb.validation.types.*

class BootStrap {

  def grailsApplication

  def init = { servletContext ->
    // Global System Roles
    def userRole = Role.findByAuthority('ROLE_USER') ?: new Role(authority: 'ROLE_USER', roleType:'global').save(failOnError: true)
    def editorRole = Role.findByAuthority('ROLE_EDITOR') ?: new Role(authority: 'ROLE_EDITOR', roleType:'global').save(failOnError: true)
    def adminRole = Role.findByAuthority('ROLE_ADMIN') ?: new Role(authority: 'ROLE_ADMIN', roleType:'global').save(failOnError: true)

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

    if (!adminUser.authorities.contains(adminRole)) {
      UserRole.create adminUser, adminRole
    }

    if (!adminUser.authorities.contains(userRole)) {
      UserRole.create adminUser, userRole
    }

	String fs = grailsApplication.config.project_dir
	
    log.debug("Make sure project files directory exists, config says it's at ${fs}");
    File f = new File(fs)
    if ( ! f.exists() ) {
      log.debug("Creating upload directory path.")
      f.mkdirs()
    }


    refdataCats()
	addValidationRules()

    // assertPublisher('Wiley');
    // assertPublisher('Random House');
    // assertPublisher('Cambridge University Press');
    // assertPublisher('Sage');
    
    // Add our custom metaclass methods for all KBComponents.
    alterDefaultMetaclass();
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
	RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "Opt Out Promotion").save()
	RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "Uncharged").save()
	RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.PaymentType", "Unkown").save()
    
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
    RefdataCategory.lookupOrCreate("Package.PaymentType", "Unkown").save()
	RefdataCategory.lookupOrCreate("Package.Global", "Consortium").save()
	RefdataCategory.lookupOrCreate("Package.Global", "Global").save()
	RefdataCategory.lookupOrCreate("Package.Global", "Other").save()
	
	RefdataCategory.lookupOrCreate("Platform.AuthMethod", "IP").save()
	RefdataCategory.lookupOrCreate("Platform.AuthMethod", "Shibboleth").save()
	RefdataCategory.lookupOrCreate("Platform.AuthMethod", "User Password").save()
	RefdataCategory.lookupOrCreate("Platform.AuthMethod", "Unknown").save()
	
	RefdataCategory.lookupOrCreate("Platform.Role", "Admin").save()
	RefdataCategory.lookupOrCreate("Platform.Role", "Host").save()

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
	
	RefdataCategory.lookupOrCreate("TitleInstance.ReasonRetired", "Ceased").save()
	RefdataCategory.lookupOrCreate("TitleInstance.ReasonRetired", "Transferred").save()
	
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

//    RefdataCategory.lookupOrCreate("Combo.Type", "Content Provider").save()
//    RefdataCategory.lookupOrCreate("Combo.Status", "Active").save()

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

    RefdataCategory.lookupOrCreate('KBComponentVariantName.variantType','Alternate Title').save()
    RefdataCategory.lookupOrCreate('KBComponentVariantName.variantType','Previous Title').save()
    RefdataCategory.lookupOrCreate('KBComponentVariantName.variantType','Misspelling').save()

    RefdataCategory.lookupOrCreate('KBComponentVariantName.locale','EN-us').save()
    RefdataCategory.lookupOrCreate('KBComponentVariantName.locale','EN-gb').save()
	
	RefdataCategory.lookupOrCreate('KBComponentVariantName.status', KBComponent.STATUS_CURRENT).save()
	RefdataCategory.lookupOrCreate('KBComponentVariantName.status', KBComponent.STATUS_DELETED).save()
	RefdataCategory.lookupOrCreate('KBComponentVariantName.status', KBComponent.STATUS_EXPECTED).save()
	RefdataCategory.lookupOrCreate('KBComponentVariantName.status', KBComponent.STATUS_RETIRED).save()
	
	// Review Request
	RefdataCategory.lookupOrCreate('ReviewRequest.status', 'Needs Review').save()
	RefdataCategory.lookupOrCreate('ReviewRequest.status', 'Reviewed').save()
  }
}
