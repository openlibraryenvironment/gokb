package gokbg3.rest

import grails.gorm.transactions.Transactional
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import org.gokb.cred.ReviewRequest
import org.gokb.cred.TitleInstance

@Integration
class ReviewsTestSpec extends AbstractAuthSpec {

  private RestBuilder rest = new RestBuilder()
  private ReviewRequest rr
  private TitleInstance title

  @Transactional
  def setup() {
    title = TitleInstance.findOrCreateWhere(name: "testTitle for integration testing").save(flush: true, failOnError: true)
    rr = ReviewRequest.findOrCreateWhere(reviewRequest: "fake review request for integration testing", componentToReview:title)
    rr.save(flush: true, failOnError: true)
  }

  @Transactional
  def cleanup() {
    rr.delete(flush: true)
    title.delete(flush: true)
  }

  void "test GET /rest/reviews with params"() {
    given:
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("$urlPath/rest/reviews") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200
    resp.json.data.size() >= 1
  }
}
