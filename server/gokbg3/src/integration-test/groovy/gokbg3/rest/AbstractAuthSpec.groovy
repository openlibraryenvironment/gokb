package gokbg3.rest

import grails.converters.JSON
import grails.core.GrailsApplication
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification

class AbstractAuthSpec extends Specification {

  GrailsApplication grailsApplication

  @Autowired
  WebApplicationContext ctx

  private RestBuilder authRest = new RestBuilder()

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

  private String getUrlPath() {
    return "http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}".toString()
  }

  private void login(username, password) {
    // calling /authRest/login to obtain a valid bearerToken

    RestResponse resp = authRest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/rest/login") {
      // headers
      accept('application/json')
      contentType('application/json')
      // body
      body([username: username, password: password] as JSON)
    }
    accessToken = resp.json?.access_token ?: accessToken
    refreshToken = resp.json?.refresh_token ?: refreshToken
    activeUser = username
    // log.debug(resp.toString())
  }
}
