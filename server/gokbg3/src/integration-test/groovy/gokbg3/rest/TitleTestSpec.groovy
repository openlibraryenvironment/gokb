package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.JournalInstance
import org.gokb.cred.BookInstance
import org.gokb.cred.DatabaseInstance
import org.springframework.web.context.WebApplicationContext

@Integration
@Rollback
class TitleTestSpec extends AbstractAuthSpec {

  @Autowired
  WebApplicationContext ctx

  private RestBuilder rest = new RestBuilder()

  def setupSpec(){
  }

  def setup() {
    def ns_eissn = IdentifierNamespace.findByValue('eissn')
    def test_ti = JournalInstance.findByName("TestJournal") ?: new JournalInstance(name: "TestJournal").save(flush:true)
  }

  void "test /rest/titles/<id> without token"() {
    def test_ti = JournalInstance.findByName("TestJournal")

    when:
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/titles/${test_ti.id}") {
      // headers
      accept('application/json')
    }
    then:
    resp.status == 401 // Unauthorized
  }

  void "test /rest/titles/<id> with valid token"() {
    def test_ti = JournalInstance.findByName("TestJournal")
    // use the bearerToken to read /rest/profile
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/titles/${test_ti.id}") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    resp.json.name == "TestJournal"
  }
}
