package gokbg3.rest

import grails.core.GrailsApplication
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.BookInstance
import org.gokb.cred.Combo
import org.gokb.cred.DatabaseInstance
import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.JournalInstance
import org.gokb.cred.RefdataCategory
import org.gokb.TitleLookupService
import org.springframework.web.context.WebApplicationContext

@Integration
@Rollback
class TitleTestSpec extends AbstractAuthSpec {

  GrailsApplication grailsApplication

  @Autowired
  WebApplicationContext ctx

  @Autowired
  TitleLookupService titleLookupService

  private RestBuilder rest = new RestBuilder()

  def setupSpec(){
  }

  def setup() {
    def ns_eissn = IdentifierNamespace.findByValue('eissn')
    def new_id = Identifier.findByValue('2345-2334') ?: new Identifier(value: '2345-2334', namespace: ns_eissn).save(flush:true)
    def test_ti = JournalInstance.findByName("TestJournal") ?: new JournalInstance(name: "TestJournal").save(flush:true)

    def id_combos = Combo.executeQuery("from Combo as c where c.fromComponent = :ti and c.toComponent = :id", [ti: test_ti, id: new_id])
    def combo = null

    if ( id_combos.size() == 0 ) {
      def id_type = RefdataCategory.lookup('Combo.Type', 'KBComponent.Ids')
      combo = new Combo(fromComponent: test_ti, toComponent: new_id, type: id_type).save(flush:true)
    }
  }

  void "test /rest/titles without token"() {
    when:
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/titles") {
      accept('application/json')
    }
    then:
    resp.status == 401 // Unauthorized
  }

  void "test /rest/titles/<id> with valid token"() {

    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/titles") {
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
  }

  void "test journal index"() {
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/titles?type=journal&ids=1491237-5") {
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    expect:
    resp.json.data?.size() == 1
  }

  void "retrieve title with embedded history"() {
    def title_id = titleLookupService.matchClassOneComponentIds([[ns: 'zdb', value:'1491237-5']])[0]
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/titles/$title_id?_embed=history") {
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    expect:
    resp.json._embedded?.history?.size() == 1
  }
}
