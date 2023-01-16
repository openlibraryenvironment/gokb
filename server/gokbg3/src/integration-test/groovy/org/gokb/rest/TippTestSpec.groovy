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

import org.gokb.cred.Package
import org.gokb.cred.Platform
import org.gokb.cred.JournalInstance
import org.gokb.cred.RefdataCategory
import org.gokb.cred.TitleInstancePackagePlatform
import org.gokb.cred.TIPPCoverageStatement
import org.gokb.cred.CuratoryGroup
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
  def testGroup
  def previousTipp
  def last = false

  def setupSpec() {
  }

  def setup() {
    if (!http) {
      http = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }

    testPackage = Package.findByName("TippTestPack") ?: new Package(name: "TippTestPack").save(flush: true)
    testPlatform = Platform.findByName("TippTestPlat") ?: new Platform(name: "TippTestPlat").save(flush: true)
    testTitle = JournalInstance.findByName("TippTestJournal") ?: new JournalInstance(name: "TippTestJournal").save(flush: true)
    testGroup = CuratoryGroup.findByName("cgtipptest") ?: new CuratoryGroup(name: "cgtipptest").save(flush: true)

    if (!TitleInstancePackagePlatform.findByName("previous TIPP")) {
      previousTipp = new TitleInstancePackagePlatform(name: "previous TIPP", pkg: testPackage, hostPlatform: testPlatform, url: "http://some.uri/").save(flush: true)
      def coverage = new TIPPCoverageStatement(owner: previousTipp, startVolume: 1, startIssue: 1, coverageDepth: RefdataCategory.lookup("Coverage.Depth", "Selected Articles")).save(flush: true)
    }
    else {
      previousTipp = TitleInstancePackagePlatform.findByName("previous TIPP")
    }
  }

  def cleanup() {
    if (last) {
      sleep(500)
      Package.findByName("TippTestPack")?.refresh().expunge()
      Platform.findByName("TippTestPlat")?.refresh().expunge()
      JournalInstance.findByName("TippTestJournal")?.refresh().expunge()
      CuratoryGroup.findByName("cgtipptest")?.refresh().expunge()
      TitleInstancePackagePlatform.findByName("previous TIPP")?.refresh().expunge()
    }
  }

  void "test /rest/tipps without token"() {
    given:
    def urlPath = getUrlPath()
    when:
    HttpRequest request = HttpRequest.GET("${urlPath}/rest/tipps")
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body() != null
  }

  void "test /rest/tipps with valid token"() {
    given:
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.GET("${urlPath}/rest/tipps?_embed=prices")
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body() != null
    resp.body().data['_embedded'].prices.size() > 0
  }

  void "test /rest/tipps POST"() {
    given:
    def upd_body = [
        pkg          : testPackage.id,
        hostPlatform : testPlatform.id,
        name         : "TippName",
        title        : testTitle.id,
        url          : "http://host-url.test/old",
        coverage     : [
            [
                startDate    : "2005-01-01",
                startVolume  : "1",
                startIssue   : "1",
                coverageDepth: "Fulltext"
            ]
        ],
        publisherName: "other Publisher",
        prices       : [
            [
                type    : [name: 'list'],
                price   : 12.95,
                currency: [name: "EUR"]
            ]
        ]
    ]

    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.POST("${urlPath}/rest/tipps", upd_body)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.CREATED
    resp.body().url == upd_body.url
    resp.body()._embedded.coverageStatements?.size() == 1
    resp.body()._embedded.prices?.size() == 1
    resp.body().publisherName == "other Publisher"
  }

  void "test /rest/tipps/<id> PUT"() {
    given:
    def tipp = TitleInstancePackagePlatform.findByUrl("http://some.uri/")
    def coverage_id = tipp.coverageStatements[0].id
    def upd_body = [
        pkg               : testPackage.id,
        hostPlatform      : testPlatform.id,
        title             : testTitle.id,
        publisherName     : "some Publisher",
        url               : "http://new-url.url",
        coverageStatements: [
            [
                id           : coverage_id,
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
    def urlPath = getUrlPath()
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
    resp.body()._embedded.coverageStatements.collect { it.id }.contains(coverage_id.toInteger()) == true
  }

  void "test add new TIPP price"() {
    given:
    def tipp = TitleInstancePackagePlatform.findByUrl("http://new-url.url")
    def coverage_id = tipp.coverageStatements[0].id
    def upd_body = [
        pkg               : testPackage.id,
        hostPlatform      : testPlatform.id,
        title             : testTitle.id,
        publisherName     : "some Publisher",
        url               : "http://new-url.url",
        coverageStatements: [
            [
                id           : coverage_id,
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
        ],
        prices: [
          [
            price: "0.01",
            currency: [ name: "EUR"],
            startDate: "2020-01-01",
            type: "list"
          ]
        ]
    ]
    def urlPath = getUrlPath()
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
    resp.body()._embedded.coverageStatements.collect { it.id }.contains(coverage_id.toInteger()) == true
    resp.body()._embedded.prices?.size() == 1
    resp.body()._embedded.prices[0].price == "0.01"
  }

  void "test remove TIPP price"() {
    given:
    def tipp = TitleInstancePackagePlatform.findByUrl("http://new-url.url")
    def coverage_id = tipp.coverageStatements[0].id
    def upd_body = [
        pkg               : testPackage.id,
        hostPlatform      : testPlatform.id,
        title             : testTitle.id,
        publisherName     : "some Publisher",
        url               : "http://new-url.url",
        coverageStatements: [
            [
                id           : coverage_id,
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
        ],
        prices: []
    ]
    def urlPath = getUrlPath()
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
    resp.body()._embedded.coverageStatements.collect { it.id }.contains(coverage_id.toInteger()) == true
    resp.body()._embedded.prices?.size() == 0
  }

  void "test replace TIPP price"() {
    given:
    def tipp = TitleInstancePackagePlatform.findByUrl("http://new-url.url")
    def coverage_id = tipp.coverageStatements[0].id
    def init_body = [
        pkg               : testPackage.id,
        hostPlatform      : testPlatform.id,
        title             : testTitle.id,
        publisherName     : "some Publisher",
        url               : "http://new-url.url",
        ids: [
          [
            type: 'issn',
            value: '3245-2349'
          ],
          [
            type: 'eissn',
            value: '3241-2541'
          ]
        ],
        prices: [
          [
            price: "0.01",
            currency: [ name: "EUR"],
            startDate: "2020-01-01",
            type: "list"
          ]
        ]
    ]
    def upd_body = [
        pkg               : testPackage.id,
        hostPlatform      : testPlatform.id,
        title             : testTitle.id,
        publisherName     : "some Publisher",
        url               : "http://new-url.url",
        ids: [
          [
            type: 'issn',
            value: '3245-2341'
          ],
          [
            type: 'eissn',
            value: '3241-2541'
          ]
        ],
        prices: [
          [
            price: "10.58",
            currency: [ name: "EUR"],
            startDate: "2020-01-01",
            type: "list"
          ]
        ]
    ]

    def urlPath = getUrlPath()
    when:
    last = true
    String accessToken = getAccessToken()
    HttpRequest req1 = HttpRequest.PUT("${urlPath}/rest/tipps/${tipp.id}", init_body)
      .bearerAuth(accessToken)
    HttpResponse resp1 = http.exchange(req1, Map)

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
    resp.body()._embedded.coverageStatements.collect { it.id }.contains(coverage_id.toInteger()) == true
    resp.body()._embedded.prices?.size() == 1
    resp.body()._embedded.prices[0].price == "10.58"
  }
}
