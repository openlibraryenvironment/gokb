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
import org.gokb.cred.Org
import org.gokb.cred.Platform
import org.gokb.TitleLookupService
import spock.lang.Specification
import spock.lang.Shared

@Integration
@Rollback
class PlatformTestSpec extends AbstractAuthSpec {


  BlockingHttpClient http

  def setupSpec(){
  }

  def setup() {
    if (!http) {
      http = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }

    def testProvider = Org.findByName("TestPltProvider") ?: new Org(name: "TestPltProvider").save(flush:true)

    Org.findByName("TestPltProviderUpd") ?: new Org(name: "TestPltProviderUpd").save(flush:true)
    Platform.findByName("TestPltNew") ?: new Platform(name: "TestPltNew").save(flush:true)

    if (!Platform.findByName("TestPltUpd")) {
      def testPlatformUpd = new Platform(name: "TestPltUpd").save(flush:true)
      testPlatformUpd.provider = testProvider
      testPlatformUpd.save(flush: true)
    }
    else {
      Platform.findByName("TestPltUpd")
    }
  }

  def cleanup() {
    sleep(500)
    Platform.findByName("TestPltUpd")?.refresh()?.expunge()
    Platform.findByName("TestPltUpdate")?.refresh()?.expunge()
    Platform.findByName("TestPltPost")?.refresh()?.expunge()
    Platform.findByName("TestPltNew")?.refresh()?.expunge()
    Org.findByName("TestPltProvider")?.refresh()?.expunge()
    Org.findByName("TestPltProviderUpd")?.refresh()?.expunge()
  }

  void "test /rest/platforms without token"() {
    given:

    def urlPath = getUrlPath()

    when:

    HttpRequest request = HttpRequest.GET("${urlPath}/rest/platforms")
    HttpResponse resp = http.exchange(request)

    then:

    resp.status == HttpStatus.OK
  }

  void "test platform index"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()

    when:

    HttpRequest request = HttpRequest.GET("${urlPath}/rest/platforms")
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:

    resp.status == HttpStatus.OK

    expect:

    resp.body()?.data?.size() > 0
  }

  void "test /rest/platforms/<id> with valid token"() {
    given:
    def id = Platform.findByName("TestPltNew").id
    def urlPath = getUrlPath()
    String accessToken = getAccessToken()

    when:

    HttpRequest request = HttpRequest.GET("${urlPath}/rest/platforms/$id")
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request)

    then:

    resp.status == HttpStatus.OK
  }

  void "test insert new platform"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()
    def provider = Org.findByName("TestPltProvider")
    Map json_record = [
      name: "TestPltPost",
      primaryUrl: "http://newplt.com",
      provider: provider.id
    ]

    when:

    HttpRequest request = HttpRequest.POST("${urlPath}/rest/platforms", json_record)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:

    resp.status == HttpStatus.CREATED

    expect:

    resp.body()?.name == "TestPltPost"
    resp.body()?.provider?.name == "TestPltProvider"
  }

  void "test platform update"() {
    given:
    def urlPath = getUrlPath()
    String accessToken = getAccessToken()
    def plt = Platform.findByName("TestPltUpd")
    def new_prov = Org.findByName("TestPltProviderUpd")

    def json_record = [
      name: "TestPltUpdate",
      primaryUrl: "http://updatedplt.com",
      provider: new_prov.id
    ]

    when:

    HttpRequest request = HttpRequest.PUT("${urlPath}/rest/platforms/$plt.id", json_record)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:

    resp.status == HttpStatus.OK

    expect:

    resp.body().name == "TestPltUpdate"
    resp.body().primaryUrl == "http://updatedplt.com"
    resp.body().provider?.name == "TestPltProviderUpd"
  }
}
