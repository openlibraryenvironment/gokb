package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.JournalInstance
import grails.converters.JSON

@Integration
@Rollback
class IdentifierTestSpec extends AbstractAuthSpec {

  private RestBuilder rest = new RestBuilder()
  def ns_eissn
  def test_id
  def test_journal

  def setupSpec(){
  }

  def setup() {
    ns_eissn = ns_eissn ?: IdentifierNamespace.findByValue('eissn')
    test_id = test_id ?: (Identifier.findByValue("1234-4567") ?: new Identifier(value: "1234-4567", namespace: ns_eissn).save(flush:true))
    test_journal = test_journal ?: new JournalInstance(name: "IdTestJournal")
  }

  def cleanup() {
    test_id?.refresh().expunge()
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
    resp.status == 401 // Unauthorized
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

  void "test identifier create"() {
    given:
    def urlPath = getUrlPath()
    def obj_map = [
      value: "6644-2231",
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
    resp.status == 200 // OK
    resp.json.value == "6644-2231"
  }

  void "test identifier create with connected component"() {
    given:
    def urlPath = getUrlPath()
    test_journal = test_journal ?: new JournalInstance(name: "IdTestJournal")
    def obj_map = [
      value: "6644-2284",
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
    resp.status == 200 // OK
    resp.json.value == "6644-2284"
    resp.json._embedded?.identifiedComponents.size() == 1
    resp.json._embedded?.identifiedComponents[0].id == test_journal.id
  }
}
