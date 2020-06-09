package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.Package
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.JournalInstance
import org.gokb.cred.Platform
import grails.converters.JSON
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.WebApplicationContext

@Integration
@Rollback
class PackageTestSpec extends AbstractAuthSpec {

  @Autowired
  WebApplicationContext ctx

  private RestBuilder rest = new RestBuilder()
  def testPackage
  def testGroup
  def testTitle
  def testPlt
  def last = false

  def setupSpec() {
  }

  def setup() {
    testPackage = Package.findByName("TestPack") ?: new Package(name: "TestPack").save(flush: true)
    testGroup = CuratoryGroup.findByName("cgtest1") ?: new CuratoryGroup(name: "cgtest1").save(flush: true)
    testTitle = JournalInstance.findByName("PackTestTitle") ?: new JournalInstance(name: "PackTestTitle").save(flush:true)
    testPlt = Platform.findByName("PackTestPlt") ?: new Platform(name: "PackTestPlt").save(flush:true)
  }

  def cleanup() {
    Package.findByName("TestPack")?.refresh()?.expunge()
    Package.findByName("UpdPack")?.refresh()?.expunge()
    Package.findByName("TestPackageWithTipps")?.refresh()?.expunge()
    CuratoryGroup.findByName("cgtest1")?.refresh()?.expunge()
    JournalInstance.findByName("PackTestTitle")?.refresh()?.expunge()
    Platform.findByName("PackTestPlt")?.refresh()?.expunge()
  }

  void "test /rest/packages/<id> without token"() {
    given:
    def urlPath = getUrlPath()
    when:
    RestResponse resp = rest.get("${urlPath}/rest/packages/${testPackage.id}") {
      // headers
      accept('application/json')
    }
    then:
    resp.status == 401 // Unauthorized
  }

  void "test /rest/packages with valid token"() {
    given:
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("${urlPath}/rest/packages/${testPackage.id}") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    resp.json.name == "TestPack"
  }

  void "test /rest/packages update name"() {
    given:
    def upd_body = [name: 'UpdPack']
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.put("${urlPath}/rest/packages/${testPackage.id}") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
      body(upd_body as JSON)
    }
    then:
    resp.status == 200 // OK
    resp.json.name == "UpdPack"
  }

  void "test /rest/packages update comboList"() {
    given:
    def upd_body = [curatoryGroups: [testGroup.id]]
    def urlPath = getUrlPath()
    last = true
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.put("${urlPath}/rest/packages/${testPackage.id}") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
      body(upd_body as JSON)
    }
    then:
    resp.status == 200 // OK
    resp.json._embedded?.curatoryGroups?.size() == 1
    resp.json._embedded?.curatoryGroups[0].id == testGroup.id
  }

  void "test /rest/packages post with new tipps"() {
    given:
    def upd_body = [
      name: "TestPackageWithTipps",
      tipps: [
        [
          title: testTitle.id,
          hostPlatform: testPlt.id,
          url: "http://testpkgwithtipp.test"
        ]
      ]
    ]
    def urlPath = getUrlPath()
    last = true
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.post("${urlPath}/rest/packages?_embed=tipps") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
      body(upd_body as JSON)
    }
    then:
    resp.status == 200 // OK
    resp.json._embedded.tipps.size() == 1
    resp.json._embedded.tipps[0].url == upd_body.tipps[0].url
  }
}
