import com.k_int.describe.*;

class BootStrap {

  def init = { servletContext ->

    def userRole = Role.findByAuthority('ROLE_USER') ?: new Role(authority: 'ROLE_USER').save(failOnError: true)
    def editorRole = Role.findByAuthority('ROLE_EDITOR') ?: new Role(authority: 'ROLE_EDITOR').save(failOnError: true)
    def adminRole = Role.findByAuthority('ROLE_ADMIN') ?: new Role(authority: 'ROLE_ADMIN').save(failOnError: true)

    // if ( grailsApplication.config.localauth ) {
    if ( true  ) {
      log.debug("localauth is set.. ensure user accounts present");

      log.debug("Create admin user...");
      def adminUser = User.findByUsername('admin')
      if ( ! adminUser ) {
        def newpass = java.util.UUID.randomUUID().toString()
        log.error("No admin user found, create with temporary password ${newpass}")
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
    }
  }

  def destroy = {
  }
}
