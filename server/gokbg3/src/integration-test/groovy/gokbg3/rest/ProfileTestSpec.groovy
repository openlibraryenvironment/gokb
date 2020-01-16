package gokbg3.rest


import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import spock.lang.Ignore
import spock.lang.Specification

@Integration
class ProfileTestSpec extends AbstractAuthSpec {

  private RestBuilder rest = new RestBuilder()

  void "test /rest/profile without token"() {
    when:
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/profile") {
      // headers
      accept('application/json')
    }
    then:
    resp.status == 401 // Unauthorized
  }

  void "test /rest/profile with valid token"() {
    // use the bearerToken to read /rest/profile
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/profile") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    resp.json.email == "admin@localhost"
  }

  @Ignore
  void "test /rest/profile with stale token"() {
    // use the bearerToken to read /rest/profile
    when:
    String accessToken = getAccessToken()
    // logout => invalidate token on the server
    RestResponse resp = rest.post("http://localhost:$serverPort/gokb/rest/logout") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body()
    }
    // reuse the stale token
    resp = rest.get("http://localhost:$serverPort/gokb/rest/profile") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 401 // Unauthorized
  }

  @Ignore
  void "test /rest/profile/update"() {
    // use the bearerToken to write to /rest/profile/update
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.post("http://localhost:$serverPort/gokb/rest/profile/update") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body('')
    }
    then:
    resp.status == 401 // Unauthorized
  }
}
