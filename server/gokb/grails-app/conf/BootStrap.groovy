import org.gokb.cred.*;
import org.gokb.DomainClassExtender;
import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass
import org.codehaus.groovy.grails.io.support.GrailsResourceUtils

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


//    refdataCats();

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


  def destroy = {
  }


  def refdataCats() {
    RefdataCategory.lookupOrCreate("KBComponent.Status", "Current")
    RefdataCategory.lookupOrCreate("KBComponent.Status", "Deleted")
    RefdataCategory.lookupOrCreate("KBComponent.Status", "Expected")
    RefdataCategory.lookupOrCreate("KBComponent.Status", "Retired")

    RefdataCategory.lookupOrCreate("KBComponent.EditStatus", "Approved")
    RefdataCategory.lookupOrCreate("KBComponent.EditStatus", "In-Process")
    RefdataCategory.lookupOrCreate("KBComponent.EditStatus", "Rejected")

    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.Format", "Digitised")
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.Format", "Electronic")
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.Format", "Print")

    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.DelayedOA", "No")
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.DelayedOA", "Unknown")
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.DelayedOA", "Yes")

    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.HybridOA", "No")
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.HybridOA", "Unknown")
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.HybridOA", "Yes")

    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.IsPrimary", "Yes")
    RefdataCategory.lookupOrCreate("TitleInstancePackagePlatform.IsPrimary", "No")
	
	RefdataCategory.lookupOrCreate("Package.Scope", "Aggregator")
	RefdataCategory.lookupOrCreate("Package.Scope", "Back File")
	RefdataCategory.lookupOrCreate("Package.Scope", "Front File")
	RefdataCategory.lookupOrCreate("Package.Scope", "Master File")
	RefdataCategory.lookupOrCreate("Package.ListStatus", "Checked")
	RefdataCategory.lookupOrCreate("Package.ListStatus", "In Progress")
	RefdataCategory.lookupOrCreate("Package.Breakable", "No")
	RefdataCategory.lookupOrCreate("Package.Breakable", "Yes")
	RefdataCategory.lookupOrCreate("Package.Breakable", "Unknown")
	RefdataCategory.lookupOrCreate("Package.Consistent", "No")
	RefdataCategory.lookupOrCreate("Package.Consistent", "Yes")
	RefdataCategory.lookupOrCreate("Package.Consistent", "Unknown")
	RefdataCategory.lookupOrCreate("Package.Fixed", "No")
	RefdataCategory.lookupOrCreate("Package.Fixed", "Yes")
	RefdataCategory.lookupOrCreate("Package.Fixed", "Unknown")
    RefdataCategory.lookupOrCreate("Package.PaymentType", "Complimentary")
    RefdataCategory.lookupOrCreate("Package.PaymentType", "Limited Promotion")
    RefdataCategory.lookupOrCreate("Package.PaymentType", "Paid")
    RefdataCategory.lookupOrCreate("Package.PaymentType", "Opt Out Promotion")
    RefdataCategory.lookupOrCreate("Package.PaymentType", "Uncharged")
    RefdataCategory.lookupOrCreate("Package.PaymentType", "Unkown")
	RefdataCategory.lookupOrCreate("Package.Global", "Consortium")
	RefdataCategory.lookupOrCreate("Package.Global", "Global")
	RefdataCategory.lookupOrCreate("Package.Global", "Other")
	
	RefdataCategory.lookupOrCreate("Platform.AuthMethod", "IP")
	RefdataCategory.lookupOrCreate("Platform.AuthMethod", "Shibboleth")
	RefdataCategory.lookupOrCreate("Platform.AuthMethod", "User Password")
	RefdataCategory.lookupOrCreate("Platform.AuthMethod", "Unknown")
	
	RefdataCategory.lookupOrCreate("Platform.Role", "Admin")
	RefdataCategory.lookupOrCreate("Platform.Role", "Host")

	RefdataCategory.lookupOrCreate("TitleInstance.Material", "A & I Database")
	RefdataCategory.lookupOrCreate("TitleInstance.Material", "Audio")
	RefdataCategory.lookupOrCreate("TitleInstance.Material", "Book")
	RefdataCategory.lookupOrCreate("TitleInstance.Material", "Dataset")
	RefdataCategory.lookupOrCreate("TitleInstance.Material", "Film")
	RefdataCategory.lookupOrCreate("TitleInstance.Material", "Image")
	RefdataCategory.lookupOrCreate("TitleInstance.Material", "Journal")
	
	RefdataCategory.lookupOrCreate("TitleInstance.PureOA", "Yes")
	RefdataCategory.lookupOrCreate("TitleInstance.PureOA", "No")
	RefdataCategory.lookupOrCreate("TitleInstance.PureOA", "Unknown")
	
	RefdataCategory.lookupOrCreate("TitleInstance.ReasonRetired", "Ceased")
	RefdataCategory.lookupOrCreate("TitleInstance.ReasonRetired", "Transferred")
	
	RefdataCategory.lookupOrCreate('Org.Mission','Academic')
	RefdataCategory.lookupOrCreate('Org.Mission','Commercial')
	RefdataCategory.lookupOrCreate('Org.Mission','Community Agency')
	RefdataCategory.lookupOrCreate('Org.Mission','Consortium')
	
	RefdataCategory.lookupOrCreate('Org.Role','Licensor')
	RefdataCategory.lookupOrCreate('Org.Role','Licensee')
	RefdataCategory.lookupOrCreate('Org.Role','Broker')
	RefdataCategory.lookupOrCreate('Org.Role','Vendor')
	RefdataCategory.lookupOrCreate('Org.Role','Content Provider')
	RefdataCategory.lookupOrCreate('Org.Role','Platform Provider')
	RefdataCategory.lookupOrCreate('Org.Role','Issuing Body')
	RefdataCategory.lookupOrCreate('Org.Role','Publisher')
	RefdataCategory.lookupOrCreate('Org.Role','Imprint')

//    RefdataCategory.lookupOrCreate("Combo.Type", "Content Provider");
    RefdataCategory.lookupOrCreate("Combo.Status", "Active");

//    RefdataCategory.lookupOrCreate('ComboType','Unknown');
//    RefdataCategory.lookupOrCreate('ComboType','Previous');
//    RefdataCategory.lookupOrCreate('ComboType','Model');
//    RefdataCategory.lookupOrCreate('ComboType','Parent');
//    RefdataCategory.lookupOrCreate('ComboType','Translated');
//    RefdataCategory.lookupOrCreate('ComboType','Absorbed');
//    RefdataCategory.lookupOrCreate('ComboType','Merged');
//    RefdataCategory.lookupOrCreate('ComboType','Renamed');
//    RefdataCategory.lookupOrCreate('ComboType','Split');
//    RefdataCategory.lookupOrCreate('ComboType','Transferred');

    RefdataCategory.lookupOrCreate('Org.Mission','Academic');
    RefdataCategory.lookupOrCreate('Org.Mission','Commercial');
    RefdataCategory.lookupOrCreate('Org.Mission','Community Agency');
    RefdataCategory.lookupOrCreate('Org.Mission','Consortium');

    RefdataCategory.lookupOrCreate('License.Type','Template');
    RefdataCategory.lookupOrCreate('License.Type','Other');
  }
}
