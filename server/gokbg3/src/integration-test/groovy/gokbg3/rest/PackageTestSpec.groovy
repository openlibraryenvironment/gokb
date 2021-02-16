package gokbg3.rest

import grails.gorm.transactions.Transactional
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.Org
import org.gokb.cred.Package
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.JournalInstance
import org.gokb.cred.Platform
import grails.converters.JSON
import org.gokb.cred.RefdataCategory
import org.gokb.cred.Source
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
  def testOrg
  def testSource
  def last = false

  def setupSpec() {
  }

  def setup() {
    testPackage = Package.findByName("TestPack") ?: new Package(name: "TestPack").save(flush: true)
    testGroup = CuratoryGroup.findByName("cgtest1") ?: new CuratoryGroup(name: "cgtest1").save(flush: true)
    testTitle = JournalInstance.findByName("PackTestTitle") ?: new JournalInstance(name: "PackTestTitle").save(flush: true)
    testPlt = Platform.findByName("PackTestPlt") ?: new Platform(name: "PackTestPlt").save(flush: true)
    testOrg = Org.findByName("PackTestOrg") ?: new Org(name: "PackTestOrg").save(flush: true)
    def http = RefdataCategory.lookup('Source.DataSupplyMethod', 'HTTP Url').save(flush: true)
    def kbart = RefdataCategory.lookup('Source.DataFormat', 'KBART').save(flush: true)
    def freq = RefdataCategory.lookup('Source.Frequency', 'Weekly').save(flush: true)
    testSource = Source.findByName("PackTestSource") ?: new Source(
      name: "PackTestSource",
      url: "https://org/package",
      frequency: freq,
      defaultSupplyMethod: http,
      defaultDataFormat: kbart)
    //.save(flush: true)
  }

  def cleanup() {
    if (last) {
      sleep(500)
      Package.findByName("TestPack")?.refresh()?.expunge()
      Package.findByName("UpdPack")?.refresh()?.expunge()
      Package.findByName("TestPackageWithTipps")?.refresh()?.expunge()
      Package.findByName("TestPackageWithProviderAndPlatform")?.refresh()?.expunge()
      CuratoryGroup.findByName("cgtest1")?.refresh()?.expunge()
      JournalInstance.findByName("PackTestTitle")?.refresh()?.expunge()
      Platform.findByName("PackTestPlt")?.refresh()?.expunge()
      Org.findByName("PackTestOrg")?.refresh()?.expunge()
      Source.findByName("PackTestSource")?.refresh()?.expunge()
    }
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
    resp.status == 200 // OK
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
    sleep(500)
    resp.json._embedded?.curatoryGroups?.size() == 1
    resp.json._embedded?.curatoryGroups[0].id == testGroup.id
  }

  void "test /rest/packages post with new tipps"() {
    given:
    def upd_body = [
      name : "TestPackageWithTipps",
      tipps: [
        [
          title       : testTitle.id,
          hostPlatform: testPlt.id,
          url         : "http://testpkgwithtipp.test",
          name        : "TIPP Name"
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
    resp.status == 201 // OK
    resp.json._embedded.tipps.size() == 1
    resp.json._embedded.tipps[0].url == upd_body.tipps[0].url
    resp.json._embedded.tipps[0].name == upd_body.tipps[0].name
  }

  void "test /rest/packages post with provider, source and platform"() {
    given:
    def new_body = [
      name           : "TestPackageWithProviderAndPlatform",
      breakable      : "Yes",
      consistent     : "Yes",
      description    : "kjkljslkdfsdf",
      descriptionURL : "https://heise.de",
      fixed          : "Yes",
      global         : "Consortium",
      globalNote     : "Testing Consortium",
      ids            : [
        [
          "value"    : "1213-123X",
          "namespace": "issn"
        ]
      ],
      provider       : testOrg.id,
      nominalPlatform: testPlt.id,
      source         : [id: testSource.id],
      scope          : [name: "Front File"]
    ] as JSON
    def urlPath = getUrlPath()
    last = true
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.post("${urlPath}/rest/packages?_embed=tipps") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
      body(new_body)
    }
    then:
    resp.json.errors == null
    resp.status == 201 // OK
    resp.json.source != null
    resp.json.provider != null
    resp.json.nominalPlatform != null
    resp.json.scope.name == "Front File"
    resp.json.globalNote == "Testing Consortium"
  }
}
