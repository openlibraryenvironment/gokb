package gokbg3.rest


import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.User

@Integration
@Rollback
class ProfileTestSpec extends AbstractAuthSpec {

  private RestBuilder rest = new RestBuilder()

  void "test GET /rest/profile without token"() {
    when:
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/profile") {
      // headers
      accept('application/json')
    }
    then:
    resp.status == 401 // Unauthorized
  }

  void "test GET /rest/profile with valid token"() {
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
    resp.json.data.email == "admin@localhost"
    resp.json.data._links.self.href == "rest/profile"
  }

  void "test GET /rest/profile with stale token"() {
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
    resp.status == 200
    // reuse the stale token
    resp = rest.get("http://localhost:$serverPort/gokb/rest/profile") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 401 // Unauthorized
    when:
    resp = rest.get("http://localhost:$serverPort/gokb/rest/profile") {
      // headers
      accept('application/json')
    }
    then:
    resp.status == 401 // Unauthorized
  }

  void "test PUT /rest/profile"() {
    // use the bearerToken to write to /rest/profile/update
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.put("http://localhost:$serverPort/gokb/rest/profile") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body('{"id":8,"username":"admin","displayName":null,"email":"admin@localhost","curatoryGroups":[],"enabled":true,"accountExpired":false,"accountLocked":false,"passwordExpired":false,"defaultPageSize":10,' +
        '"roles":[' +
        '{' +
        '"authority":"ROLE_CONTRIBUTOR",' +
        '},' +
        '{' +
        '"authority":"ROLE_USER",' +
        '},' +
        '{' +
        '"authority":"ROLE_EDITOR",' +
        '},' +
        '{' +
        '"authority":"ROLE_ADMIN",' +
        '},' +
        '{' +
        '"authority":"ROLE_API",' +
        '},' +
        '{' +
        '"authority":"ROLE_SUPERUSER",' +
        '}' +
        ']' +
        '}')
    }
    then:
    resp.status == 200
  }

  void "test DELETE /rest/profile/"() {
    when:
    String accessToken = getAccessToken('tempUser', 'tempUser')
    RestResponse resp = rest.delete("http://localhost:$serverPort/gokb/rest/profile/") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200
    User.findByUsername('tempUser') == null
  }
}
