package org.gokb.rest

import grails.core.GrailsApplication
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification
import spock.lang.Shared

class AbstractAuthSpec extends Specification {

  GrailsApplication grailsApplication

  @Autowired
  WebApplicationContext ctx

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
    return "http://localhost:${serverPort}${grailsApplication.config.getProperty('server.servlet.context-path', String) ?: ''}".toString()
  }

  private void login(username, password) {
    // calling /authRest/login to obtain a valid bearerToken

    Map request_body = [username: username, password: password]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/rest/login", request_body)
    HttpResponse resp = HttpClient.create(new URL(getUrlPath())).toBlocking().exchange(request, Map)

    accessToken = resp.body().access_token ?: accessToken
    refreshToken = resp.body().refresh_token ?: refreshToken
    activeUser = username
    // log.debug(resp.toString())
  }
}
