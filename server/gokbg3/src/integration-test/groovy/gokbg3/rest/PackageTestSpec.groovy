package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.Package
import org.gokb.cred.CuratoryGroup
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
  def last = false

  def setupSpec() {
  }

  def setup() {
    testPackage = Package.findByName("TestPack") ?: new Package(name: "TestPack").save(flush: true)
    testGroup = CuratoryGroup.findByName("cgtest1") ?: new CuratoryGroup(name: "cgtest1").save(flush: true)
  }

  def cleanup() {
    if (last) {
      testPackage?.refresh().expunge()
      testGroup?.refresh().expunge()
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
}
