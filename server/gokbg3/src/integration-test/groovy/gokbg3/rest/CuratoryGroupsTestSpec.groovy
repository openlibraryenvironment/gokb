package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.Role
import org.gokb.cred.User

@Integration
@Rollback
class CuratoryGroupsTestSpec extends AbstractAuthSpec {

  def group1
  def group2

  def setup() {
    group1 = CuratoryGroup.findByName("Curatory Group A") ?: new CuratoryGroup(name: "Curatory Group A").save(flush: true)
    group2 = CuratoryGroup.findByName("Curatory Group B") ?: new CuratoryGroup(name: "Curatory Group B").save(flush: true)
  }

  private RestBuilder rest = new RestBuilder()

  void "test GET /rest/group/{id} without token"() {
    when:
    RestResponse resp = rest.get("http://localhost:$serverPort/gokb/rest/group/$group1.id") {
      // headers
      accept('application/json')
    }
    then:
    log.debug(resp)
    resp.status == 200
    resp.json.data.name == group1.name
  }
}