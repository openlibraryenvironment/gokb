package gokbg3.rest

import grails.converters.JSON
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
import org.gokb.cred.Org
import org.gokb.cred.RefdataCategory
import org.gokb.TitleLookupService

@Integration
@Rollback
class TitleTestSpec extends AbstractAuthSpec {

  @Autowired
  TitleLookupService titleLookupService

  private RestBuilder rest = new RestBuilder()

  def setupSpec(){
  }

  def setup() {
    def ns_eissn = IdentifierNamespace.findByValue('eissn')
    def new_id = Identifier.findByValue('2345-2334') ?: new Identifier(value: '2345-2334', namespace: ns_eissn).save(flush:true)
    def new_org = Org.findByName('TestOrg') ?: new Org(name: 'TestOrg').save(flush:true)
    def test_ti = JournalInstance.findByName("TestJournal") ?: new JournalInstance(name: "TestJournal").save(flush:true)
  }

  void "test /rest/titles without token"() {
    def urlPath = getUrlPath()
    when:
    RestResponse resp = rest.get("${urlPath}/rest/titles") {
      accept('application/json')
    }
    then:
    resp.status == 401 // Unauthorized
  }

  void "test /rest/titles/<id> with valid token"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("${urlPath}/rest/titles") {
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
  }

  void "test insert new title"() {
    def urlPath = getUrlPath()
    def issn_ns = IdentifierNamespace.findByValue('issn')
    def test_id = Identifier.findByValue('2345-2334')
    def publisher = Org.findByName("TestOrg")

    when:
    def json_record = [
      name: "TestFullJournal",
      ids: [
        test_id.id,
        [namespace: issn_ns.id, value: "3344-5544"],
        [namespace: "zdb", value: "1234435-6"]
      ],
      publisher: publisher.id
    ]

    String accessToken = getAccessToken()
    RestResponse resp = rest.post("${urlPath}/rest/titles?type=journal") {
      accept('application/json')
      auth("Bearer $accessToken")
      body(json_record as JSON)
    }
    then:
    resp.status == 201 // Created
    expect:
    resp.json._embedded?.ids?.size() == 3
    resp.json._embedded?.publisher?.size() == 1
  }

  void "test journal index"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("${urlPath}/rest/titles?type=journal&ids=1234435-6") {
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    expect:
    resp.json.data?.size() == 1
  }
}
