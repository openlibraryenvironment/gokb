package org.gokb.rest

import grails.converters.JSON
import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.*
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
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


  HttpClient http

  def setupSpec() {
  }

  def setup() {
    def new_plt = Platform.findByName("TestOrgPlt") ?: new Platform(name: "TestOrgPlt").save(flush: true)
    def new_plt_upd = Platform.findByName("TestOrgPltUpdate") ?: new Platform(name: "TestOrgPltUpdate").save(flush: true)
    def new_source = Source.findByName("TestOrgPatchSource") ?: new Source(name: "TestOrgPatchSource").save(flush: true)
    def new_office = Office.findByName("firstTestOffice") ?: new Office(name: "firstTestOffice", language: RefdataCategory.lookup(KBComponent.RD_LANGUAGE, "ger"))
        new_office.save(flush: true)
    def patch_org = Org.findByName("TestOrgPatch") ?: new Org(name: "TestOrgPatch", source: new_source, offices:[new_office])
        patch_org.save(flush: true)
  }

  def cleanup() {
    if (Platform.findByName("TestOrgPlt")) {
      Platform.findByName("TestOrgPlt")?.refresh().expunge()
    }
    if (Platform.findByName("TestOrgPltUpdate")) {
      Platform.findByName("TestOrgPltUpdate")?.refresh().expunge()
    }
    Office.list().each {
      it.expunge()
    }
    if (Org.findByName("TestOrgPost")) {
        Org.findByName("TestOrgPost")?.refresh().expunge()
    }
    if (Org.findByName("TestOrgUpdateNew")) {
      Org.findByName("TestOrgUpdateNew")?.refresh().expunge()
    }
    if (Org.findByName("TestOrgUpdateSource")) {
      Org.findByName("TestOrgUpdateSource")?.refresh().expunge()

      if (Source.findByName("TestOrgPatchSource")) {
        Source.findByName("TestOrgPatchSource")?.refresh().expunge()
      }
    }
  }

  void "test /rest/orgs without token"() {
    given:

    def urlPath = getUrlPath()

    when:

    HttpRequest request = HttpRequest.GET("${urlPath}/rest/orgs")
    HttpResponse resp = http.toBlocking().exchange(request)

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
    HttpResponse resp = http.toBlocking().exchange(request)

    then:

    resp.status == HttpStatus.OK
    resp.body().name == "TestOrgPatch"
  }

  void "test insert new org"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()
    def json_record = [
      name             : "TestOrgPost",
      ids              : [
        [namespace: "global", value: "test-org-id-val"]
      ],
      providedPlatforms: ["TestOrgPlt"],
      offices: [
          [name: "TestOffice1",
           language:"ger",
           function: "Technical Support"],
          [name: "TestOffice2",
           language:"epo",
           function: "other"],
          [name: "TestOffice3",
           language:"hun"]
      ],
    ]

    when:

    HttpRequest request = HttpRequest.POST("${urlPath}/rest/orgs", json_record as JSON)
      .bearerAuth(accessToken)
    HttpResponse resp = http.toBlocking().exchange(request)

    then:

    resp.status == HttpStatus.CREATED

    expect:
    resp.body()?.name == "TestOrgPost"
    resp.body()?._embedded?.ids?.size() == 1
    resp.body()?._embedded?.offices?.size()==3
    resp.body()?._embedded?.offices*.function.name.count("Technical Support")==2
    resp.body()?._embedded?.offices*.function.name.count("Other")
    resp.body()?._embedded?.offices*.language.name.containsAll(["hun", "ger", "epo"])
  }

  void "test org index"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()

    when:

    HttpRequest request = HttpRequest.GET("${urlPath}/rest/orgs")
    HttpResponse resp = http.toBlocking().exchange(request)

    then:

    resp.status == HttpStatus.OK

    expect:

    resp.body()?.data?.size() > 0
  }

  void "test org update"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()
    def updated_plt = Platform.findByName("TestOrgPltUpdate")
    def id = Org.findByName("TestOrgPatch")?.id

    def update_record = [
      name             : "TestOrgUpdateNew",
      ids              : [
        [namespace: "global", value: "test-org-id-val-new"]
      ],
      providedPlatforms: [updated_plt.id],
      offices: [[name: "2ndTestOffice1",
                 language:"ger",
                 function:"Technical Support"],
                [name: "2ndTestOffice2",
                 language:RefdataCategory.lookup(KBComponent.RD_LANGUAGE, "eng").id,
                 function: "other"]
      ]
    ]

    when:

    HttpRequest request = HttpRequest.PUT("${urlPath}/rest/orgs/$id", update_record as JSON)
      .queryParam('_embed', 'providedPlatforms,ids,offices')
      .bearerAuth(accessToken)
    then:

    resp.status == HttpStatus.OK

    expect:

    resp.body().name == "TestOrgUpdateNew"
    resp.body()._embedded?.ids?.size() == 1
    resp.body()._embedded?.providedPlatforms?.size() == 1
    resp.body()._embedded?.offices.size() == 2
    resp.body()._embedded?.offices*.function.name.contains("Other")
  }

  void "test source delete"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()
    def id = Org.findByName("TestOrgPatch")?.id

    def update_record = [
      name             : "TestOrgUpdateSource",
      ids              : [
        [namespace: "global", value: "test-org-id-val-new"]
      ],
      source           : null
    ]

    when:

    HttpRequest request = HttpRequest.PUT("${urlPath}/rest/orgs/$id", update_record as JSON)
      .bearerAuth(accessToken)

    then:

    resp.status == HttpStatus.OK

    expect:

    resp.body().name == "TestOrgUpdateSource"
    resp.body().source == null
    resp.body()._embedded?.ids?.size() == 1
//    resp.body()._embedded?.providedPlatforms?.size() == 1
  }
}
