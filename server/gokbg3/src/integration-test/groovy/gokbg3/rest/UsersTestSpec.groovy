package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.Role
import org.gokb.cred.User
import org.gokb.cred.UserRole
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import spock.lang.Ignore

@Integration
@Rollback
class UsersTestSpec extends AbstractAuthSpec {

  def rest
  def delUser
  def altUser

  def setup() {
    delUser = User.findByUsername("deleteUser") ?: new User(username: "deleteUser").save(flush: true)
    altUser = User.findByUsername("altUser") ?: new User(username: "altUser", enabled: true).save(flush: true)
    if (!rest) {
      RestTemplate restTemp = new RestTemplate()
      restTemp.setRequestFactory(new HttpComponentsClientHttpRequestFactory())
      rest = new RestBuilder(restTemp)
    }
  }

  def cleanup() {
    UserRole.findAllByUser(delUser).each { ur ->
      ur.delete(flush: true)
    }
    UserRole.findAllByUser(altUser).each { ur ->
      ur.delete(flush: true)
    }
    User.findByUsername('delUser')?.delete(flush: true)
    User.findByUsername('altUser')?.delete(flush: true)
  }

  void "test GET /rest/users/{id} without token"() {
    def urlPath = getUrlPath()
    when:
    RestResponse resp = rest.get("${urlPath}/rest/users/$altUser.id") {
      // headers
      accept('application/json')
    }
    then:
    resp.status in [401, 403] // Unauthorized/Forbidden
  }

  void "test GET /rest/users/{id} with valid token"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("${urlPath}/rest/users/$delUser.id?_embed=id,organisations,roles&_include=id,name") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    resp.json.data.username == "deleteUser"
  }

  void "test GET /rest/users/?{params} with valid token and parameters"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("${urlPath}/rest/users/?name=m&roleId=3&_embed=id,organisations,roles&_include=id,username&_sort=username&_order=desc") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    resp.json.data[1].username == "admin"
  }

  void "test DELETE /rest/users/{id} with valid token"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.delete("${urlPath}/rest/users/$delUser.id") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    def checkUser = User.findById(delUser.id)
    checkUser == null
  }

  void "test PUT /rest/users/{id}"() {
    def urlPath = getUrlPath()
    // use the bearerToken to write to /rest/user
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.put("${urlPath}/rest/users/$altUser.id") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body('{id: + altUser.id + ,' +
        'username:"OtherUser",' +
        'displayName:"DisplayName",' +
        'email:"nobody@localhost",' +
        'curatoryGroups:[],' +
        'enabled:true,' +
        'accountExpired:false,' +
        'accountLocked:false,' +
        'passwordExpired:false,' +
        'defaultPageSize:15,' +
        'roles:[' +
        '{' +
        'authority:"ROLE_CONTRIBUTOR",' +
        '},' +
        '{' +
        'authority:"ROLE_USER",' +
        '},' +
        '{' +
        'authority:"ROLE_EDITOR",' +
        '}' +
        ']' +
        '}')
    }
    then:
    resp.status == 200
    resp.json.data.defaultPageSize == 15
    def checkUser = User.findById(altUser.id)
    !checkUser.authorities.contains(Role.findByAuthority("ROLE_ADMIN"))
    checkUser.authorities.contains(Role.findByAuthority("ROLE_USER"))
    checkUser.username != "OtherUser"
    checkUser.email == "nobody@localhost"
  }

  void "test PATCH /rest/users/{id}"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.patch("${urlPath}/rest/users/$altUser.id") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body('{displayName:"DisplayName",' +
        'enabled:false,' +
        'defaultPageSize:18,' +
        'roles:[' +
        '{' +
        'authority:"ROLE_CONTRIBUTOR",' +
        '},' +
        '{' +
        'authority:"ROLE_USER",' +
        '},' +
        '{' +
        'authority:"ROLE_EDITOR",' +
        '},' +
        ']' +
        '}')
    }
    then:
    resp.status == 200
    resp.json.data.defaultPageSize == 18
    def checkUser = User.findById(altUser.id)
    checkUser.enabled == false
  }

  void "test POST /rest/users"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.post("${urlPath}/rest/users") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body('{"username":"newerUser", "email":"nobody@localhost","password":"defaultPassword"}')
    }
    then:
    resp.status == 200
    resp.json.data.username == "newerUser"
  }

  /*
  * /register is not implemented for security reasons.
  */

  @Ignore
  void "test POST /rest/register"() {
    when:
    RestResponse resp = rest.post("http://localhost:$serverPort/gokb/rest/register") {
      // headers
      accept('application/json')
      contentType('application/json')
      body('{"username":"newUser", "email":"nobody@localhost","password":"defaultPassword"}')
    }
    then:
    resp.status == 200
    User checkUser = User.findByUsername("newUser")
    checkUser != null
  }
}
