package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.Package
import org.gokb.cred.Platform
import org.gokb.cred.JournalInstance
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
  def last = false

  def setupSpec() {
  }

  def setup() {
    testPackage = Package.findByName("TippTestPack") ?: new Package(name: "TippTestPack").save(flush: true)
    testPlatform = Platform.findByName("TippTestPlat") ?: new Platform(name: "TippTestPlat").save(flush: true)
    testTitle = JournalInstance.findByName("TippTestJournal") ?: new JournalInstance(name: "TippTestJournal").save(flush: true)
    testGroup = CuratoryGroup.findByName("cgtipptest") ?: new CuratoryGroup(name: "cgtipptest").save(flush: true)
  }

  def cleanup() {
    if (last) {
      sleep(500)
      Package.findByName("TippTestPack")?.expunge()
      Platform.findByName("TippTestPlat")?.expunge()
      JournalInstance.findByName("TippTestJournal")?.expunge()
      CuratoryGroup.findByName("cgtipptest")?.expunge()
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
    RestResponse resp = rest.get("${urlPath}/rest/tipps") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    resp.json != null
  }

  void "test /rest/tipps POST"() {
    given:
    def upd_body = [
        pkg         : testPackage.id,
        hostPlatform: testPlatform.id,
        name        : "TippName",
        title       : testTitle.id,
        url         : "http://host-url.test/old",
        coverage    : [
            [
                startDate    : "2010-01-01",
                startVolume  : "1",
                startIssue   : "1",
                coverageDepth: "Fulltext"
            ]
        ],
            publisherName: "other Publisher"
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
    resp.status == 200 // OK
    resp.json.url == upd_body.url
    resp.json._embedded.coverageStatements?.size() == 1
    resp.json.publisherName == "other Publisher"
  }

  void "test /rest/tipps/<id> PUT"() {
    given:
    sleep(200)
    def tipp = TitleInstancePackagePlatform.findByUrl("http://host-url.test/old")
    def upd_body = [
        pkg               : testPackage.id,
        hostPlatform      : testPlatform.id,
        title             : testTitle.id,
        name              : "TippName",
        publisherName     : "some Publisher",
        url               : "http://host-url.test/new",
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
    resp.json.url == upd_body.url
    resp.json.name == "TippName"
    resp.json.publisherName == "some Publisher"
    resp.json._embedded.coverageStatements?.size() == 2
    resp.json._embedded.coverageStatements.collect { it.id }.contains(tipp.coverageStatements[0].id.toInteger()) == true
  }
}
