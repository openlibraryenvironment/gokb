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

import org.gokb.cred.*
import org.gokb.TitleLookupService
import gokbg3.RestMappingService
import org.springframework.web.client.RestTemplate

import spock.lang.Specification
import spock.lang.Shared

@Integration
@Rollback
class TitleTestSpec extends AbstractAuthSpec {

  @Autowired
  TitleLookupService titleLookupService

  @Autowired
  RestMappingService restMappingService

  BlockingHttpClient client

  boolean last = false

  def setupSpec(){
  }

  def setup() {
    if (!client) {
      client = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }

    IdentifierNamespace ns_issn = IdentifierNamespace.findByValue('issn')
    IdentifierNamespace ns_eissn = IdentifierNamespace.findByValue('eissn')
    Identifier new_id = Identifier.findByValue('2345-2331') ?: new Identifier(value: '2345-2331', namespace: ns_eissn).save(flush:true)
    Org new_org = Org.findByName('TestTitleOrg') ?: new Org(name: 'TestTitleOrg').save(flush:true)
    Org new_update_org = Org.findByName('TestTitleOrgUpdate') ?: new Org(name: 'TestTitleOrgUpdate').save(flush:true)
    Identifier old_id = Identifier.findByValue('2345-2323') ?: new Identifier(value: '2345-2323', namespace: ns_eissn).save(flush:true)

    if (!JournalInstance.findByName("TitleTestJournal")) {
      JournalInstance test_ti = new JournalInstance(name: "TitleTestJournal").save(flush:true)
      test_ti.addIdentifier(old_id)
      RefdataValue ddc_schema = RefdataCategory.lookup('Subject.Scheme', 'DDC')
      Subject ddc_test = Subject.findBySchemeAndHeading(ddc_schema, '001')

      test_ti.addPublisher(new_org)
      test_ti.save()

      if (!ddc_test) {
        ddc_test = new Subject(scheme: ddc_schema, heading: '001').save(flush:true)
      }

      if (!ComponentSubject.findByComponentAndSubject(test_ti, ddc_test)) {
        new ComponentSubject(component: test_ti, subject: ddc_test).save(flush: true)
      }
    }

    JournalInstance test_ti = JournalInstance.findByName("TestTitleMergeObject")

    if (!test_ti) {
      test_ti = new JournalInstance(name: "TestTitleMergeObject").save(flush:true)
      Identifier merge_id = Identifier.findByValue('5252-2342') ?: new Identifier(value: '5252-2342', namespace: ns_issn).save(flush:true)
      test_ti.addIdentifiers([merge_id, old_id])
      test_ti.save(flush: true)
    }

    if (!TitleInstancePackagePlatform.findByName("TestTitleMergeTipp")) {
      Platform tipp_plt = Platform.findByName("TestTitleMergePlatform") ?: new Platform(name: "TestTitleMergePlatform", primaryUrl: "http://testmergetitle.org").save(flush: true)
      Package tipp_pkg = Package.findByName("TestTitleMergePackage") ?: new Package(name: "TestTitleMergePackage", nominalPlatform: tipp_plt, provider: new_org).save(flush: true)

      new TitleInstancePackagePlatform(name: "TestTitleMergeTipp", url: "http://testmergetitle.org/tipp1", pkg: tipp_pkg, hostPlatform: tipp_plt, title: test_ti).save(flush: true)
    }

    JournalInstance test_prev = JournalInstance.findByName("TestPrevJournal") ?: new JournalInstance(name: "TestPrevJournal").save(flush:true)
    JournalInstance test_next = JournalInstance.findByName("TestNextJournal") ?: new JournalInstance(name: "TestNextJournal").save(flush:true)
    JournalInstance test_upd_history = JournalInstance.findByName("TestUpdateJournalHistory") ?: new JournalInstance(name: "TestUpdateJournalHistory").save(flush:true)
  }

  def cleanup() {
    if (last) {
      sleep(300)

      ["TestPrevJournal", "TestNextJournal", "TestUpdateJournalHistory", "TitleTestJournal", "TestFullJournal", "TestTitleMergeTarget"].each {
        JournalInstance.findByName(it)?.refresh()?.expunge()
      }

      TitleInstancePackagePlatform.findByName("TestTitleMergeTipp")?.refresh()?.expunge()
      Package.findByName("TestTitleMergePackage")?.refresh()?.expunge()
      Platform.findByName("TestTitleMergePlatform")?.refresh()?.expunge()
      Org.findByName("TestTitleOrg")?.refresh()?.expunge()
      Org.findByName('TestTitleOrgUpdate')?.refresh()?.expunge()
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
    resp.body().data?.size() == 2
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
    def publisher = Org.findByName("TestTitleOrg")

    when:
    Map json_record = [
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
      publisher: [
        publisher.id
      ]
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
    def body = resp.body()
    body._embedded?.ids?.size() == 3
    body._embedded?.publisher?.size() == 1
    body._embedded?.subjects?.size() == 2
    sleep(500)
    TitleInstance new_ti = TitleInstance.findById(body.id)

    new_ti.publisher?.size() == 1
  }

  void "test add title history event"() {
    String urlPath = getUrlPath()
    JournalInstance obj = JournalInstance.findByName("TitleTestJournal")

    when:
    Map json_record = [
      date: "2010-01-01",
      from: [JournalInstance.findByName("TestPrevJournal").id]
    ]

    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.POST("${urlPath}/rest/titles/${obj.id}/history", json_record)
      .bearerAuth(accessToken)
    HttpResponse resp = client.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    expect:
    resp.body().size() == 1
  }

  void "test update title history events"() {
    String urlPath = getUrlPath()
    JournalInstance obj = JournalInstance.findByName("TestUpdateJournalHistory")

    when:
    Map json_record = [
      [
        date: "1990-01-01",
        from: [JournalInstance.findByName("TestPrevJournal").id]
      ],
      [
        date: "2010-01-01",
        to: [JournalInstance.findByName("TestNextJournal").id]
      ]
    ]

    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.PUT("${urlPath}/rest/titles/${obj.id}/history", json_record)
      .bearerAuth(accessToken)
    HttpResponse resp = client.exchange(request, Map)

    then:
    // resp.status == 200 // OK
    resp.body()?.data?.size() == 2
  }

  void "test remove title history event by update"() {
    String urlPath = getUrlPath()
    last = true
    JournalInstance obj = JournalInstance.findByName("TestUpdateJournalHistory")

    when:
    Map json_record = [
      [
        date: "1990-01-01",
        from: [JournalInstance.findByName("TestPrevJournal").id]
      ]
    ]

    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.PUT("${urlPath}/rest/titles/${obj.id}/history", json_record)
      .bearerAuth(accessToken)
    HttpResponse resp = client.exchange(request, Map)

    then:
    // resp.status == 200 // OK
    resp.body()?.data?.size() == 1
  }
  void "test send stale update info"() {
    String urlPath = getUrlPath()
    JournalInstance obj = JournalInstance.findByName("TitleTestJournal")

    when:
    Map json_record = [
      name: "TitleTestJournalV1"
    ]

    String accessToken = getAccessToken()
    HttpRequest req1 = HttpRequest.PUT("${urlPath}/rest/titles/${obj.id}", json_record)
      .bearerAuth(accessToken)
    HttpResponse resp1 = client.exchange(req1, Map)

    def json_stale_record = [
      name: "TitleTestJournalV2",
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

  void "test merge titles"() {
    String urlPath = getUrlPath()
    JournalInstance tid = JournalInstance.findByName("TitleTestJournal")
    JournalInstance mergeid = JournalInstance.findByName("TestTitleMergeObject")

    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.PUT("${urlPath}/rest/titles/${mergeid.id}/merge?target=${tid.id}&mergeTipps=true&mergeIds=true", null)
      .bearerAuth(accessToken)
    HttpResponse resp = client.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    sleep(300)
    JournalInstance target = JournalInstance.findById(tid)
    JournalInstance merged = JournalInstance.findById(mergeid)
    TitleInstancePackagePlatform test_tipp = TitleInstancePackagePlatform.findByName("TestTitleMergeTipp")
    test_tipp.title == target
    merged.refresh().status.value == 'Deleted'
    merged.tipps.size() == 0
    target.refresh().status.value == 'Current'
    target.tipps.size() == 1
    target.ids.size() == 2
  }

 void "test update publisher"() {
    String urlPath = getUrlPath()
    JournalInstance ti = JournalInstance.findByName("TitleTestJournal")
    Org new_pub = Org.findByName('TestTitleOrgUpdate')

    when:
    Map json_record = [
      publisher: [
        new_pub.id
      ]
    ]

    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.PUT("${urlPath}/rest/titles/${ti.id}", json_record)
      .bearerAuth(accessToken)
    HttpResponse resp = client.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    sleep(300)
    ti.refresh()
    ti.publisher.size() == 1
    ti.publisher[0].id == new_pub.id
    resp.body()?._embedded?.publisher?.size() == 1
    resp.body()._embedded.publisher[0].id == new_pub.id
  }
}
