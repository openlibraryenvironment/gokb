package gokbg3

import com.fasterxml.jackson.annotation.JsonProperty
import com.sun.jna.WString
import geb.error.GebException
import geb.spock.GebSpec
import grails.testing.mixin.integration.Integration
import grails.testing.spock.OnceBefore

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientException
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
    HttpClient client

    @OnceBefore
    void init() {
        client = HttpClient.create(new URL("http://localhost:$serverPort/"))
    }

    def setup() {
    }

    def cleanup() {
    }

    def 'test wrong credentials'() {
        when:
        client = HttpClient.create(new URL("http://localhost:$serverPort/"))
        HttpRequest request = HttpRequest.POST('/gokb/rest/login', new UserCredentials(username: 'pad', password: 'woot?'))
        client.toBlocking().exchange request

        then:
        HttpClientResponseException e = thrown(Exception)
        e.response.status == HttpStatus.UNAUTHORIZED
    }

    def "test correct credentials"() {
        when:
        HttpRequest request = HttpRequest.POST('/gokb/api/checkLogin?format=json', '')
        HttpResponse response = client.toBlocking().exchange request

        then:
        HttpClientResponseException e = thrown(Exception)
        e.response.status == HttpStatus.UNAUTHORIZED

        when:
        UserCredentials credentials = new UserCredentials(username: 'pad', password: '123456')
        request = HttpRequest.POST('/gokb/rest/login', credentials)
        HttpResponse<BearerToken> bearerTokenHttpResponse = client.toBlocking().exchange request, BearerToken

        then:
        bearerTokenHttpResponse.status.code == 200
        bearerTokenHttpResponse.body() != null
        def refreshToken = bearerTokenHttpResponse.body().refreshToken
        def accessToken = bearerTokenHttpResponse.body().accessToken

        when:
        request = HttpRequest.GET('/gokb/oauth/access_token')
        request.parameters.add('grant_type', 'refresh_token').add('refresh_token', refreshToken)//.add('access_token'. accessToken)
        bearerTokenHttpResponse = client.toBlocking().exchange request, BearerToken

        then:
        e = thrown(Exception)
        bearerTokenHttpResponse.status.code == 200
        bearerTokenHttpResponse.body().refreshToken
//        accessToken = bearerTokenHttpResponse.body().accessToken

        when:
        request = HttpRequest.GET('/gokb/rest/logout')
        HttpResponse res = client.toBlocking().exchange request.header('access_token', accessToken)

        then:
        res.status.code == 200
        res.body() == null
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
    String username
    @JsonProperty('email')
    String email
    @JsonProperty('curatoryGroups')
    String[] curatoryGroups
}

class CustomError {
    Integer status
    String error
    String message
    String path
}