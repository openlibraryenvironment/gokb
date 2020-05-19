package gokbg3.rest

import grails.gorm.transactions.Transactional
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.Source
import spock.lang.Ignore

@Integration
@Rollback
class SourcesTestSpec extends AbstractAuthSpec {

  private RestBuilder rest = new RestBuilder()

  @Transactional
  def cleanup(){
    Source.findByName("Quelle 1")?.expunge()
  }

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
    Source quelle = Source.findByName("WILEY")
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/sources/$quelle.id") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200
    resp.json.name == quelle.name
  }

  void "test POST /rest/sources"() {
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.post("http://localhost:$serverPort/gokb/rest/sources") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body("{data:{shortcode: 'q1', name: 'Quelle 1'}}")
    }
    then:
    resp.status == 200
    resp.json.data.name == "Quelle 1"
  }
}