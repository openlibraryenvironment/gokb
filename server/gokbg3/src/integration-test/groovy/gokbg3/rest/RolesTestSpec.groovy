package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.CuratoryGroup

@Integration
@Rollback
class RolesTestSpec extends AbstractAuthSpec {

  private RestBuilder rest = new RestBuilder()

  void "test GET /rest/roles without token"() {
    when:
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/roles") {
      // headers
      accept('application/json')
    }
    then:
    resp.status == 200
    resp.json.data.size() == 6
  }
}