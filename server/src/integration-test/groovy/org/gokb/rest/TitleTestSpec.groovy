package org.gokb.rest

import grails.gorm.transactions.*
import grails.testing.mixin.integration.Integration

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.uri.UriBuilder

import org.gokb.cred.BookInstance
import org.gokb.cred.Combo
import org.gokb.cred.ComponentSubject
import org.gokb.cred.DatabaseInstance
import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.JournalInstance
import org.gokb.cred.Org
import org.gokb.cred.RefdataCategory
import org.gokb.cred.RefdataValue
import org.gokb.cred.Subject
import org.gokb.TitleLookupService
import org.springframework.web.client.RestTemplate

import spock.lang.Specification
import spock.lang.Shared

@Integration
@Rollback
class TitleTestSpec extends AbstractAuthSpec {

  @Autowired
  TitleLookupService titleLookupService


  BlockingHttpClient client

  def last = false

  def setupSpec(){
  }

  def setup() {
    if (!client) {
      client = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }

    def ns_eissn = IdentifierNamespace.findByValue('eissn')
    def new_id = Identifier.findByValue('2345-2331') ?: new Identifier(value: '2345-2331', namespace: ns_eissn).save(flush:true)
    def new_org = Org.findByName('TestOrg') ?: new Org(name: 'TestOrg').save(flush:true)
    def old_id = Identifier.findByValue('2345-2323') ?: new Identifier(value: '2345-2323', namespace: ns_eissn).save(flush:true)

    if (!JournalInstance.findByName("TestJournal")) {
      def test_ti = new JournalInstance(name: "TestJournal").save(flush:true)
      def id_combo = new Combo(fromComponent: test_ti, toComponent: old_id, type: RefdataCategory.lookup('Combo.Type','KBComponent.Ids')).save(flush:true)
      RefdataValue ddc_schema = RefdataCategory.lookup('Subject.Scheme', 'DDC')
      def ddc_test = Subject.findBySchemeAndHeading(ddc_schema, '001')

      if (!ddc_test) {
        ddc_test = new Subject(scheme: ddc_schema, heading: '001').save(flush:true)
      }

      if (!ComponentSubject.findByComponentAndSubject(test_ti, ddc_test)) {
        new ComponentSubject(component: test_ti, subject: ddc_test).save(flush: true)
      }
    }

    def test_prev = JournalInstance.findByName("TestPrevJournal") ?: new JournalInstance(name: "TestPrevJournal").save(flush:true)
    def test_next = JournalInstance.findByName("TestNextJournal") ?: new JournalInstance(name: "TestNextJournal").save(flush:true)
    def test_upd_history = JournalInstance.findByName("TestUpdateJournalHistory") ?: new JournalInstance(name: "TestUpdateJournalHistory").save(flush:true)
  }

  def cleanup() {
    if (last) {
      sleep(300)
      JournalInstance.findByName("TestPrevJournal")?.refresh()?.expunge()
      JournalInstance.findByName("TestNextJournal")?.refresh()?.expunge()
      JournalInstance.findByName("TestUpdateJournalHistory")?.refresh()?.expunge()
      JournalInstance.findByName("TestJournal")?.refresh()?.expunge()
      JournalInstance.findByName("TestFullJournal")?.refresh()?.expunge()
    }
  }

  void "test /rest/titles without token"() {
    def urlPath = getUrlPath()
    when:
    HttpRequest request = HttpRequest.GET("${urlPath}/rest/titles")
    HttpResponse resp = client.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
  }

  void "test journal index"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    URI uri = UriBuilder.of(urlPath)
      .path("/rest/titles")
      .queryParam('type', 'journal')
      .queryParam('ids', '2345-2323')
      .build()

    HttpRequest request = HttpRequest.GET(uri)
      .bearerAuth(accessToken)
    HttpResponse resp = client.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    expect:
    resp.body().data?.size() == 1
  }

  void "test /rest/titles/<id> with valid token"() {
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.GET("${urlPath}/rest/titles")
      .bearerAuth(accessToken)
    HttpResponse resp = client.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
  }

  void "test insert new title"() {
    def urlPath = getUrlPath()
    def issn_ns = IdentifierNamespace.findByValue('issn')
    def test_id = Identifier.findByValue('2345-2331')
    def publisher = Org.findByName("TestOrg")

    when:
    def json_record = [
      name: "TestFullJournal",
      ids: [
        test_id.id,
        [namespace: issn_ns.id, value: "3344-5540"],
        [namespace: "zdb", value: "1483109-0"]
      ],
      subjects: [
        [scheme: 'DDC', heading: '001'],
        [scheme: 'DDC', heading: '101']
      ],
      publisher: publisher.id
    ]

    String accessToken = getAccessToken()
    URI uri = UriBuilder.of(urlPath)
      .path("/rest/titles")
      .queryParam('type', 'journal')
      .build()

    HttpRequest request = HttpRequest.POST(uri, json_record)
      .bearerAuth(accessToken)
    HttpResponse resp = client.exchange(request, Map)

    then:
    resp.status == HttpStatus.CREATED
    expect:
    resp.body()._embedded?.ids?.size() == 3
    resp.body()._embedded?.publisher?.size() == 1
    resp.body()._embedded?.subjects?.size() == 2
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
    HttpRequest request = HttpRequest.POST("${urlPath}/rest/titles/$id/history", json_record)
      .bearerAuth(accessToken)
    HttpResponse resp = client.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    expect:
    resp.body().size() == 1
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
    HttpRequest request = HttpRequest.PUT("${urlPath}/rest/titles/$id/history", json_record)
      .bearerAuth(accessToken)
    HttpResponse resp = client.exchange(request, Map)

    then:
    // resp.status == 200 // OK
    resp.body()?.data?.size() == 2
  }

  void "test remove title history event by update"() {
    def urlPath = getUrlPath()
    last = true
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
    HttpRequest request = HttpRequest.PUT("${urlPath}/rest/titles/$id/history", json_record)
      .bearerAuth(accessToken)
    HttpResponse resp = client.exchange(request, Map)

    then:
    // resp.status == 200 // OK
    resp.body()?.data?.size() == 1
  }
  void "test send stale update info"() {
    def urlPath = getUrlPath()
    last = true
    def id = JournalInstance.findByName("TestJournal").id

    when:
    def json_record = [
      name: "TestJournalV1"
    ]

    String accessToken = getAccessToken()
    HttpRequest req1 = HttpRequest.PUT("${urlPath}/rest/titles/$id", json_record)
      .bearerAuth(accessToken)
    HttpResponse resp1 = client.exchange(req1, Map)

    def json_stale_record = [
      name: "TestJournalV2",
      version: "0"
    ]
    HttpRequest req2 = HttpRequest.PUT("${urlPath}/rest/titles/$id", json_stale_record)
      .bearerAuth(accessToken)
    HttpStatus status2

    try {
      HttpRequest resp2 = client.exchange(req2)
    }
    catch (io.micronaut.http.client.exceptions.HttpClientResponseException e) {
      status2 = e.status
    }

    then:
    resp1.status == HttpStatus.OK
    status2 == HttpStatus.CONFLICT
  }
}
