package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.Package
import org.springframework.web.context.WebApplicationContext

@Integration
@Rollback
class PackageTestSpec extends AbstractAuthSpec {

  @Autowired
  WebApplicationContext ctx

  private RestBuilder rest = new RestBuilder()

  def setupSpec(){
  }

  def setup() {
    def pkg = Package.findByName("TestPack") ?: new Package(name: "TestPack").save(flush:true)
  }

  void "test /rest/packages/<id> without token"() {
    def pack = Package.findByName("TestPack")

    when:
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/packages/${pack.id}") {
      // headers
      accept('application/json')
    }
    then:
    resp.status == 401 // Unauthorized
  }

  void "test /rest/packages with valid token"() {
    def pack = Package.findByName("TestPack")

    // use the bearerToken to read /rest/profile
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/packages/${pack.id}") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    resp.json.name == "TestPack"
  }
}
