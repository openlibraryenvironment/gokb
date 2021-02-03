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
    def old_id = Identifier.findByValue('2345-2323') ?: new Identifier(value: '2345-2323', namespace: ns_eissn).save(flush:true)
    def combo = new Combo(fromComponent: test_ti, toComponent: old_id, type: RefdataCategory.lookup('Combo.Type','KBComponent.Ids')).save(flush:true)
    def test_prev = JournalInstance.findByName("TestPrevJournal") ?: new JournalInstance(name: "TestPrevJournal").save(flush:true)
    def test_next = JournalInstance.findByName("TestNextJournal") ?: new JournalInstance(name: "TestNextJournal").save(flush:true)
    def test_upd_history = JournalInstance.findByName("TestUpdateJournalHistory") ?: new JournalInstance(name: "TestUpdateJournalHistory").save(flush:true)
  }

  def cleanup() {
    JournalInstance.findByName("TestPrevJournal")?.expunge()
    JournalInstance.findByName("TestNextJournal")?.expunge()
    JournalInstance.findByName("TestUpdateJournalHistory")?.expunge()
    JournalInstance.findByName("TestJournal")?.expunge()
  }

  void "test /rest/titles without token"() {
    def urlPath = getUrlPath()
    when:
    RestResponse resp = rest.get("${urlPath}/rest/titles") {
      accept('application/json')
    }
    then:
    resp.status == 200 // OK
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
    RestResponse resp = rest.get("${urlPath}/rest/titles?type=journal&ids=2345-2323") {
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    expect:
    resp.json.data?.size() == 1
  }

  void "test add title history event"() {
    def urlPath = getUrlPath()
    def id = JournalInstance.findByName("TestJournal").id
    def prev_id = JournalInstance.findByName("TestPrevJournal").id

    when:
    def json_record = [
      date: "2010-01-01",
      from: [prev_id]
    ]

    String accessToken = getAccessToken()
    RestResponse resp = rest.post("${urlPath}/rest/titles/$id/history") {
      accept('application/json')
      auth("Bearer $accessToken")
      body(json_record as JSON)
    }
    then:
    resp.status == 200 // OK
    expect:
    resp.json.size() == 1
  }

  void "test update title history events"() {
    def urlPath = getUrlPath()
    def id = JournalInstance.findByName("TestUpdateJournalHistory").id
    def prev_id = JournalInstance.findByName("TestPrevJournal").id
    def next_id = JournalInstance.findByName("TestNextJournal").id

    when:
    def json_record = [
      [
        date: "1990-01-01",
        from: [prev_id]
      ],
      [
        date: "2010-01-01",
        to: [next_id]
      ]
    ]

    String accessToken = getAccessToken()
    RestResponse resp = rest.put("${urlPath}/rest/titles/$id/history") {
      accept('application/json')
      auth("Bearer $accessToken")
      body(json_record as JSON)
    }
    then:
    // resp.status == 200 // OK
    resp.json?.data?.size() == 2
  }

  void "test remove title history event by update"() {
    def urlPath = getUrlPath()
    def id = JournalInstance.findByName("TestUpdateJournalHistory").id
    def prev_id = JournalInstance.findByName("TestPrevJournal").id
    def next_id = JournalInstance.findByName("TestNextJournal").id

    when:
    def json_record = [
      [
        date: "1990-01-01",
        from: [prev_id]
      ]
    ]

    String accessToken = getAccessToken()
    RestResponse resp = rest.put("${urlPath}/rest/titles/$id/history") {
      accept('application/json')
      auth("Bearer $accessToken")
      body(json_record as JSON)
    }
    then:
    // resp.status == 200 // OK
    resp.json?.data?.size() == 1
  }
}
