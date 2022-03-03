package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.Package
import org.gokb.cred.Platform
import org.gokb.cred.JournalInstance
import org.gokb.cred.RefdataCategory
import org.gokb.cred.TitleInstancePackagePlatform
import org.gokb.cred.TIPPCoverageStatement
import org.gokb.cred.CuratoryGroup
import grails.converters.JSON
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.WebApplicationContext

@Integration
@Rollback
class TippTestSpec extends AbstractAuthSpec {

  @Autowired
  WebApplicationContext ctx

  private RestBuilder rest = new RestBuilder()
  def testPackage
  def testTitle
  def testPlatform
  def testGroup
  def previousTipp
  def last = false

  def setupSpec() {
  }

  def setup() {
    testPackage = Package.findByName("TippTestPack") ?: new Package(name: "TippTestPack").save(flush: true)
    testPlatform = Platform.findByName("TippTestPlat") ?: new Platform(name: "TippTestPlat").save(flush: true)
    testTitle = JournalInstance.findByName("TippTestJournal") ?: new JournalInstance(name: "TippTestJournal").save(flush: true)
    testGroup = CuratoryGroup.findByName("cgtipptest") ?: new CuratoryGroup(name: "cgtipptest").save(flush: true)
    previousTipp = TitleInstancePackagePlatform.findByName("previous TIPP") ?: new TitleInstancePackagePlatform(name: "previous TIPP", pkg: testPackage, hostPlatform: testPlatform, url: "http://some.uri/").save(flush: true)
    def coverage = new TIPPCoverageStatement(owner: previousTipp, startVolume: 1, startIssue: 1, coverageDepth: RefdataCategory.lookup("Coverage.Depth", "Selected Articles")).save(flush: true)
  }

  def cleanup() {
    if (last) {
      sleep(500)
      Package.findByName("TippTestPack")?.expunge()
      Platform.findByName("TippTestPlat")?.expunge()
      JournalInstance.findByName("TippTestJournal")?.expunge()
      CuratoryGroup.findByName("cgtipptest")?.expunge()
      TitleInstancePackagePlatform.findByName("previous TIPP")?.expunge()
      TitleInstancePackagePlatform.findByName("new TIPP name")?.expunge()
    }
  }

  void "test /rest/tipps without token"() {
    given:
    def urlPath = getUrlPath()
    when:
    RestResponse resp = rest.get("${urlPath}/rest/tipps") {
      // headers
      accept('application/json')
    }
    then:
    resp.status == 200 // OK
    resp.json != null
  }

  void "test /rest/tipps with valid token"() {
    given:
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("${urlPath}/rest/tipps?_embed=prices") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    resp.json != null
    resp.json.data['_embedded'].prices.size() > 0
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
    RestResponse resp = rest.post("${urlPath}/rest/tipps") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
      body(upd_body as JSON)
    }

    then:
    resp.status == 201 // CREATED
    resp.json.url == upd_body.url
    resp.json._embedded.coverageStatements?.size() == 1
    resp.json._embedded.prices?.size() == 1
    resp.json.publisherName == "other Publisher"
  }

  void "test /rest/tipps/<id> PUT"() {
    given:
    def tipp = TitleInstancePackagePlatform.findByUrl("http://some.uri/")
    def coverage_id = tipp.coverageStatements[0].id
    def upd_body = [
        pkg               : testPackage.id,
        hostPlatform      : testPlatform.id,
        title             : testTitle.id,
        name              : "new TIPP name",
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
            value: '3245-2341'
          ],
          [
            type: 'eissn',
            value: '3241-2541'
          ]
        ]
    ]
    def urlPath = getUrlPath()
    when:
    last = true
    String accessToken = getAccessToken()
    RestResponse resp = rest.put("${urlPath}/rest/tipps/${tipp.id}") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
      body(upd_body as JSON)
    }
    then:
    resp.status == 200 // OK
    resp.json.id == tipp.id
    resp.json.url == upd_body.url
    resp.json.name == "new TIPP name"
    resp.json.publisherName == "some Publisher"
    resp.json._embedded.ids?.size() == 2
    resp.json._embedded.coverageStatements?.size() == 2
    resp.json._embedded.coverageStatements.collect { it.id }.contains(coverage_id.toInteger()) == true
  }

  void "test add new TIPP price"() {
    given:
    def tipp = TitleInstancePackagePlatform.findByUrl("http://some.uri/")
    def coverage_id = tipp.coverageStatements[0].id
    def upd_body = [
        pkg               : testPackage.id,
        hostPlatform      : testPlatform.id,
        title             : testTitle.id,
        name              : "new TIPP name",
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
            value: '3245-2341'
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
    last = true
    String accessToken = getAccessToken()
    RestResponse resp = rest.put("${urlPath}/rest/tipps/${tipp.id}") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
      body(upd_body as JSON)
    }
    then:
    resp.status == 200 // OK
    resp.json.id == tipp.id
    resp.json.url == upd_body.url
    resp.json.name == "new TIPP name"
    resp.json.publisherName == "some Publisher"
    resp.json._embedded.ids?.size() == 2
    resp.json._embedded.coverageStatements?.size() == 2
    resp.json._embedded.coverageStatements.collect { it.id }.contains(coverage_id.toInteger()) == true
    resp.json._embedded.prices?.size() == 1
    resp.json._embedded.prices[0].price == "0.01"
  }

  void "test remove TIPP price"() {
    given:
    def tipp = TitleInstancePackagePlatform.findByUrl("http://some.uri/")
    def coverage_id = tipp.coverageStatements[0].id
    def upd_body = [
        pkg               : testPackage.id,
        hostPlatform      : testPlatform.id,
        title             : testTitle.id,
        name              : "new TIPP name",
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
            value: '3245-2341'
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
    last = true
    String accessToken = getAccessToken()
    RestResponse resp = rest.put("${urlPath}/rest/tipps/${tipp.id}") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
      body(upd_body as JSON)
    }
    then:
    resp.status == 200 // OK
    resp.json.id == tipp.id
    resp.json.url == upd_body.url
    resp.json.name == "new TIPP name"
    resp.json.publisherName == "some Publisher"
    resp.json._embedded.ids?.size() == 2
    resp.json._embedded.coverageStatements?.size() == 2
    resp.json._embedded.coverageStatements.collect { it.id }.contains(coverage_id.toInteger()) == true
    resp.json._embedded.prices?.size() == 0
  }

  void "test replace TIPP price"() {
    given:
    def tipp = TitleInstancePackagePlatform.findByUrl("http://some.uri/")
    def coverage_id = tipp.coverageStatements[0].id
    def init_body = [
        pkg               : testPackage.id,
        hostPlatform      : testPlatform.id,
        title             : testTitle.id,
        name              : "new TIPP name",
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
            value: '3245-2341'
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
        name              : "new TIPP name",
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

    RestResponse resp_init = rest.put("${urlPath}/rest/tipps/${tipp.id}") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
      body(init_body as JSON)
    }
    RestResponse resp = rest.put("${urlPath}/rest/tipps/${tipp.id}") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
      body(upd_body as JSON)
    }
    then:
    resp.status == 200 // OK
    resp.json.id == tipp.id
    resp.json.url == upd_body.url
    resp.json.name == "new TIPP name"
    resp.json.publisherName == "some Publisher"
    resp.json._embedded.ids?.size() == 2
    resp.json._embedded.coverageStatements?.size() == 2
    resp.json._embedded.coverageStatements.collect { it.id }.contains(coverage_id.toInteger()) == true
    resp.json._embedded.prices?.size() == 1
    resp.json._embedded.prices[0].price == "10.58"
  }
}
