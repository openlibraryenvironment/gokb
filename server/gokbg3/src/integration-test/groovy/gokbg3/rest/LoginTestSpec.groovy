package gokbg3.rest

import grails.core.GrailsApplication
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import spock.lang.Ignore
import spock.lang.Specification

/**
 * See http://www.gebish.org/manual/current/ for more instructions
 */
@Integration
class LoginTestSpec extends Specification {

  GrailsApplication grailsApplication
  private RestBuilder rest = new RestBuilder()

  void "test getting tokens with valid credentials"() {
    when:
    RestResponse response = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/rest/login") {
      accept('application/json')
      // body
      body('{"username": "admin","password": "admin"}')
      contentType('application/json')
    }

    then:
    response.status == 200 // OK
    response.json.access_token != null
  }

  @Ignore
  /*
  refresh_token is not supported by the grailscache token store
  */
  void "test refresh tokens "() {
    when:
    RestResponse response = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/rest/login") {
      accept('application/json')
      contentType('application/json')
      // body
      body('{"username": "admin","password": "admin"}')
    }
    String refreshToken = response.json.refresh_token
    String accessToken = response.json.access_token
    response = rest.post("http://localhost:$serverPort/gokb" +
        "/oauth/access_token?" +
        "grant_type=refresh_token&refresh_token=$refreshToken") {
      accept('application/json')
    }

    then:
    response.status == 200 // OK
    response.json.access_token != accessToken
  }

  void "test logout"() {
    when:
    RestResponse response = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/rest/login") {
      accept('application/json')
      contentType('application/json')
      // body
      body('{"username": "admin","password": "admin"}')
    }
    String accessToken = response.json.access_token
    response = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}" +
        "/rest/logout"){
      accept('application/json')
      auth("Bearer $accessToken")
    }

    then:
    response.status == 200 // OK
    response.json == null
  }
}

