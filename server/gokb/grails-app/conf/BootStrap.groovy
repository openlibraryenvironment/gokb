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


    refdataCats();

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
      
      // Extend the method missing.
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
    RefdataCategory.lookupOrCreate("Combo.Status", "Active")
    RefdataCategory.lookupOrCreate('Org Role','Content Provider');
    RefdataCategory.lookupOrCreate("Package Status", "Current");
    RefdataCategory.lookupOrCreate("Package Scope", "Front File");
    RefdataCategory.lookupOrCreate("Pkg.Breakable", "Y");
    RefdataCategory.lookupOrCreate("Pkg.Parent", "N");
    RefdataCategory.lookupOrCreate("Pkg.Global", "Y");
    RefdataCategory.lookupOrCreate("Pkg.Fixed", "Y");
    RefdataCategory.lookupOrCreate("Pkg.Consisitent", "N");
    RefdataCategory.lookupOrCreate("Combo.Type", "ContentProvider");
    RefdataCategory.lookupOrCreate("Combo.Status", "Active");
    RefdataCategory.lookupOrCreate('ComboType','Unknown');
  }
}
