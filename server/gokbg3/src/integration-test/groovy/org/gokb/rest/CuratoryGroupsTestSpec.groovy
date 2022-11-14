package org.gokb.rest

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.*
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.User
import spock.lang.Specification
import spock.lang.Shared

@Integration
@Rollback
class CuratoryGroupsTestSpec extends AbstractAuthSpec {


  HttpClient http

  def group1, group2, group3, group4, user

  def setup() {
    group1 = CuratoryGroup.findByName("Curatory Group A") ?: new CuratoryGroup(name: "Curatory Group A", email: "a@b.cd").save(flush: true)
    group2 = CuratoryGroup.findByName("Curatory Group B") ?: new CuratoryGroup(name: "Curatory Group B").save(flush: true)
    group3 = CuratoryGroup.findByName("Curatory Group C") ?: new CuratoryGroup(name: "Curatory Group C").save(flush: true)
    group4 = CuratoryGroup.findByName("Curatory Group D") ?: new CuratoryGroup(name: "Curatory Group D").save(flush: true)
    user = User.findByUsername("groupUser")?:new User(username:"groupUser", password: "groupUser", enabled: true).save(flush:true)
  }

  def cleanup() {
    CuratoryGroup.findByName("Curatory Group A")?.expunge()
    CuratoryGroup.findByName("Curatory Group B")?.expunge()
    CuratoryGroup.findByName("Curatory Group C")?.expunge()
    CuratoryGroup.findByName("Curatory Group D")?.expunge()
    User.findByUsername("groupUser")?.delete(flush:true)
  }

  void "test GET /rest/curatoryGroups/{id}"() {
    def urlPath = getUrlPath()
    when:
    HttpRequest request = HttpRequest.GET("${urlPath}/rest/curatoryGroups/${group1.id}")
      .bearerAuth(getAccessToken("groupUser", "groupUser"))
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.OK
    resp.body().data.name == group1.name
    resp.body().data.email == group1.email
  }

  void "test GET /rest/curatoryGroups"() {
    def urlPath = getUrlPath()
    when:
    HttpRequest request = HttpRequest.GET("${urlPath}/rest/curatoryGroups?name=curatory")
      .bearerAuth(getAccessToken("groupUser", "groupUser"))
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.OK
    resp.body().data.size() == 6
    resp.body().data*.email.contains(group1.email)
  }

  void "test GET /rest/curatoryGroups with inverse sorting by name"() {
    def urlPath = getUrlPath()
    when:
    HttpRequest request = HttpRequest.GET("${urlPath}/rest/curatoryGroups?_sort=name&_order=desc")
      .bearerAuth(getAccessToken("groupUser", "groupUser"))
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.OK
    resp.body().data[5].id == group1.id
  }
}
