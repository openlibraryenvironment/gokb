package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import spock.lang.Ignore

@Integration
@Ignore
class PackageTestSpec extends AbstractAuthSpec {

  private RestBuilder rest = new RestBuilder()

  def setupSpec(){
  }

  def setup() {
  }

  void "test /rest/packages/<id> without token"() {
    when:
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/packages/"+pack.id) {
      // headers
      accept('application/json')
    }
    then:
    resp.status == 401 // Unauthorized
  }

  void "test /rest/packages with valid token"() {
    def pack = Package.findOrBuild(
        name: "TestPack"
    )
    // use the bearerToken to read /rest/profile
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/packages/$pack.id") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    resp.json.email == "admin@localhost"
  }
}
