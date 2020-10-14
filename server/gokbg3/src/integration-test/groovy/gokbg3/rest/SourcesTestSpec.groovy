package gokbg3.rest

import grails.converters.JSON
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

  def setup() {
    def src_upd = Source.findByName("Source PreUpdate") ?: new Source(name: "Source PreUpdate")
  }

  @Transactional
  def cleanup() {
    Source.findByName("Quelle 1")?.expunge()
    Source.findByName("Source PreUpdate")?.expunge()
    Source.findByName("Source AfterUpdate")?.expunge()
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
    resp.json.data.size() == 8
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
      body([shortcode: 'q1', name: 'Quelle 1'] as JSON)
    }
    then:
    resp.status == 201
    resp.json.name == "Quelle 1"
  }

  void "test PUT /rest/sources/{id}"() {
    given:
    def srcId = Source.findByName("Source PreUpdate")?.id
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.put("http://localhost:$serverPort/gokb/rest/sources/$srcId") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body([name: 'Source AfterUpdate', frequency: '1M', url: "http://kbart-source.com/test-pkg"] as JSON)
    }
    then:
    resp.status == 200
    resp.json.name == "Source AfterUpdate"
    resp.json.frequency == "1M"
    resp.json.url == "http://kbart-source.com/test-pkg"
  }
}
