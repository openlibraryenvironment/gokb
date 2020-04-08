package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.WebApplicationContext

@Integration
@Rollback
class IdentifierTestSpec extends AbstractAuthSpec {

  @Autowired
  WebApplicationContext ctx

  private RestBuilder rest = new RestBuilder()

  def setupSpec(){
  }

  def setup() {
    def ns_eissn = IdentifierNamespace.findByValue('eissn')
    def test_id = Identifier.findByValue("1234-4567") ?: new Identifier(value: "1234-4567", namespace: ns_eissn).save(flush:true)
  }

  void "test /rest/identifiers/<id> without token"() {
    def test_id = Identifier.findByValue("1234-4567")

    when:
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/identifiers/${test_id.id}") {
      // headers
      accept('application/json')
    }
    then:
    resp.status == 401 // Unauthorized
  }

  void "test /rest/identifiers/<id> with valid token"() {
    def test_id = Identifier.findByValue("1234-4567")
    // use the bearerToken to read /rest/profile
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/identifiers/${test_id.id}") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    resp.json.value == "1234-4567"
  }
}
