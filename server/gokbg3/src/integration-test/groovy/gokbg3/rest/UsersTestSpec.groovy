package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration

@Integration
class UsersTestSpec extends AbstractAuthSpec {

  private RestBuilder rest = new RestBuilder()

  void "test GET /rest/users/{id} without token"() {
    when:
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/users/10") {
      // headers
      accept('application/json')
    }
    then:
    resp.status == 401 // Unauthorized
  }

  void "test GET /rest/users/{id} with valid token"() {
    // use the bearerToken to read /rest/users
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/users/10") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    resp.json.username == "deleted"
  }

  void "test DELETE /rest/users/{id} with valid token"() {
    // use the bearerToken to read /rest/users
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.delete("http://localhost:$serverPort/gokb/rest/users/10") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
  }

  void "test PUT /rest/users/{id}"() {
    // use the bearerToken to write to /rest/user
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.put("http://localhost:$serverPort/gokb/rest/users/10") {
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
}
