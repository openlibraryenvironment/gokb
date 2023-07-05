package org.gokb.rest

import grails.testing.mixin.integration.Integration
import grails.converters.JSON
import grails.gorm.transactions.*

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.BlockingHttpClient

import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.JournalInstance
import org.gokb.cred.RefdataCategory

import spock.lang.Specification
import spock.lang.Shared

@Integration
@Rollback
class IdentifierTestSpec extends AbstractAuthSpec {


  BlockingHttpClient http

  IdentifierNamespace ns_eissn
  IdentifierNamespace ns_isbn
  Identifier test_id
  JournalInstance test_journal

  def setupSpec() {
  }

  def setup() {
    if (!http) {
      http = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }

    ns_eissn = IdentifierNamespace.findByValue('eissn')
    ns_isbn = IdentifierNamespace.findByValue('isbn')
    test_id = Identifier.findByValue("1234-4567") ?: new Identifier(value: "1234-4567", namespace: ns_eissn).save(flush:true)
    test_journal = JournalInstance.findByName("IdTestJournal") ?: new JournalInstance(name: "IdTestJournal").save(flush:true)
  }

  def cleanup() {
    sleep(500)
    Identifier.findByValue("6644-2231")?.expunge()
    Identifier.findByValue("6644-223")?.expunge()
    Identifier.findByValue("6644-2284")?.expunge()
    Identifier.findByValue("0001-5547")?.expunge()
    Identifier.findByValue("1938-2650")?.expunge()
    Identifier.findByValue("9780781783385")?.expunge()
    test_id?.expunge()
    test_journal?.refresh().expunge()
  }

  void "test /rest/identifiers/<id> without token"() {
    given:
    def urlPath = getUrlPath()
    def test_id = Identifier.findByValue("1234-4567")

    when:
    HttpRequest request = HttpRequest.GET("${urlPath}/rest/identifiers/${test_id.id}")
    HttpResponse resp = http.exchange(request)

    then:
    resp.status == HttpStatus.OK
  }

  void "test /rest/identifiers/<id> with valid token"() {
    given:
    def urlPath = getUrlPath()
    def test_id = Identifier.findByValue("1234-4567")
    // use the bearerToken to read /rest/profile
    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.GET("${urlPath}/rest/identifiers/${test_id.id}")
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().value == "1234-4567"
  }

  void "test /rest/identifier-namespaces"() {
    def urlPath = getUrlPath()
    // use the bearerToken to read /rest/profile
    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.GET("${urlPath}/rest/identifier-namespaces")
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().data != null
    resp.body()._links.size() == 1
    resp.body().data.size() >= 8
    resp.body().data[1].name != null
  }

  void "test /rest/identifier-namespaces?targetType=Book"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    HttpRequest request1 = HttpRequest.GET("${urlPath}/rest/identifier-namespaces?targetType=Book")
    HttpResponse resp1 = http.exchange(request1, Map)
    HttpRequest request2 = HttpRequest.GET("${urlPath}/rest/identifier-namespaces?targetType=Title")
    HttpResponse resp2 = http.exchange(request2, Map)
    then:
    resp1.status == HttpStatus.OK
    resp1.body().data != null
    resp1.body()._links.size() == 1
    resp1.body().data.size() == 3
    resp2.status == HttpStatus.OK
    resp2.body().data != null
    resp2.body()._links.size() == 1
    resp2.body().data.size() == 7
  }

  void "test identifier create"() {
    given:
    def urlPath = getUrlPath()
    def obj_map = [
      value    : "6644-2230",
      namespace: ns_eissn.id
    ]
    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.POST("${urlPath}/rest/identifiers", obj_map)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)
    then:
    resp.status == HttpStatus.CREATED
    resp.body().value == "6644-2230"
  }

  void "test identifier namespace validation"() {
    given:
    def urlPath = getUrlPath()
    def obj_map = [
      value    : "6644-2231",
      namespace: ns_eissn.id
    ]
    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.POST("${urlPath}/rest/identifiers", obj_map)
      .bearerAuth(accessToken)
    HttpStatus status

    try {
      HttpRequest resp2 = http.exchange(request, Map)
    }
    catch (io.micronaut.http.client.exceptions.HttpClientResponseException e) {
      status = e.status
    }
    then:
    status == HttpStatus.BAD_REQUEST
  }

  void "test identifier create with connected component"() {
    given:
    def urlPath = getUrlPath()
    test_journal = test_journal ?: new JournalInstance(name: "IdTestJournal")
    def obj_map = [
      value    : "6644-2281",
      namespace: ns_eissn.id,
      component: test_journal.id
    ]
    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.POST("${urlPath}/rest/identifiers?_embed=identifiedComponents", obj_map)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)
    then:
    resp.status == HttpStatus.CREATED
    resp.body().value == "6644-2281"
    resp.body()._embedded?.identifiedComponents.size() == 1
    resp.body()._embedded?.identifiedComponents[0].id == test_journal.id
  }

  void "test identifier create reformatted ISBN"() {
    given:
    def urlPath = getUrlPath()
    def obj_map = [
      value    : "0-7817-8338-0",
      namespace: ns_isbn.id
    ]
    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.POST("${urlPath}/rest/identifiers", obj_map)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)
    then:
    resp.status == HttpStatus.CREATED
    resp.body().value == "9780781783385"
  }
}
