package gokbg3

import grails.converters.JSON
import grails.core.GrailsApplication
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.JournalInstance
import org.gokb.cred.Package
import org.gokb.cred.Platform
import org.gokb.cred.TitleInstancePackagePlatform
import spock.lang.Specification
import groovyx.net.http.HTTPBuilder

@Integration
@Rollback
class PackageExportSpec extends Specification {

  GrailsApplication grailsApplication
  RestBuilder rest = new RestBuilder()

  JournalInstance journal1, journal2, journal3
  Package pack1, pack2
  Platform plt

  def setup() {
    journal1 = new JournalInstance(name: "journal1")
    journal2 = new JournalInstance(name: "journal2")
    journal3 = new JournalInstance(name: "journal3")
    pack1 = new Package(name: "package1")
    pack2 = new Package(name: "package2")
    plt = new Platform(name: "platform")

    TitleInstancePackagePlatform tipp1 = new TitleInstancePackagePlatform(hostPlatform: plt, pkg: pack1, title: journal1, accessStartDate: new Date())
    TitleInstancePackagePlatform tipp2 = new TitleInstancePackagePlatform(hostPlatform: plt, pkg: pack1, title: journal2, accessStartDate: new Date())
    TitleInstancePackagePlatform tipp3 = new TitleInstancePackagePlatform(hostPlatform: plt, pkg: pack2, title: journal3, accessStartDate: new Date())
    TitleInstancePackagePlatform tipp4 = new TitleInstancePackagePlatform(hostPlatform: plt, pkg: pack2, title: journal2, accessStartDate: new Date())
  }

  def cleanup() {
/*    plt.delete(flush:true)
    pack1.delete(flush:true)
    pack2.delete(flush:true)
    journal1.delete(flush:true)
    journal2.delete(flush:true)
    journal3.delete(flush:true)*/
  }

  private String getUrlPath() {
    return "http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}".toString()
  }

  void "test GET /packages/kbart/<uuid>"() {
    given:
    def urlPath = getUrlPath()

    when:
    RestResponse resp = rest.get("${urlPath}/packages/kbart/${pack1.uuid}")

    then:
    resp.status == 200 // OK
  }

  void "test POST /packages/packageTSVExport/"() {
    given:
    def urlPath = getUrlPath()
    def ids = [
      data: [ids: [pack1.uuid, pack2.uuid]]
    ]

    when:
    RestResponse resp = rest.post("${urlPath}/packages/packageTSVExport/") {
      body(ids as JSON)
    }

    then:
    resp.status == 200 // OK
  }
}

