package gokbg3.rest

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.User

@Integration
@Rollback
class CuratoryGroupsTestSpec extends AbstractAuthSpec {

  def group1, group2, group3, group4, user

  def setup() {
    group1 = CuratoryGroup.findByName("Curatory Group A") ?: new CuratoryGroup(name: "Curatory Group A").save(flush: true)
    group2 = CuratoryGroup.findByName("Curatory Group B") ?: new CuratoryGroup(name: "Curatory Group B").save(flush: true)
    group3 = CuratoryGroup.findByName("Curatory Group C") ?: new CuratoryGroup(name: "Curatory Group C").save(flush: true)
    group4 = CuratoryGroup.findByName("Curatory Group D") ?: new CuratoryGroup(name: "Curatory Group D").save(flush: true)
    user = User.findByUsername("groupUser")?:new User(username:"groupUser", password: "groupUser", enabled: true).save(flush:true)
  }

  def cleanup() {
    CuratoryGroup.findByName("Curatory Group A")?.expunge()
    CuratoryGroup.findByName("Curatory Group B")?.expunge()
    CuratoryGroup.findByName("Curatory Group C")?.expunge()
    CuratoryGroup.findByName("Curatory Group D")?.expunge()
    User.findByUsername("groupUser")?.delete(flush:true)
  }

  private RestBuilder rest = new RestBuilder()

  void "test GET /rest/curatoryGroups/{id}"() {
    def urlPath = getUrlPath()
    when:
    String token = getAccessToken("groupUser", "groupUser")
      RestResponse resp = rest.get("${urlPath}/rest/curatoryGroups/${group1.id}") {
      // headers
      accept('application/json')
        auth("Bearer $token")
      }
    then:
    resp.status == 200
    resp.json.data.name == group1.name
  }

  void "test GET /rest/curatoryGroups"() {
    def urlPath = getUrlPath()
    when:
    String token = getAccessToken("groupUser", "groupUser")
    RestResponse resp = rest.get("${urlPath}/rest/curatoryGroups?name=curatory") {
      // headers
      accept('application/json')
      auth("Bearer $token")
    }
    then:
    resp.status == 200
    resp.json.data.size() == 5
  }

  void "test GET /rest/curatoryGroups with inverse sorting by name"() {
    def urlPath = getUrlPath()
    when:
    String token = getAccessToken("groupUser", "groupUser")
    RestResponse resp = rest.get("${urlPath}/rest/curatoryGroups?_sort=name&_order=desc") {
      // headers
      accept('application/json')
      auth("Bearer $token")
    }
    then:
    resp.status == 200
    resp.json.data[4].id == group1.id
  }
}