package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace

@Integration
@Rollback
class IdentifierTestSpec extends AbstractAuthSpec {

  private RestBuilder rest = new RestBuilder()

  def setupSpec() {
  }

  def setup() {
    def ns_eissn = IdentifierNamespace.findByValue('eissn')
    def test_id = Identifier.findByValue("1234-4567") ?: new Identifier(value: "1234-4567", namespace: ns_eissn).save(flush: true)
  }

  void "test /rest/identifiers/<id> without token"() {
    def test_id = Identifier.findByValue("1234-4567")
    def urlPath = getUrlPath()

    when:
    RestResponse resp = rest.get("${urlPath}/rest/identifiers/${test_id.id}") {
      // headers
      accept('application/json')
    }
    then:
    resp.status == 401 // Unauthorized
  }

  void "test /rest/identifiers/<id> with valid token"() {
    def test_id = Identifier.findByValue("1234-4567")
    def urlPath = getUrlPath()
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
  }
}
