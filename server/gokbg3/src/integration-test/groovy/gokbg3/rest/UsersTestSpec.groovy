package gokbg3.rest

import grails.converters.JSON
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.CuratoryGroup
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
  CuratoryGroup cg
  Role role

  def setup() {
    role = Role.findByAuthority("ROLE_USER")
    cg = CuratoryGroup.findByName("UserTestGroup") ?: new CuratoryGroup(name: "UserTestGroup").save(flush: true)
    delUser = User.findByUsername("deleteUser") ?: new User(username: "deleteUser", curatoryGroups: [cg]).save(flush: true)
    UserRole.findOrCreateByRoleAndUser(role, delUser).save(flush: true)
    altUser = User.findByUsername("altUser") ?: new User(username: "altUser", curatoryGroups: [cg], enabled: true).save(flush: true)
    UserRole.findOrCreateByRoleAndUser(role, altUser).save(flush: true)
    if (!rest) {
      RestTemplate restTemp = new RestTemplate()
      restTemp.setRequestFactory(new HttpComponentsClientHttpRequestFactory())
      rest = new RestBuilder(restTemp)
    }
  }

  def cleanup() {
    sleep(500)
    UserRole.findAllByUser(delUser).each { ur ->
      ur.delete(flush: true)
    }
    UserRole.findAllByUser(User.findByUsername("newerUser")).each { ur ->
      ur.delete(flush: true)
    }
    UserRole.findAllByUser(altUser).each { ur ->
      ur.delete(flush: true)
    }
    User user = User.findByUsername(delUser.username)
    if (user) {
      user.curatoryGroups -= cg
      user.delete(flush: true)
    }
    user = User.findByUsername(altUser.username)
    if (user) {
      user.curatoryGroups -= cg
      user.delete(flush: true)
    }
    user = User.findByUsername("newerUser")
    if (user) {
      user.curatoryGroups -= cg
      user.delete(flush: true)
    }
    CuratoryGroup group = CuratoryGroup.findByName(cg.name)
    group.expunge()
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
    RestResponse resp = rest.get("${urlPath}/rest/users/?name=Use&roleId=$role.id&curatoryGroupId=$cg.id&_embed=id,organisations,roles&_include=id,username&_sort=username&_order=desc&offset=0&limit=10") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    resp.json.data[1].username == "altUser"
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
    resp.status == 204 // OK
    sleep(500)
    def checkUser = User.findById(delUser.id)
    checkUser == null
  }

  void "test PUT /rest/users/{id}"() {
    def urlPath = getUrlPath()
    def contributorRole = Role.findByAuthority('ROLE_CONTRIBUTOR')
    def userRole = Role.findByAuthority('ROLE_USER')
    def editorRole = Role.findByAuthority('ROLE_EDITOR')
    def adminRole = Role.findByAuthority('ROLE_ADMIN')
    def apiRole = Role.findByAuthority('ROLE_API')

    // use the bearerToken to write to /rest/user
    when:
    String accessToken = getAccessToken()
    Map bodyData = [displayName     : "DisplayName",
                    password        : "secr3t",
                    email           : "nobody@localhost",
                    curatoryGroupIds: [cg.id],
                    enabled         : true,
                    accountExpired  : false,
                    accountLocked   : false,
                    passwordExpired : false,
                    defaultPageSize : 15,
                    roleIds         : [contributorRole.id, userRole.id, editorRole.id, apiRole.id]
    ]
    RestResponse resp = rest.put("${urlPath}/rest/users/$altUser.id") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body(bodyData as JSON)
    }
    then:
    resp.status == 200
    resp.json.data.defaultPageSize == 15
    sleep(500)
    def checkUser = User.findById(altUser.id)
    !checkUser.authorities.contains(adminRole)
    checkUser.authorities.contains(userRole)
    checkUser.email == "nobody@localhost"
  }

  void "test PATCH /rest/users/{id}"() {
    def urlPath = getUrlPath()
    def contributorRole = Role.findByAuthority('ROLE_CONTRIBUTOR')
    def userRole = Role.findByAuthority('ROLE_USER')
    def editorRole = Role.findByAuthority('ROLE_EDITOR')

    when:
    String accessToken = getAccessToken()
    Map bodyData = [
      displayName     : "DisplayName",
      password        : "someOther",
      enabled         : false,
      defaultPageSize : 18,
      roleIds         : [contributorRole.id, userRole.id, editorRole.id],
      curatoryGroupIds: []
    ]

    def bodyText = bodyData as JSON
    RestResponse resp = rest.patch("${urlPath}/rest/users/$altUser.id") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body(bodyData as JSON)
    }
    then:
    resp.status == 200
    resp.json.data.defaultPageSize == 18
    sleep(500)
    def checkUser = User.findById(altUser.id).refresh()
    checkUser.enabled == false
    checkUser.curatoryGroups.size() == 0
  }

  void "test PATCH /rest/users/{id} with invalid data"() {
    def urlPath = getUrlPath()
    def contributorRole = Role.findByAuthority('ROLE_CONTRIBUTOR')
    def userRole = Role.findByAuthority('ROLE_USER')
    def editorRole = Role.findByAuthority('ROLE_EDITOR')
    when:
    String accessToken = getAccessToken()
    Map bodyData = [
      displayName     : "DisplayName",
      password        : "someOther",
      enabled         : false,
      defaultPageSize : 18,
      roleIds         : [contributorRole.id, userRole.id, editorRole.id],
      curatoryGroupIds: [],
      organisation    : 666
    ]

    def bodyText = bodyData as JSON
    RestResponse resp = rest.patch("${urlPath}/rest/users/$altUser.id") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body(bodyData as JSON)
    }
    then:
    resp.status == 400
    resp.json.data == null
    sleep(500)
    def checkUser = User.findById(altUser.id).refresh()
    checkUser.enabled == true
    checkUser.curatoryGroups.size() == 1
  }

  void "test POST /rest/users"() {
    def urlPath = getUrlPath()
    def contributorRole = Role.findByAuthority('ROLE_CONTRIBUTOR')
    def userRole = Role.findByAuthority('ROLE_USER')
    def editorRole = Role.findByAuthority('ROLE_EDITOR')
    when:
    String accessToken = getAccessToken()
    Map bodyData = [
      username        : "newerUser",
      email           : "nobody@localhost",
      password        : "defaultPassword",
      displayName     : "DisplayName",
      enabled         : true,
      defaultPageSize : 18,
      roleIds         : [contributorRole.id, editorRole.id],
      curatoryGroupIds: [cg.id]
    ]
    RestResponse resp = rest.post("${urlPath}/rest/users") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body(bodyData as JSON)
    }
    then:
    resp.status == 201
    resp.json.data.username == "newerUser"
    sleep(500)
    User checkUser = User.findById(resp.json.data.id)
    checkUser.hasRole("ROLE_USER")
    checkUser != null
  }

  /*
  * /register is not implemented for security reasons.
  */

  @Ignore
  void "test POST /rest/register"() {
    when:
    RestResponse resp = rest.post("${urlPath}/rest/register") {
      // headers
      accept('application/json')
      contentType('application/json')
      body([username: "newerUser", email: "nobody@localhost", password: "defaultPassword"] as JSON)
    }
    then:
    resp.status == 200
    sleep(500)
    User checkUser = User.findByUsername("newerUser")
    checkUser != null
  }
}
