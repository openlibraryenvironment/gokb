package org.gokb.rest

import grails.gorm.transactions.*
import grails.testing.mixin.integration.Integration
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import org.gokb.cred.Role
import org.gokb.cred.User
import org.gokb.cred.UserRole
import spock.lang.Specification
import spock.lang.Shared

@Integration
@Rollback
class RolesTestSpec extends AbstractAuthSpec {


  HttpClient http

  def setup() {
    User rolesUser = User.findByUsername("rolesUser") ?: new User(username: "rolesUser", password: "rolesUser", enabled: true).save(flush: true)
    UserRole ur = UserRole.findOrCreateByRoleAndUser(Role.findWhere(authority: "ROLE_USER"), rolesUser).save(flush: true)
  }

  void "test GET /rest/roles"() {
    given:
      def urlPath = getUrlPath()
    when:
    String token = getAccessToken("rolesUser", "rolesUser")
    HttpRequest request = HttpRequest.GET("$urlPath/rest/roles")
      .bearerAuth(accessToken)
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.OK
    resp.body().data.size() == 6
  }
}
