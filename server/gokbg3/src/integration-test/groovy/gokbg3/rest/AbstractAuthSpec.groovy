package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import spock.lang.Specification

class AbstractAuthSpec extends Specification {

  private RestBuilder rest = new RestBuilder()

  private String accessToken = null
  private String refreshToken = null

  private String getAccessToken() {
    if (accessToken == null) {
      login()
    }
    return accessToken
  }

  private String getRefreshToken() {
    if (refreshToken == null) {
      login()
    }
    return accessToken
  }

  private void login() {
    // calling /rest/login to obtain a valid bearerToken

    RestResponse resp = rest.post("http://localhost:$serverPort/gokb/rest/login") {
      // headers
      accept('application/json')
      contentType('application/json')
      // body
      body('{"username":"admin","password":"admin"}')
    }
    accessToken = resp.json.access_token
    refreshToken = resp.json.refresh_token
  }
}
