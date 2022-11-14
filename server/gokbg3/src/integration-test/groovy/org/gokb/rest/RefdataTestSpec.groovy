package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.*
import grails.testing.mixin.integration.Integration
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import org.gokb.cred.Source
import spock.lang.Specification
import spock.lang.Shared

@Integration
@Rollback
class RefdataTestSpec extends AbstractAuthSpec {


  HttpClient http

  void "test GET /rest/package-scopes"() {
    given:
    def urlPath = getUrlPath()
    when:
    HttpRequest request = HttpRequest.GET("$urlPath/rest/package-scopes")
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.OK
    resp.body().data.size() >= 4
    resp.body()._links.self.href.contains ("/rest/package-scopes")
  }

  void "test GET /rest/coverage-depth"() {
    given:
    def urlPath = getUrlPath()
    when:
    HttpRequest request = HttpRequest.GET("$urlPath/rest/coverage-depth")
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.OK
    resp.body().data.size() == 3
    resp.body()._links.self.href.contains ("/rest/coverage-depth")
  }
}
