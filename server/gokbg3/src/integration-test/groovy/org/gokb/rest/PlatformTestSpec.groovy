package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.*
import grails.testing.mixin.integration.Integration
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import org.gokb.cred.Org
import org.gokb.cred.Platform
import org.gokb.TitleLookupService
import spock.lang.Specification
import spock.lang.Shared

@Integration
@Rollback
class PlatformTestSpec extends AbstractAuthSpec {


  HttpClient http

  def setupSpec(){
  }

  def setup() {
    def new_prov = Org.findByName("TestPltProvider") ?: new Org(name: "TestPltProvider").save(flush:true)
    def upd_prov = Org.findByName("TestPltProviderUpd") ?: new Org(name: "TestPltProviderUpd").save(flush:true)
    def upd_plt = Platform.findByName("TestPltUpd") ?: new Platform(name: "TestPltUpd").save(flush:true)
  }

  def cleanup() {
    if (Org.findByName("TestPltProvider")) {
      Org.findByName("TestPltProvider")?.refresh().expunge()
    }
    if (Org.findByName("TestPltProviderUpd")) {
      Org.findByName("TestPltProviderUpd")?.refresh().expunge()
    }
    if (Platform.findByName("TestPltUpd")) {
      Platform.findByName("TestPltUpd")?.refresh().expunge()
    }
  }

  void "test /rest/platforms without token"() {
    given:

    def urlPath = getUrlPath()

    when:

    HttpRequest request = HttpRequest.GET("${urlPath}/rest/platforms")
    HttpResponse resp = http.toBlocking().exchange(request)

    then:

    resp.status == HttpStatus.OK
  }

  void "test /rest/platforms/<id> with valid token"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()

    when:

    HttpRequest request = HttpRequest.GET("${urlPath}/rest/platforms")
      .bearerAuth(accessToken)
    HttpResponse resp = http.toBlocking().exchange(request)

    then:

    resp.status == HttpStatus.OK
  }

  void "test insert new platform"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()
    def provider = Org.findByName("TestPltProvider")
    def json_record = [
      name: "TestPltPost",
      primaryUrl: "http://newplt.com",
      provider: provider.id
    ]

    when:

    HttpRequest request = HttpRequest.POST("${urlPath}/rest/platforms", json_record as JSON)
      .bearerAuth(accessToken)
    HttpResponse resp = http.toBlocking().exchange(request)

    then:

    resp.status == HttpStatus.CREATED

    expect:

    resp.body()?.name == "TestPltPost"
    resp.body()?.provider?.name == "TestPltProvider"
  }

  void "test platform index"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()

    when:

    HttpRequest request = HttpRequest.GET("${urlPath}/rest/platforms")
      .bearerAuth(accessToken)
    HttpResponse resp = http.toBlocking().exchange(request)

    then:

    resp.status == HttpStatus.OK

    expect:

    resp.body()?.data?.size() > 0
  }

  void "test platform update"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()
    def id = Platform.findByName("TestPltUpd").id
    def new_prov = Org.findByName("TestPltProviderUpd")

    def json_record = [
      name: "TestPltUpdate",
      primaryUrl: "http://updatedplt.com",
      provider: new_prov.id
    ]

    when:

    HttpRequest request = HttpRequest.PUT("${urlPath}/rest/platforms/$id", json_record as JSON)
      .bearerAuth(accessToken)
    HttpResponse resp = http.toBlocking().exchange(request)

    then:

    resp.status == HttpStatus.OK

    expect:

    resp.body().name == "TestPltUpdate"
    resp.body().primaryUrl == "http://updatedplt.com"
    resp.body().provider?.name == "TestPltProviderUpd"
  }
}
