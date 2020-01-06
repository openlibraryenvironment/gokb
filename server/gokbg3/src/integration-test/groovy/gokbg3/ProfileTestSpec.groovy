package gokbg3

import grails.testing.mixin.integration.Integration
import groovy.json.JsonParserType
import groovy.json.JsonSlurper
import org.apache.http.client.entity.EntityBuilder
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import spock.lang.Specification

@Integration
class ProfileTestSpec extends Specification {
  private final CloseableHttpClient httpClient = HttpClients.createDefault();

  def setup() {
  }

  def cleanup() {
    httpClient.close()
  }

  void "test valid credentials profile"() {
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
      def bearer = parser.parse(response.entity.content)
      bearer != null
      bearer.access_token != null
      accessToken = bearer.access_token;
    } catch (Exception ex) {
      // to see the exception
      ex.printStackTrace()
    }

    when:
    HttpGet get = new HttpGet("http://localhost:$serverPort/gokb/rest/profile");
    // add request headers
    get.addHeader('accept', 'application/json')
    get.addHeader("authorization", "bearer $accessToken")

    then:
    try {
      response = httpClient.execute(get)
      response.statusLine.statusCode == 200 // OK
      JsonSlurper parser = new JsonSlurper().setType(JsonParserType.LAX)
      UserProfile profile = parser.parse(response.entity.content)
      profile.roles.count > 0
      profile.username == "admin"
    } catch (Exception ex) {
      ex.printStackTrace()
    }

    when:
    post = new HttpPost("http://localhost:$serverPort/gokb/rest/logout")
    post.addHeader('accept', 'application/json')
    post.addHeader("authorization", "bearer $accessToken")

    then:
    try {
      response = httpClient.execute(post)
      response.statusLine.statusCode == 200 // OK
    } catch (Exception ex) {
      ex.printStackTrace()
    }

    when:
    get = new HttpGet("http://localhost:$serverPort/gokb/rest/profile");
    // add request headers
    get.addHeader('accept', 'application/json')
    get.addHeader("authorization", "bearer $accessToken")

    then:
    try {
      response = httpClient.execute(get)
      response.statusLine.statusCode == 401 // Unauthorized
      JsonSlurper parser = new JsonSlurper().setType(JsonParserType.LAX)
      UserProfile profile = parser.parse(response.entity.content)
      profile.username == "admin"
      profile.curatoryGroups == null
    } catch (Exception ex) {
      ex.printStackTrace()
    }
  }
}

class UserProfile {
  int id
  String username
  String displayName
  String email
  Collection<Map<String, Object>> curatoryGroups
  boolean enabled
  boolean accountLocked
  boolean accountExpired
  boolean passwordExpired
  int defaultPageSize
}
