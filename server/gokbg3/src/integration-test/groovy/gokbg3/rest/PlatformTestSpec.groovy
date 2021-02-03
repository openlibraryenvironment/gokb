package gokbg3.rest

import grails.converters.JSON
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.Org
import org.gokb.cred.Platform
import org.gokb.TitleLookupService

@Integration
@Rollback
class PlatformTestSpec extends AbstractAuthSpec {

  private RestBuilder rest = new RestBuilder()

  def setupSpec(){
  }

  def setup() {
    def new_prov = Org.findByName("TestPltProvider") ?: new Org(name: "TestPltProvider").save(flush:true)
    def upd_prov = Org.findByName("TestPltProviderUpd") ?: new Org(name: "TestPltProviderUpd").save(flush:true)
    def upd_plt = Platform.findByName("TestPltUpd") ?: new Platform(name: "TestPltUpd").save(flush:true)
  }

  def cleanup() {
    if (Org.findByName("TestPltProvider")) {
      Org.findByName("TestPltProvider")?.refresh().expunge()
    }
    if (Org.findByName("TestPltProviderUpd")) {
      Org.findByName("TestPltProviderUpd")?.refresh().expunge()
    }
    if (Platform.findByName("TestPltUpd")) {
      Platform.findByName("TestPltUpd")?.refresh().expunge()
    }
  }

  void "test /rest/platforms without token"() {
    given:

    def urlPath = getUrlPath()

    when:

    RestResponse resp = rest.get("${urlPath}/rest/platforms") {
      accept('application/json')
    }

    then:

    resp.status == 200 // OK
  }

  void "test /rest/platforms/<id> with valid token"() {
    given:
  
    def urlPath = getUrlPath()
    String accessToken = getAccessToken()

    when:

    RestResponse resp = rest.get("${urlPath}/rest/platforms") {
      accept('application/json')
      auth("Bearer $accessToken")
    }

    then:

    resp.status == 200 // OK
  }

  void "test insert new platform"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()
    def provider = Org.findByName("TestPltProvider")
    def json_record = [
      name: "TestPltPost",
      primaryUrl: "http://newplt.com",
      provider: provider.id
    ]

    when:

    RestResponse resp = rest.post("${urlPath}/rest/platforms") {
      accept('application/json')
      auth("Bearer $accessToken")
      body(json_record as JSON)
    }

    then:

    resp.status == 201 // Created

    expect:

    resp.json?.name == "TestPltPost"
    resp.json?.provider?.name == "TestPltProvider"
  }

  void "test platform index"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()

    when:

    RestResponse resp = rest.get("${urlPath}/rest/platforms") {
      accept('application/json')
      auth("Bearer $accessToken")
    }

    then:

    resp.status == 200 // OK

    expect:

    resp.json?.data?.size() > 0
  }

  void "test platform update"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()
    def id = Platform.findByName("TestPltUpd").id
    def new_prov = Org.findByName("TestPltProviderUpd")

    def json_record = [
      name: "TestPltUpdate",
      primaryUrl: "http://updatedplt.com",
      provider: new_prov.id
    ]

    when:

    RestResponse resp = rest.put("${urlPath}/rest/platforms/$id") {
      accept('application/json')
      auth("Bearer $accessToken")
      body(json_record as JSON)
    }

    then:

    resp.status == 200

    expect:

    resp.json.name == "TestPltUpdate"
    resp.json.primaryUrl == "http://updatedplt.com"
    resp.json.provider?.name == "TestPltProviderUpd"
  }
}
