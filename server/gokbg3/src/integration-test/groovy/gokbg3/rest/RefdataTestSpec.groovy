package gokbg3.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.Source

@Integration
@Rollback
class RefdataTestSpec extends AbstractAuthSpec {

  private RestBuilder rest = new RestBuilder()

  void "test GET /rest/package-scopes"() {
    given:
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("$urlPath/rest/package-scopes") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200
    resp.json.data.size() >= 4
    resp.json._links.self.href.contains ("/rest/package-scopes")
  }

  void "test GET /rest/coverage-depth"() {
    given:
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("$urlPath/rest/coverage-depth") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200
    resp.json.data.size() == 3
    resp.json._links.self.href.contains ("/rest/coverage-depth")
  }
}