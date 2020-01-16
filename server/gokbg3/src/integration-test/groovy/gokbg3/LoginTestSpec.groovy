package gokbg3

import geb.spock.GebSpec
import grails.testing.mixin.integration.Integration
import groovy.json.JsonParserType
import groovy.json.JsonSlurper
import org.apache.http.client.entity.EntityBuilder
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients

/**
 * See http://www.gebish.org/manual/current/ for more instructions
 */
@Integration
class LoginTestSpec extends GebSpec {
  private final CloseableHttpClient httpClient = HttpClients.createDefault();

  def setup() {
  }

  def cleanup() {
    httpClient.close()
  }

  void "test valid credentials login"() {
    String accessToken
    CloseableHttpResponse response
    when:
    HttpPost post = new HttpPost("http://localhost:$serverPort/gokb/rest/login")
    post.setEntity(EntityBuilder.create().setText('{"username":"admin","password":"admin"}').build())
    post.addHeader('accept', 'application/json')

    then:
    try {
      response = httpClient.execute(post)
      response.statusLine.statusCode == 200 // OK
      JsonSlurper parser = new JsonSlurper().setType(JsonParserType.LAX)
      BearerToken bearer = parser.parse(response.entity.content)
      bearer != null
      bearer.access_token != null
      accessToken = bearer.access_token;
    } catch (Exception ex) {
      // to see the exception
      ex.printStackTrace()
    }
  }
}

class BearerToken {
  String access_token
  String refresh_token
  List<String> roles
  String username
  String token_type
  int expires_in
}

