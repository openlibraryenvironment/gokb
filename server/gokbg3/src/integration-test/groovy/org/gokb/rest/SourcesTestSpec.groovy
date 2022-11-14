package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.gorm.transactions.*
import grails.testing.mixin.integration.Integration
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.RefdataCategory
import org.gokb.cred.Source
import org.springframework.web.client.RestTemplate
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Shared

@Integration
@Rollback
class SourcesTestSpec extends AbstractAuthSpec {


  HttpClient http

  def setup() {
    def src_upd = Source.findByName("Source PreUpdate") ?: new Source(name: "Source PreUpdate")
    IdentifierNamespace titleNS = IdentifierNamespace.findByName("TestSourceTitleNS") ?: new IdentifierNamespace(
      value: "testsourcetitlenamespace",
      name: "TestSourceTitleNS",
      targetType: RefdataCategory.lookup('IdentifierNamespace.TargetType', 'Title'))
    Source quelle = Source.findByName("TestSource") ?: new Source(name: "TestSource", targetNamespace: titleNS)
  }

  @Transactional
  def cleanup() {
    Source.findByName("Quelle 1")?.expunge()
    Source.findByName("Source PreUpdate")?.expunge()
    Source.findByName("Source AfterUpdate")?.expunge()
    Source.findByName("TestSource")?.expunge()
    IdentifierNamespace.findByName("TestSourceTitleNS")?.delete(flush: true)
  }

  void "test GET /rest/sources"() {
    given:
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.GET("$urlPath/rest/sources?_sort=name&_order=asc")
      .queryParam('_sort', 'name')
      .queryParam('_order', 'asc')
      .bearerAuth(accessToken)
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.OK
    resp.body().data.size() == 8
  }

  void "test GET /rest/sources/{id}"() {
    given:
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    Source quelle = Source.findByName("TestSource")
    HttpRequest request = HttpRequest.GET("$urlPath/rest/sources/$quelle.id")
      .bearerAuth(accessToken)
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.OK
    resp.body().name == quelle.name
    resp.body().targetNamespace != null
  }

  void "test POST /rest/sources"() {
    given:
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    Map restBody = [shortcode: 'q1', name: 'Quelle 1']
    HttpRequest request = HttpRequest.POST("$urlPath/rest/sources", restBody as JSON)
      .bearerAuth(accessToken)
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.CREATED

    resp.body().name == "Quelle 1"
  }

  void "test PUT /rest/sources/{id}"() {
    given:
    def srcId = Source.findByName("Source PreUpdate")?.id
    def urlPath = getUrlPath()
    def namespace = IdentifierNamespace.findByName("TestSourceTitleNS")
    when:
    String accessToken = getAccessToken()
    Map restBody = [
        name           : 'Source AfterUpdate',
        frequency      : 'Monthly',
        url            : "http://kbart-source.com/test-pkg",
        targetNamespace: namespace.id
    ]
    HttpRequest request = HttpRequest.PUT("$urlPath/rest/sources/$srcId", restBody as JSON)
      .bearerAuth(accessToken)
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.OK
    resp.body().name == "Source AfterUpdate"
    resp.body().frequency.name == "Monthly"
    resp.body().url == "http://kbart-source.com/test-pkg"
    resp.body().targetNamespace.name == "TestSourceTitleNS"
    resp.body().automaticUpdates == false
  }
}
