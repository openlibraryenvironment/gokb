package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback

@Integration
@Rollback
class SourcesTestSpec extends AbstractAuthSpec {

  private RestBuilder rest = new RestBuilder()

  void "test GET /rest/sources"() {
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/sources?_sort=name&_order=asc&es") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200
    resp.json.data.size() == 6
  }

  void "test GET /rest/sources/{id}"() {
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/sources/3794") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200
    resp.json.id == 3794
  }

  void "test POST /rest/sources"() {
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/sources/3794") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200
    resp.json.id == 3794
  }
}