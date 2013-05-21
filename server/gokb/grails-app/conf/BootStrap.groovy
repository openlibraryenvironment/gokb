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
    RefdataCategory.lookupOrCreate("Component.Status", "Current")
    RefdataCategory.lookupOrCreate("Component.Status", "Deleted")
    RefdataCategory.lookupOrCreate("Component.Status", "Expected")
    RefdataCategory.lookupOrCreate("Component.Status", "Retired")

    RefdataCategory.lookupOrCreate("Edit.Status", "Approved")
    RefdataCategory.lookupOrCreate("Edit.Status", "In-Process")
    RefdataCategory.lookupOrCreate("Edit.Status", "Rejected")

    RefdataCategory.lookupOrCreate("tipp.format", "Digitised")
    RefdataCategory.lookupOrCreate("tipp.format", "Electronic")
    RefdataCategory.lookupOrCreate("tipp.format", "Print")

    RefdataCategory.lookupOrCreate("tipp.delatedOA", "No")
    RefdataCategory.lookupOrCreate("tipp.delatedOA", "Unknown")
    RefdataCategory.lookupOrCreate("tipp.delatedOA", "Yes")

    RefdataCategory.lookupOrCreate("tipp.hybridOA", "No")
    RefdataCategory.lookupOrCreate("tipp.hybridOA", "Unknown")
    RefdataCategory.lookupOrCreate("tipp.hybridOA", "Yes")

    RefdataCategory.lookupOrCreate("tipp.isPrimary", "Yes")
    RefdataCategory.lookupOrCreate("tipp.isPrimary", "No")

    RefdataCategory.lookupOrCreate("paymentType", "Complimentary")
    RefdataCategory.lookupOrCreate("paymentType", "Limited Promotion")
    RefdataCategory.lookupOrCreate("paymentType", "Paid")
    RefdataCategory.lookupOrCreate("paymentType", "Opt Out Promotion")
    RefdataCategory.lookupOrCreate("paymentType", "Uncharged")
    RefdataCategory.lookupOrCreate("paymentType", "Unkown")

    RefdataCategory.lookupOrCreate("Combo.Status", "Active")
    RefdataCategory.lookupOrCreate('Org Role','Licensor');
    RefdataCategory.lookupOrCreate('Org Role','Licensee');
    RefdataCategory.lookupOrCreate('Org Role','Broker');
    RefdataCategory.lookupOrCreate('Org Role','Vendor');
    RefdataCategory.lookupOrCreate('Org Role','Content Provider');
    RefdataCategory.lookupOrCreate('Org Role','Platform Provider');
    RefdataCategory.lookupOrCreate('Org Role','Issuing Body');
    RefdataCategory.lookupOrCreate('Org Role','Publisher');

    RefdataCategory.lookupOrCreate("Package Status", "Aggregator");
    RefdataCategory.lookupOrCreate("Package Status", "Back File");
    RefdataCategory.lookupOrCreate("Package Status", "Front File");
    RefdataCategory.lookupOrCreate("Package Status", "Master File");

    RefdataCategory.lookupOrCreate("Pkg.Breakable", "Yes");
    RefdataCategory.lookupOrCreate("Pkg.Breakable", "No");
    RefdataCategory.lookupOrCreate("Pkg.Breakable", "Unknown");

    RefdataCategory.lookupOrCreate("Pkg.Consisitent", "Yes");
    RefdataCategory.lookupOrCreate("Pkg.Consisitent", "No");
    RefdataCategory.lookupOrCreate("Pkg.Consisitent", "Unkown");

    RefdataCategory.lookupOrCreate("Pkg.Fixed", "Yes");
    RefdataCategory.lookupOrCreate("Pkg.Fixed", "No");
    RefdataCategory.lookupOrCreate("Pkg.Fixed", "Unknown");

    RefdataCategory.lookupOrCreate("Pkg.Global", "Consortium");
    RefdataCategory.lookupOrCreate("Pkg.Global", "Global");
    RefdataCategory.lookupOrCreate("Pkg.Global", "Other");

    RefdataCategory.lookupOrCreate("AuthMethod", "IP");
    RefdataCategory.lookupOrCreate("AuthMethod", "Shibboleth");
    RefdataCategory.lookupOrCreate("AuthMethod", "User Password");
    RefdataCategory.lookupOrCreate("AuthMethod", "Unkown");

    RefdataCategory.lookupOrCreate("PlatformRole", "Admin");
    RefdataCategory.lookupOrCreate("PlatformRole", "Host");

    RefdataCategory.lookupOrCreate("Title.Material", "A & I Database");
    RefdataCategory.lookupOrCreate("Title.Material", "Audio");
    RefdataCategory.lookupOrCreate("Title.Material", "Book");
    RefdataCategory.lookupOrCreate("Title.Material", "Dataset");
    RefdataCategory.lookupOrCreate("Title.Material", "Film");
    RefdataCategory.lookupOrCreate("Title.Material", "Image");
    RefdataCategory.lookupOrCreate("Title.Material", "Journal");

    RefdataCategory.lookupOrCreate("Title.ReasonRetired", "Ceased");
    RefdataCategory.lookupOrCreate("Title.ReasonRetired", "Transferred");

    RefdataCategory.lookupOrCreate("Combo.Type", "ContentProvider");
    RefdataCategory.lookupOrCreate("Combo.Status", "Active");

    RefdataCategory.lookupOrCreate('ComboType','Unknown');
    RefdataCategory.lookupOrCreate('ComboType','Previous');
    RefdataCategory.lookupOrCreate('ComboType','Model');
    RefdataCategory.lookupOrCreate('ComboType','Parent');
    RefdataCategory.lookupOrCreate('ComboType','Translated');
    RefdataCategory.lookupOrCreate('ComboType','Absorbed');
    RefdataCategory.lookupOrCreate('ComboType','Merged');
    RefdataCategory.lookupOrCreate('ComboType','Renamed');
    RefdataCategory.lookupOrCreate('ComboType','Split');
    RefdataCategory.lookupOrCreate('ComboType','Transferred');

    RefdataCategory.lookupOrCreate('Org.Mission','Academic');
    RefdataCategory.lookupOrCreate('Org.Mission','Commercial');
    RefdataCategory.lookupOrCreate('Org.Mission','Community Agency');
    RefdataCategory.lookupOrCreate('Org.Mission','Consortium');

    RefdataCategory.lookupOrCreate('License.Type','Template');
    RefdataCategory.lookupOrCreate('License.Type','Other');
  }
}
