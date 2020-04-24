package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import groovy.util.logging.Slf4j
import spock.lang.Specification
import grails.converters.JSON

@Slf4j
class AbstractAuthSpec extends Specification {

  private RestBuilder rest = new RestBuilder()

  private def accessToken = null
  private String refreshToken = null
  private String activeUser = "admin"

  private String getAccessToken(String username = 'admin', String password = 'admin') {
    if (accessToken == null) {
      login(username, password)
    }
    return accessToken
  }

  private String getRefreshToken(def username = 'admin', String password = 'admin') {
    if (refreshToken == null) {
      login(username, password)
    }
    return refreshToken
  }

  private void login(String username, String password) {
    // calling /rest/login to obtain a valid bearerToken
    RestResponse resp = rest.post("http://localhost:$serverPort/gokb/rest/login") {
      // headers
      accept('application/json')
      contentType('application/json')
      // body
      body([username: username, password: password] as JSON)
    }
    accessToken = resp.json?.access_token ?: accessToken
    refreshToken = resp.json?.refresh_token ?: refreshToken
    activeUser = username
    log.debug(resp.toString())
  }
}
