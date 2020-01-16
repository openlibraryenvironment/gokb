package gokbg3

import com.fasterxml.jackson.annotation.JsonProperty
import geb.spock.GebSpec
import grails.testing.mixin.integration.Integration
import grails.testing.spock.OnceBefore
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import spock.lang.AutoCleanup
import spock.lang.Shared

/**
 * See http://www.gebish.org/manual/current/ for more instructions
 */
@Integration
class LoginTestSpec extends GebSpec {
  @Shared
  @AutoCleanup
  HttpClient client //= HttpClient.create(new URL("http://localhost:$serverPort/"))
  HttpRequest request
  HttpResponse response

  @OnceBefore
  void init() {
  }

  def setup() {
  }

  def cleanup() {
  }

  def 'test wrong credentials'() {
    when:
    client = HttpClient.create(new URL("http://localhost:$serverPort/"))
    request = HttpRequest.GET('/gokb/rest/profile').header('Accept', 'application/json')
    response = client.toBlocking().exchange request

    then:
    HttpClientResponseException e = thrown(Exception)
    e.response.status == HttpStatus.UNAUTHORIZED
  }

  def "test correct credentials"() {
    when:
    client = HttpClient.create(new URL("http://localhost:$serverPort/"))
    UserCredentials credentials = new UserCredentials(username: 'admin', password: 'admin')
    request = HttpRequest.POST('/gokb/rest/login', credentials)
    response = client.toBlocking().exchange request, BearerToken

    then:
    response.status.code == 200
    response.body != null
    def refreshToken = response.body.value.refreshToken
    def accessToken = response.body.value.accessToken

    when:
    client = HttpClient.create(new URL("http://localhost:$serverPort/"))
    request = HttpRequest.GET('/gokb/rest/profile')
    request.header('Accept', 'application/json')
    // Strangely, this works even without the Authorization parameter
    request.parameters.add('Authorization', 'Bearer ' + accessToken)
    response = client.toBlocking().exchange request, UserProfile

    then:
    response.status.code == 200
    response.body.value.userName == 'admin'
    response.body.value.defaultPageSize == 10
    response.body.value.enabled == true
  }
}

class UserCredentials {
  String username
  String password
}

class BearerToken {
  @JsonProperty('access_token')
  String accessToken

  @JsonProperty('refresh_token')
  String refreshToken

  List<String> roles

  String username
}

class UserProfile {
  @JsonProperty('id')
  int id
  @JsonProperty('username')
  String userName
  @JsonProperty('displayname')
  String displayName
  @JsonProperty('email')
  String eMail
  @JsonProperty('curatoryGroups')
  Collection<Map<String, Object>> curatoryGroups
  @JsonProperty('enabled')
  boolean enabled
  @JsonProperty('accountLocked')
  boolean accountLocked
  @JsonProperty('accountExpired')
  boolean accountExpired
  @JsonProperty('passwordExpired')
  boolean passwordExpired
  @JsonProperty('defaultPageSize')
  int defaultPageSize
}

class CustomError {
  Integer status
  String error
  String message
  String path
}