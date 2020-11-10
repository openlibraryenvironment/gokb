package gokbg3.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.RefdataCategory
import org.gokb.cred.Source
import spock.lang.Ignore

@Integration
@Rollback
class SourcesTestSpec extends AbstractAuthSpec {

  private RestBuilder rest = new RestBuilder()

  def setup() {
    def src_upd = Source.findByName("Source PreUpdate") ?: new Source(name: "Source PreUpdate")
    IdentifierNamespace titleNS = IdentifierNamespace.findByName("TestSourceTitleNS") ?: new IdentifierNamespace(
      value: "testsourcetitlenamespace",
      name: "TestSourceTitleNS",
      targetType: RefdataCategory.lookup('IdentifierNamespace.TargetType', 'Title'))
    Source quelle = Source.findByName("TestSource") ?: new Source(name: "TestSource", targetNamespace: titleNS)
  }

  @Transactional
  def cleanup() {
    Source.findByName("Quelle 1")?.expunge()
    Source.findByName("Source PreUpdate")?.expunge()
    Source.findByName("Source AfterUpdate")?.expunge()
    Source.findByName("TestSource")?.expunge()
    IdentifierNamespace.findByName("TestSourceTitleNS")?.delete(flush: true)
  }

  void "test GET /rest/sources"() {
    given:
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("$urlPath/rest/sources?_sort=name&_order=asc&es") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200
    resp.json.data.size() == 8
  }

  void "test GET /rest/sources/{id}"() {
    given:
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    Source quelle = Source.findByName("TestSource")
    RestResponse resp = rest.get("$urlPath/rest/sources/$quelle.id") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200
    resp.json.name == quelle.name
    resp.json.targetNamespace != null
  }

  void "test POST /rest/sources"() {
    given:
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.post("$urlPath/rest/sources") {
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
    def urlPath = getUrlPath()
    def namespace = IdentifierNamespace.findByName("TestSourceTitleNS")
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.put("$urlPath/rest/sources/$srcId") {
      // headers
      accept('application/json')
      contentType('application/json')
      auth("Bearer $accessToken")
      body([
        name           : 'Source AfterUpdate',
        frequency      : '1M',
        url            : "http://kbart-source.com/test-pkg",
        targetNamespace: namespace.id
      ] as JSON)
    }
    then:
    resp.status == 200
    resp.json.name == "Source AfterUpdate"
    resp.json.frequency == "1M"
    resp.json.url == "http://kbart-source.com/test-pkg"
    resp.json.targetNamespace.name == "TestSourceTitleNS"
    resp.json.automaticUpdates == false
  }
}
