package org.gokb.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.JournalInstance
import grails.converters.JSON
import org.gokb.cred.RefdataCategory

@Integration
@Rollback
class IdentifierTestSpec extends AbstractAuthSpec {

  private RestBuilder rest = new RestBuilder()
  IdentifierNamespace ns_eissn
  Identifier test_id
  JournalInstance test_journal

  def setupSpec() {
  }

  def setup() {
    ns_eissn = IdentifierNamespace.findByValue('eissn')
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
    test_id?.expunge()
    test_journal?.refresh().expunge()
  }

  void "test /rest/identifiers/<id> without token"() {
    given:
    def urlPath = getUrlPath()
    def test_id = Identifier.findByValue("1234-4567")

    when:
    RestResponse resp = rest.get("${urlPath}/rest/identifiers/${test_id.id}") {
      // headers
      accept('application/json')
    }
    then:
    resp.status == 200 // OK
  }

  void "test /rest/identifiers/<id> with valid token"() {
    given:
    def urlPath = getUrlPath()
    def test_id = Identifier.findByValue("1234-4567")
    // use the bearerToken to read /rest/profile
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("${urlPath}/rest/identifiers/${test_id.id}") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    resp.json.value == "1234-4567"
  }

  void "test /rest/identifier-namespaces"() {
    def urlPath = getUrlPath()
    // use the bearerToken to read /rest/profile
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("${urlPath}/rest/identifier-namespaces") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    resp.json.data != null
    resp.json._links.size() == 1
    resp.json.data.size() >= 8
    resp.json.data[1].name != null
  }

  void "test /rest/identifier-namespaces?targetType=Book"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("${urlPath}/rest/identifier-namespaces?targetType=Book") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }

    then:
    resp.status == 200 // OK
    resp.json.data != null
    resp.json._links.size() == 1
    resp.json.data.size() == 3
  }

  void "test /rest/identifier-namespaces?targetType=Title"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("${urlPath}/rest/identifier-namespaces?targetType=Title") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    resp.json.data != null
    resp.json._links.size() == 1
    resp.json.data.size() == 7
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
    RestResponse resp = rest.post("${urlPath}/rest/identifiers") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
      body(obj_map as JSON)
    }
    then:
    resp.status == 201 // Created
    resp.json.value == "6644-2230"
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
    RestResponse resp = rest.post("${urlPath}/rest/identifiers") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
      body(obj_map as JSON)
    }
    then:
    resp.status == 400 // ERROR
    resp.json.result == 'ERROR'
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
    RestResponse resp = rest.post("${urlPath}/rest/identifiers?_embed=identifiedComponents") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
      body(obj_map as JSON)
    }
    then:
    resp.status == 201 // OK
    resp.json.value == "6644-2281"
    resp.json._embedded?.identifiedComponents.size() == 1
    resp.json._embedded?.identifiedComponents[0].id == test_journal.id
  }
}
