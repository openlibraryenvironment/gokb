package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.*
import grails.testing.mixin.integration.Integration
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.Role
import org.gokb.cred.User
import org.gokb.cred.UserRole
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import spock.lang.Specification
import spock.lang.Shared

@Integration
@Rollback
class ProfileTestSpec extends AbstractAuthSpec {

  private User normalUser
  private CuratoryGroup cg

  @Transactional
  def setup() {
    normalUser = User.findByUsername("normalUser") ?: new User(username: "normalUser", password: "normalUser", enabled: true, email: 'someone@somewhere.org').save(flush: true)
    Role roleUser = Role.findByAuthority('ROLE_USER')
    if (!normalUser.hasRole('ROLE_USER')) {
      UserRole.create normalUser, roleUser
    }
    cg = CuratoryGroup.findByName("UserTestGroup") ?: new CuratoryGroup(name: "UserTestGroup").save(flush: true)
  }

  @Transactional
  def cleanup() {
    sleep(200)
    UserRole.findAllByUser(normalUser)?.each { ur ->
      ur.delete(flush: true)
    }
    User.findByUsername("normalUser")?.delete(flush: true)
    CuratoryGroup group = CuratoryGroup.findByName(cg.name)
    group.expunge()
  }

  void "test GET /rest/profile without token"() {
    def urlPath = getUrlPath()
    when:
    HttpRequest request = HttpRequest.GET("${urlPath}/rest/profile")
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.UNAUTHORIZED
  }

  void "test GET /rest/profile with valid token"() {
    def urlPath = getUrlPath()
    // use the bearerToken to read /rest/profile
    when:
    String accessToken = getAccessToken('normalUser', 'normalUser')
    HttpRequest request = HttpRequest.GET("${urlPath}/rest/profile")
      .bearerAuth(accessToken)
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.OK
    resp.body().data.email == "someone@somewhere.org"
    resp.body().data._links.self.href.endsWith("rest/profile")
  }

  void "test GET /rest/profile with stale token"() {
    def urlPath = getUrlPath()
    // use the bearerToken to read /rest/profile
    when:
    String accessToken = getAccessToken('normalUser', 'normalUser')
    // logout => invalidate token on the server
    HttpRequest req1 = HttpRequest.POST("${urlPath}/rest/logout")
      .bearerAuth(accessToken)
    HttpResponse resp1 = http.toBlocking().exchange(req1)

    then:
    resp1.status == HttpStatus.OK

    when:
    // reuse the stale token
    HttpRequest req2 = HttpRequest.GET("${urlPath}/rest/profile")
      .bearerAuth(accessToken)
    HttpResponse resp2 = http.toBlocking().exchange(req2)

    then:
    resp2.status == HttpStatus.UNAUTHORIZED // Unauthorized
  }

  void "test PUT /rest/profile"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken("normalUser", "normalUser")
    Map bodyData = [displayName    : null,
                    email          : "MrX@localhost",
       //             curatoryGroupIds: [cg.id],
                    defaultPageSize: 10
    ]
    HttpRequest request = HttpRequest.PUT("${urlPath}/rest/profile", bodyData as JSON)
      .bearerAuth(accessToken)
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.OK
    resp.body().data.email == "MrX@localhost"
  }

  void "test PATCH /rest/profile"() {
    def urlPath = getUrlPath()
    def before = normalUser.password
    when:
    String accessToken = getAccessToken('normalUser', 'normalUser')
    Map bodyData = [
      displayName : "tempo",
      email       : "frank@gmail.com",
  //    curatoryGroupIds: [cg.id],
      password    : "normalUser",
      new_password: "roles"
    ]
    HttpRequest request = HttpRequest.PATCH("${urlPath}/rest/profile", bodyData as JSON)
      .bearerAuth(accessToken)
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.OK
    sleep(500)
    def user = User.findByUsername("normalUser").refresh()
    user.password != before
    user.email == 'frank@gmail.com'
  }

  void "test PUT /rest/profile new_password without old password"() {
    def urlPath = getUrlPath()
    def before = normalUser.password
    when:
    String accessToken = getAccessToken('normalUser', 'normalUser')
    Map bodyData = [
      new_password: "secr3t"
    ]
    HttpRequest request = HttpRequest.PUT("${urlPath}/rest/profile", bodyData as JSON)
      .bearerAuth(accessToken)
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.OK
    def user = User.findByUsername("normalUser").refresh()
    resp.body().data != null
    user.password == before
  }

  void "test PATCH /rest/profile with wrong data"() {
    def urlPath = getUrlPath()
    // use the bearerToken to write to /rest/profile
    when:
    String accessToken = getAccessToken('normalUser', 'normalUser')
    Map bodyData = [
      username        : "sRsLy?",
      displayName     : "tempo",
      email           : "frank@gmail.com",
      password        : "normalUser",
      curatoryGroupIds: [cg.id],
      new_password    : "roles"
    ]
    HttpRequest request = HttpRequest.PATCH("${urlPath}/rest/profile", bodyData as JSON)
      .bearerAuth(accessToken)
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.BAD_REQUEST
  }

  void "test POST /rest/profile/"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken('normalUser', 'normalUser')
    Map bodyData = [
      username   : "sRsLy?",
      displayName: "tempo",
      email      : "frank@gmail.com",
      password   : "otherthan"
    ]
    HttpRequest request = HttpRequest.POST("${urlPath}/rest/profile", bodyData as JSON)
      .bearerAuth(accessToken)
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.NOT_FOUND
  }
}
