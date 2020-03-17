package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import spock.lang.Specification
import grails.converters.JSON

class AbstractAuthSpec extends Specification {

  private RestBuilder rest = new RestBuilder()

  private def accessToken = null
  private String refreshToken = null
  private String activeUser = "admin"

  private String getAccessToken(def username = 'admin') {
    if (accessToken == null) {
      login(username)
    }
    return accessToken
  }

  private String getRefreshToken(def username = 'admin') {
    if (refreshToken == null) {
      login(username)
    }
    return accessToken
  }

  private void login(username) {
    // calling /rest/login to obtain a valid bearerToken

    RestResponse resp = rest.post("http://localhost:$serverPort/gokb/rest/login") {
      // headers
      accept('application/json')
      contentType('application/json')
      // body
      body([username: username, password:username] as JSON)
    }
    accessToken = resp.json?.access_token ?: accessToken
    refreshToken = resp.json?.refresh_token ?: refreshToken
    activeUser = username
  }
}
