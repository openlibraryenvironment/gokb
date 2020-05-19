package gokbg3.rest

import grails.gorm.transactions.Transactional
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.Role
import org.gokb.cred.User
import org.gokb.cred.UserRole
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Integration
@Rollback
class ProfileTestSpec extends AbstractAuthSpec {

  private RestBuilder rest
  private User normalUser

  @Transactional
  def setup() {
    if (!rest) {
      RestTemplate restTemp = new RestTemplate()
      restTemp.setRequestFactory(new HttpComponentsClientHttpRequestFactory())
      rest = new RestBuilder(restTemp)
    }
    normalUser = User.findByUsername("normalUser") ?: new User(username: "normalUser", password: "normalUser", enabled: true, email: 'someone@somewhere.org').save(flush: true)
    Role roleUser = Role.findByAuthority('ROLE_USER')
    if (!normalUser.hasRole('ROLE_USER')) {
      UserRole.create normalUser, roleUser
    }
  }

  @Transactional
  def cleanup() {
    UserRole.findAllByUser(normalUser)?.each { ur ->
      ur.delete(flush: true)
    }
    User.findByUsername("normalUser")?.delete(flush: true)
  }

  void "test GET /rest/profile without token"() {
    def urlPath = getUrlPath()
    when:
    RestResponse resp = rest.get("${urlPath}/rest/profile") {
      // headers
      accept('application/json')
    }
    then:
    resp.status == 401 // Unauthorized
  }

  void "test GET /rest/profile with valid token"() {
    def urlPath = getUrlPath()
    // use the bearerToken to read /rest/profile
    when:
    String accessToken = getAccessToken('normalUser', 'normalUser')
    RestResponse resp = rest.get("${urlPath}/rest/profile") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    resp.json.data.email == "someone@somewhere.org"
    resp.json.data._links.self.href == "rest/profile"
  }

  void "test GET /rest/profile with stale token"() {
    def urlPath = getUrlPath()
    // use the bearerToken to read /rest/profile
    when:
    String accessToken = getAccessToken('normalUser', 'normalUser')
    // logout => invalidate token on the server
    RestResponse resp = rest.post("${urlPath}/rest/logout") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body()
    }
    resp.status == 200
    // reuse the stale token
    resp = rest.get("${urlPath}/rest/profile") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 401 // Unauthorized
    when:
    resp = rest.get("${urlPath}/rest/profile") {
      // headers
      accept('application/json')
    }
    then:
    resp.status == 401 // Unauthorized
  }

  void "test PUT /rest/profile"() {
    def urlPath = getUrlPath()
    // use the bearerToken to write to /rest/profile/update
    when:
    String accessToken = getAccessToken("normalUser", "normalUser")
    Map bodyData = [data: [displayName    : null,
                           email          : "MrX@localhost",
                           enabled        : true,
                           accountExpired : false,
                           accountLocked  : false,
                           passwordExpired: false,
                           defaultPageSize: 10
    ]]
    RestResponse resp = rest.put("${urlPath}/rest/profile") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body(bodyData as JSON)
    }
    then:
    resp.status == 200
    resp.json.data.email == "MrX@localhost"
  }

  void "test PATCH /rest/profile"() {
    def urlPath = getUrlPath()
    // use the bearerToken to write to /rest/profile
    when:
    String accessToken = getAccessToken('normalUser', 'normalUser')
    Map bodyData = [data: [
      displayName : "tempo",
      email       : "frank@gmail.com",
      password    : "normalUser",
      new_password: "roles"
    ]]
    RestResponse resp = rest.patch("${urlPath}/rest/profile") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body(bodyData as JSON)
    }
    then:
    resp.status == 200
    def user = User.findByUsername("normalUser").refresh()
    getAccessToken('normalUser', 'roles') != null
    user.email == 'frank@gmail.com'
  }

  void "test DELETE /rest/profile/"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken('normalUser', 'normalUser')
    RestResponse resp = rest.delete("${urlPath}/rest/profile/") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200
    User.findByUsername('normalUser') == null
  }
}
