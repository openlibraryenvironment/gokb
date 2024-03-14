package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.*
import grails.testing.mixin.integration.Integration

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.uri.UriBuilder

import org.gokb.cred.CuratoryGroup
import org.gokb.cred.Role
import org.gokb.cred.User
import org.gokb.cred.UserRole
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Shared

@Integration
@Rollback
class UsersTestSpec extends AbstractAuthSpec {

  def delUser
  def altUser
  CuratoryGroup cg
  Role role


  BlockingHttpClient http

  def setup() {
    if (!http) {
      http = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }

    role = Role.findByAuthority("ROLE_USER")
    cg = CuratoryGroup.findByName("UserTestGroup") ?: new CuratoryGroup(name: "UserTestGroup").save(flush: true)
    delUser = User.findByUsername("UserTestDeleteUser") ?: new User(username: "UserTestDeleteUser", curatoryGroups: [cg]).save(flush: true)
    UserRole.findOrCreateByRoleAndUser(role, delUser).save(flush: true)
    altUser = User.findByUsername("altUser") ?: new User(username: "altUser", curatoryGroups: [cg], enabled: true).save(flush: true)
    UserRole.findOrCreateByRoleAndUser(role, altUser).save(flush: true)
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
      user.delete(flush: true)
    }
    user = User.findByUsername(altUser.username)
    if (user) {
      user.delete(flush: true)
    }
    user = User.findByUsername("newerUser")
    if (user) {
      user.delete(flush: true)
    }
    CuratoryGroup group = CuratoryGroup.findByName(cg.name)
    group.expunge()
  }

  void "test GET /rest/users/{id} without token"() {
    def urlPath = getUrlPath()
    when:
    HttpRequest request = HttpRequest.GET("${urlPath}/rest/users/$altUser.id")
    HttpStatus status

    try {
      HttpResponse resp = http.exchange(request, Map)
    } catch (io.micronaut.http.client.exceptions.HttpClientResponseException e) {
      status = e.status
    }

    then:
    status == HttpStatus.UNAUTHORIZED
  }

  void "test GET /rest/users/{id} with valid token"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    URI uri = UriBuilder.of(urlPath)
      .path("rest/users/$altUser.id")
      .build()

    HttpRequest request = HttpRequest.GET(uri)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().data.username == "altUser"
  }

  void "test GET /rest/users?{params} with valid token and parameters"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    URI uri = UriBuilder.of(urlPath)
      .path("/rest/users")
      .queryParam('name', 'Use')
      .queryParam('roleId', role.id)
      .queryParam('curatoryGroupId', cg.id)
      .queryParam('_embed', 'id,organisations,roles')
      .queryParam('_include', 'id,username')
      .queryParam('_sort', 'username')
      .queryParam('_order', 'desc')
      .queryParam('offset', '0')
      .queryParam('limit', '10')
      .build()

    HttpRequest request = HttpRequest.GET(uri)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().data[1].username == "altUser"
  }

  void "test DELETE /rest/users/{id} with valid token"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.DELETE("${urlPath}/rest/users/$delUser.id")
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.NO_CONTENT
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
    HttpRequest request = HttpRequest.PUT("${urlPath}/rest/users/$altUser.id", bodyData)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().data.defaultPageSize == 15
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

    HttpRequest request = HttpRequest.PATCH("${urlPath}/rest/users/$altUser.id", bodyData)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().data.defaultPageSize == 18
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

    HttpRequest request = HttpRequest.PATCH("${urlPath}/rest/users/$altUser.id", bodyData)
      .bearerAuth(accessToken)

    HttpStatus status

    try {
      HttpResponse resp = http.exchange(request, Map)
    } catch (io.micronaut.http.client.exceptions.HttpClientResponseException e) {
      status = e.status
    }

    then:
    status == HttpStatus.BAD_REQUEST
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
    HttpRequest request = HttpRequest.POST("${urlPath}/rest/users", bodyData)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.CREATED
    resp.body().data.username == "newerUser"
    sleep(500)
    User checkUser = User.findById(resp.body().data.id)
    checkUser.hasRole("ROLE_USER")
    checkUser != null
  }

  void "test PATCH /rest/users/{id}/activate without notification"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()

    HttpRequest request = HttpRequest.PATCH("${urlPath}/rest/users/$altUser.id/activate")
      .bearerAuth(accessToken)

    HttpStatus status

    try {
      HttpResponse resp = http.exchange(request, Map)
    } catch (io.micronaut.http.client.exceptions.HttpClientResponseException e) {
      status = e.status
    }

    then:
    status == HttpStatus.OK
    sleep(500)
    def checkUser = User.findById(altUser.id).refresh()
    checkUser.enabled == true
    checkUser.accountLocked == false
    checkUser.hasRole('ROLE_USER')
    checkUser.hasRole('ROLE_CONTRIBUTOR')
    checkUser.hasRole('ROLE_EDITOR')
  }
}
