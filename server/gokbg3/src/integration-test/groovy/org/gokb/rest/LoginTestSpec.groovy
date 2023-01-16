package org.gokb.rest

import grails.core.GrailsApplication
import grails.testing.mixin.integration.Integration

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.uri.UriBuilder

import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

/**
 * See http://www.gebish.org/manual/current/ for more instructions
 */
@Integration
class LoginTestSpec extends Specification {

  GrailsApplication grailsApplication


  BlockingHttpClient http

  private String getUrlPath() {
    return "http://localhost:${serverPort}${grailsApplication.config.getProperty('server.contextPath') ?: ''}".toString()
  }

  def setup() {
    if (!http) {
      http = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }
  }

  void "test getting tokens with valid credentials"() {
    when:
    Map requestBody = [
      username: "admin",
      password: "admin"
    ]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/rest/login", requestBody)
    HttpResponse response = http.exchange(request, Map)

    then:
    response.status == HttpStatus.OK
    response.body().access_token != null
  }

  @Ignore
  /*
  refresh_token is not supported by the grailscache token store
  */
  void "test refresh tokens "() {
    when:
    Map requestBody = [
      username: "admin",
      password: "admin"
    ]

    HttpRequest req1 = HttpRequest.POST(getUrlPath() + "/rest/login", requestBody)
    HttpResponse resp1 = http.exchange(req1)

    String refreshToken = resp1.body().refresh_token
    String accessToken = resp1.body().access_token
    URI uri = UriBuilder.of(getUrlPath())
      .path("/oauth/access_token")
      .queryParam("grant_type", "refresh_token")
      .queryParam("refresh_token", refreshToken)
      .build()

    HttpRequest req2 = HttpRequest.POST(uri)
    HttpResponse response = http.exchange(req2, Map)

    then:
    response.status == HttpStatus.OK
    response.body().access_token != accessToken
  }

  void "test logout"() {
    when:
    Map requestBody = [
      username: "admin",
      password: "admin"
    ]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/rest/login", requestBody)
    HttpResponse resp1 = http.exchange(request, Map)

    String accessToken = resp1.body().access_token
    HttpRequest req2 = HttpRequest.POST(getUrlPath() + "/rest/logout", requestBody)
      .bearerAuth(accessToken)
    HttpResponse response = http.exchange(req2, Map)

    then:
    response.status == HttpStatus.OK
    response.body() == null
  }
}

