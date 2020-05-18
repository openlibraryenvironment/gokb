package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration

@Integration
class ReviewsTestSpec extends AbstractAuthSpec {

  private RestBuilder rest = new RestBuilder()

  void "test GET /rest/reviews with params"() {
    // use the bearerToken to write to /authRest/user
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/reviews") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200
  }
}
