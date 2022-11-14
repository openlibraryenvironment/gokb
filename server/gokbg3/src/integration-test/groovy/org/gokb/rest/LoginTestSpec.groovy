package org.gokb.rest

import grails.core.GrailsApplication
import grails.testing.mixin.integration.Integration
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

/**
 * See http://www.gebish.org/manual/current/ for more instructions
 */
@Integration
class LoginTestSpec extends Specification {

  GrailsApplication grailsApplication


  HttpClient http

  void "test getting tokens with valid credentials"() {
    when:
    HttpRequest request = HttpRequest.POST("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/rest/login", '{"username": "admin","password": "admin"}')
    HttpResponse response = http.toBlocking().exchange(request)

    then:
    response.status == HttpStatus.OK
    response.json.access_token != null
  }

  @Ignore
  /*
  refresh_token is not supported by the grailscache token store
  */
  void "test refresh tokens "() {
    when:
    HttpRequest req1 = HttpRequest.POST("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/rest/login", '{"username": "admin","password": "admin"}')
    HttpResponse resp1 = http.toBlocking().exchange(req1)

    String refreshToken = resp1.body().refresh_token
    String accessToken = resp1.body().access_token
    HttpRequest req2 = HttpRequest.POST("http://localhost:$serverPort/gokb" +
        "/oauth/access_token?" +
        "grant_type=refresh_token&refresh_token=$refreshToken")
    HttpResponse response = http.toBlocking().exchange(req2)

    then:
    response.status == HttpStatus.OK
    response.body().access_token != accessToken
  }

  void "test logout"() {
    when:
    HttpRequest request = HttpRequest.POST("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/rest/login", '{"username": "admin","password": "admin"}')
    HttpResponse resp1 = http.toBlocking().exchange(request)

    String accessToken = resp1.body().access_token
    HttpRequest req2 = HttpRequest.POST("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}" +
        "/rest/logout").bearerAuth(accessToken)
    HttpResponse response = http.toBlocking().exchange(request)

    then:
    response.status == HttpStatus.OK
    response.body() == null
  }
}

