package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.*
import grails.testing.mixin.integration.Integration

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.BlockingHttpClient

import org.gokb.cred.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.client.RestTemplate

import spock.lang.Specification
import spock.lang.Shared

@Integration
@Rollback
class TippTestSpec extends AbstractAuthSpec {

  @Autowired
  WebApplicationContext ctx


  BlockingHttpClient http

  def testPackage
  def testTitle
  def testPlatform
  def testOrg
  def testGroup
  def last = false

  def setupSpec() {
  }

  def setup() {
    if (!http) {
      http = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }

    RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    IdentifierNamespace ns_eissn = IdentifierNamespace.findByValue('eissn')

    testOrg = Org.findByName("TippTestOrg") ?: new Org(name: "TippTestOrg").save(flush: true)
    testPlatform = Platform.findByName("TippTestPlat") ?: new Platform(name: "TippTestPlat", provider: testOrg).save(flush: true)

    testPackage = Package.findByName("TippTestPack") ?: new Package(name: "TippTestPack", provider: testOrg, nominalPlatform: testPlatform).save(flush: true)
    testTitle = JournalInstance.findByName("TippTestJournal") ?: new JournalInstance(name: "TippTestJournal").save(flush: true)
    testGroup = CuratoryGroup.findByName("cgtipptest") ?: new CuratoryGroup(name: "cgtipptest").save(flush: true)

    if (!TitleInstancePackagePlatform.findByName("previous TIPP")) {
      TitleInstancePackagePlatform previousTipp = new TitleInstancePackagePlatform(name: "previous TIPP", pkg: testPackage, hostPlatform: testPlatform, url: "http://some.net/").save(flush: true)
      previousTipp.addToCoverageStatements([startVolume: "1", startIssue: "1", coverageDepth: RefdataCategory.lookup("Coverage.Depth", "Selected Articles")]).save(flush: true)
    }

    if (!TitleInstancePackagePlatform.findByName("merge target TIPP")) {
      Map target_info = [
        name: "merge target TIPP",
        pkg: testPackage,
        hostPlatform: testPlatform,
        title: testTitle,
        url: "http://some.old.net/",
        status: status_deleted,
        accessEndDate: new Date()
      ]

      TitleInstancePackagePlatform merge_target = new TitleInstancePackagePlatform(target_info).save(flush: true)
      merge_target.addToCoverageStatements([startVolume: "1", startIssue: "1", coverageDepth: RefdataCategory.lookup("Coverage.Depth", "Fulltext")]).save(flush: true)
      Identifier new_id = Identifier.findByValue('2345-2323') ?: new Identifier(value: '2345-2323', namespace: ns_eissn).save(flush:true)
      merge_target.addIdentifier(new_id)
    }

    if (!TitleInstancePackagePlatform.findByName("merge victim TIPP")) {
      Map target_info = [
        name: "merge victim TIPP",
        pkg: testPackage,
        hostPlatform: testPlatform,
        title: testTitle,
        url: "http://some.new.net/"
      ]

      TitleInstancePackagePlatform merge_victim = new TitleInstancePackagePlatform(target_info).save(flush: true)
      merge_victim.addToCoverageStatements([startVolume: "3", startIssue: "10", coverageDepth: RefdataCategory.lookup("Coverage.Depth", "Fulltext")]).save(flush: true)
      Identifier old_id = Identifier.findByValue('2345-2331') ?: new Identifier(value: '2345-2331', namespace: ns_eissn).save(flush:true)
      merge_victim.addIdentifier(old_id)
    }
  }

  def cleanup() {
    TitleInstancePackagePlatform.executeQuery("select id from TitleInstancePackagePlatform").each {
      TitleInstancePackagePlatform.findById(it)?.expunge()
    }
    TitleInstancePlatform.executeQuery("select id from TitleInstancePlatform").each {
      TitleInstancePlatform.findById(it)?.expunge()
    }
    TitleInstance.executeQuery("select id from TitleInstance").each {
      TitleInstance.findById(it)?.expunge()
    }

    sleep(200)

    Package.findByName("TippTestPack")?.refresh().expunge()
    Platform.findByName("TippTestPlat")?.refresh().expunge()
    Org.findByName("TippTestOrg")?.expunge()
    CuratoryGroup.findByName("cgtipptest")?.refresh().expunge()
  }

  void "test /rest/tipps without token"() {
    given:
    String urlPath = getUrlPath()
    when:
    HttpRequest request = HttpRequest.GET("${urlPath}/rest/tipps")
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body() != null
  }

  void "test /rest/tipps with valid token"() {
    given:
    String urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.GET("${urlPath}/rest/tipps")
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body() != null
    resp.body().data.size() == 2 // merge target is Deleted
  }

  void "test /rest/tipps POST"() {
    given:
    Map upd_body = [
      pkg: testPackage.id,
      hostPlatform: testPlatform.id,
      name: "TippName",
      title: testTitle.id,
      url: "http://host-url.test/old",
      coverage: [
        [
          startDate: "2005-01-01",
          startVolume: "1",
          startIssue: "1",
          coverageDepth: "Fulltext"
        ]
      ],
      publisherName: "other Publisher"
    ]

    String urlPath = getUrlPath()

    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.POST("${urlPath}/rest/tipps", upd_body)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.CREATED
    resp.body().url == upd_body.url
    resp.body()._embedded.coverageStatements?.size() == 1
    resp.body().publisherName == "other Publisher"
  }

  void "test /rest/tipps/<id> PUT"() {
    given:
    TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.findByUrl("http://some.net/")
    Map upd_body = [
        pkg               : testPackage.id,
        hostPlatform      : testPlatform.id,
        title             : testTitle.id,
        publisherName     : "some Publisher",
        url               : "http://new-url.com",
        coverageStatements: [
            [
                id           : tipp.coverageStatements[0].id,
                startDate    : "2005-01-01",
                startVolume  : "1",
                startIssue   : "1",
                endVolume    : "5",
                endDate      : "2008-01-01",
                coverageDepth: "Fulltext"
            ],
            [
                startDate    : "2010-01-01",
                startVolume  : "7",
                startIssue   : "1",
                coverageDepth: "Fulltext"
            ]
        ],
        ids: [
          [
            type: 'issn',
            value: '3245-2349'
          ],
          [
            type: 'eissn',
            value: '3241-2541'
          ]
        ]
    ]
    String urlPath = getUrlPath()

    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.PUT("${urlPath}/rest/tipps/${tipp.id}", upd_body)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().id == tipp.id
    resp.body().url == upd_body.url
    resp.body().name == "previous TIPP"
    resp.body().publisherName == "some Publisher"
    resp.body()._embedded.ids?.size() == 2
    resp.body()._embedded.coverageStatements?.size() == 2
  }

  void "test TIPP merge keep updates"() {
    given:
    TitleInstancePackagePlatform dupe = TitleInstancePackagePlatform.findByName("merge victim TIPP")
    TitleInstancePackagePlatform target = TitleInstancePackagePlatform.findByName("merge target TIPP")
    String urlPath = getUrlPath()

    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.PUT("${urlPath}/rest/tipps/${dupe.id}/merge?target=${target.id}", null)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    sleep(500)
    dupe.refresh()
    target.refresh()
    dupe.status.value == 'Deleted'
    target.status.value == 'Current'
    target.url == 'http://some.new.net/'
    target.coverageStatements[0].startVolume == "3"
    target.ids[0].value == '2345-2331'
    target.accessEndDate == null
  }
}
