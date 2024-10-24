package org.gokb.rest

import grails.converters.JSON
import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.*

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.uri.UriBuilder

import org.gokb.cred.KBComponent
import org.gokb.cred.Office
import org.gokb.cred.Org
import org.gokb.cred.Platform
import org.gokb.TitleLookupService
import org.gokb.cred.RefdataCategory
import org.gokb.cred.Source

import spock.lang.Specification
import spock.lang.Shared

@Integration
@Rollback
class OrgTestSpec extends AbstractAuthSpec {


  BlockingHttpClient http

  Platform test_org_plt
  Platform test_org_plt_update
  Org test_org

  def setupSpec() {
  }

  def setup() {
    if (!http) {
      http = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }

    Source new_source = Source.findByName("TestOrgPatchSource") ?: new Source(name: "TestOrgPatchSource").save(flush: true)
    Office new_office = Office.findByName("firstTestOffice") ?: new Office(name: "firstTestOffice").save(flush: true)
    test_org = Org.findByName("TestOrgPatch") ?: new Org(name: "TestOrgPatch", source: new_source, offices:[new_office]).save(flush: true)
    test_org_plt = Platform.findByName("TestOrgPlt") ?: new Platform(name: "TestOrgPlt").save(flush: true)
    test_org_plt_update = Platform.findByName("TestOrgPltUpdate") ?: new Platform(name: "TestOrgPltUpdate", provider: test_org).save(flush: true)
  }

  def cleanup() {
    Office.list().each {
      it.refresh()?.expunge()
    }

    Platform.list().each {
      it.expunge()
    }

    Org.list().each {
      it.expunge()
    }

    Source.findByName("TestOrgPatchSource")?.expunge()
  }

  void "test /rest/orgs without token"() {
    given:

    def urlPath = getUrlPath()

    when:

    HttpRequest request = HttpRequest.GET("${urlPath}/rest/orgs")
    HttpResponse resp = http.exchange(request)

    then:

    resp.status == HttpStatus.OK
  }

  void "test /rest/orgs/<id> with valid token"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()

    when:

    HttpRequest request = HttpRequest.GET("${urlPath}/rest/orgs/${Org.findByName("TestOrgPatch").id}")
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:

    resp.status == HttpStatus.OK
    resp.body().name == "TestOrgPatch"
  }

  void "test insert new org"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()
    Map json_record = [
      name             : "TestOrgPost",
      ids              : [
        [namespace: "global", value: "test-org-id-val"]
      ],
      providedPlatforms: ["TestOrgPlt"],
      offices: [
          [name: "TestOffice1",
           function: "Technical Support"],
          [name: "TestOffice2",
           function: "other"],
          [name: "TestOffice3"]
      ],
    ]

    when:

    HttpRequest request = HttpRequest.POST("${urlPath}/rest/orgs", json_record)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:

    resp.status == HttpStatus.CREATED

    expect:
    resp.body()?.name == "TestOrgPost"
    resp.body()?._embedded?.ids?.size() == 1
    resp.body()?._embedded?.offices?.size()==3
    resp.body()?._embedded?.offices*.function.name.count("Technical Support")==2
    resp.body()?._embedded?.offices*.function.name.count("Other")
  }

  void "test org index"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()

    when:

    HttpRequest request = HttpRequest.GET("${urlPath}/rest/orgs")
    HttpResponse resp = http.exchange(request, Map)

    then:

    resp.status == HttpStatus.OK

    expect:

    resp.body()?.data?.size() > 0
  }

  void "test org update"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()

    Map update_record = [
      name             : "TestOrgUpdateNew",
      ids              : [
        [namespace: "viaf", value: "4870153184551227100006"]
      ],
      providedPlatforms: [test_org_plt.id],
      offices: [
        [name: "2ndTestOffice1", function:"Technical Support"],
        [name: "2ndTestOffice2", function:"other"]
      ]
    ]

    when:
    URI uri = UriBuilder.of(urlPath)
      .path("/rest/orgs/$test_org.id")
      .queryParam('_embed', 'providedPlatforms,ids,offices')
      .build()

    HttpRequest request = HttpRequest.PUT(uri, update_record)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)
    then:

    resp.status == HttpStatus.OK

    expect:

    resp.body().name == "TestOrgUpdateNew"
    resp.body()._embedded.ids?.size() == 1
    resp.body()._embedded.providedPlatforms?.size() == 1
    resp.body()._embedded.providedPlatforms[0].name == test_org_plt.name
    resp.body()._embedded.offices?.size() == 2
    resp.body()._embedded.offices*.function.name.contains("Other")
  }

  void "test source delete"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()

    Map update_record = [
      name: "TestOrgUpdateSource",
      source: null
    ]

    when:

    HttpRequest request = HttpRequest.PUT("${urlPath}/rest/orgs/$test_org.id", update_record)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)
    then:

    resp.status == HttpStatus.OK

    expect:

    resp.body().name == "TestOrgUpdateSource"
    resp.body().source == null
    resp.body()._embedded?.providedPlatforms?.size() == 1
  }
}
