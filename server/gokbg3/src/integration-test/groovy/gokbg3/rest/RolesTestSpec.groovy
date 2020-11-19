package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.Role
import org.gokb.cred.User
import org.gokb.cred.UserRole

@Integration
@Rollback
class RolesTestSpec extends AbstractAuthSpec {

  private RestBuilder rest = new RestBuilder()

  def setup() {
    User rolesUser = User.findByUsername("rolesUser") ?: new User(username: "rolesUser", password: "rolesUser", enabled: true).save(flush: true)
    UserRole ur = UserRole.findOrCreateByRoleAndUser(Role.findWhere(authority: "ROLE_USER"), rolesUser).save(flush: true)
  }

  void "test GET /rest/roles"() {
    given:
      def urlPath = getUrlPath()
    when:
    String token = getAccessToken("rolesUser", "rolesUser")
    RestResponse resp = rest.get("$urlPath/rest/roles") {
      // headers
      accept('application/json')
      auth("Bearer $token")
    }
    then:
    resp.status == 200
    resp.json.data.size() == 6
  }
}