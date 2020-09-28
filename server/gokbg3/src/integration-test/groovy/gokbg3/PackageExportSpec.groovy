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
  TitleInstancePackagePlatform tipp1, tipp2, tipp3, tipp4

  def setup() {
    journal1 = JournalInstance.findByName("journal1") ?: new JournalInstance(name: "journal1")
    journal2 = JournalInstance.findByName("journal2") ?: new JournalInstance(name: "journal2")
    journal3 = JournalInstance.findByName("journal3") ?: new JournalInstance(name: "journal3")
    pack1 = Package.findByName("package1") ?: new Package(name: "package1")
    pack2 = Package.findByName("package2") ?: new Package(name: "package2")
    plt = Platform.findByName("platform") ?: new Platform(name: "platform")

    tipp1 = new TitleInstancePackagePlatform(hostPlatform: plt, pkg: pack1, title: journal1, accessStartDate: new Date())
    tipp2 = new TitleInstancePackagePlatform(hostPlatform: plt, pkg: pack1, title: journal2, accessStartDate: new Date())
    tipp3 = new TitleInstancePackagePlatform(hostPlatform: plt, pkg: pack2, title: journal3, accessStartDate: new Date())
    tipp4 = new TitleInstancePackagePlatform(hostPlatform: plt, pkg: pack2, title: journal2, accessStartDate: new Date())
  }

  def cleanup() {
    tipp1.expunge()
    tipp2.expunge()
    tipp3.expunge()
    tipp4.expunge()
    journal1.expunge()
    journal2.expunge()
    journal3.expunge()
    pack1.expunge()
    pack2.expunge()
    plt.expunge()
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

