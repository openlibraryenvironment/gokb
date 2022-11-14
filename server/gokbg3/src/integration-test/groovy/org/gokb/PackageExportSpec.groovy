package org.gokb

import grails.converters.JSON
import grails.core.GrailsApplication
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.*
import org.gokb.cred.JournalInstance
import org.gokb.cred.Package
import org.gokb.cred.Platform
import org.gokb.cred.TitleInstancePackagePlatform
import spock.lang.Specification
import spock.lang.Shared
import groovyx.net.http.HTTPBuilder

import java.text.SimpleDateFormat

@Integration
@Rollback
class PackageExportSpec extends Specification {

  GrailsApplication grailsApplication

  HttpClient http

  JournalInstance journal1, journal2, journal3
  Package pack1, pack2
  Platform plt
  TitleInstancePackagePlatform tipp1, tipp2, tipp3, tipp4

  def setup() {
    journal1 = JournalInstance.findByName("journal1") ?: new JournalInstance(name: "journal1").save(flush:true)
    journal2 = JournalInstance.findByName("journal2") ?: new JournalInstance(name: "journal2").save(flush:true)
    journal3 = JournalInstance.findByName("journal3") ?: new JournalInstance(name: "journal3").save(flush:true)
    pack1 = Package.findByName("Package1") ?: new Package(name: "Package1").save(flush:true)
    pack2 = Package.findByName("Package2") ?: new Package(name: "Package2").save(flush:true)
    plt = Platform.findByName("Platform") ?: new Platform(name: "Platform").save(flush:true)

    tipp1 = new TitleInstancePackagePlatform(hostPlatform: plt, pkg: pack1, title: journal1, accessStartDate: new Date()).save(flush:true)
    tipp2 = new TitleInstancePackagePlatform(hostPlatform: plt, pkg: pack1, title: journal2, accessStartDate: new Date()).save(flush:true)
    tipp3 = new TitleInstancePackagePlatform(hostPlatform: plt, pkg: pack2, title: journal3, accessStartDate: new Date()).save(flush:true)
    tipp4 = new TitleInstancePackagePlatform(hostPlatform: plt, pkg: pack2, title: journal2, accessStartDate: new Date()).save(flush:true)
  }

  def cleanup() {
    pack1.expunge()
    pack2.expunge()
    tipp1.expunge()
    tipp2.expunge()
    tipp3.expunge()
    tipp4.expunge()
    journal1.expunge()
    journal2.expunge()
    journal3.expunge()
    plt.expunge()
  }

  private String getUrlPath() {
    return "http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}".toString()
  }

  void "test GET /packages/kbart/<uuid>"() {
    given:
    def urlPath = getUrlPath()

    when:
    HttpRequest request = HttpRequest.GET("${urlPath}/packages/kbart/${pack1.uuid}")
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.OK
    resp.body().contains("journal1")
    resp.body().contains("journal2")
    resp.headers["Content-Disposition"] == ["attachment; filename=\"UnknownProvider_${pack1.global.value}_${pack1.name}_${new SimpleDateFormat("yyyy-MM-dd").format(new Date())}.tsv\""]
  }

  void "test multiple results /packages/packageTSVExport/"() {
    given:
    def urlPath = getUrlPath()

    when:
    HttpRequest request = HttpRequest.GET("${urlPath}/packages/packageTSVExport?pkg=${pack1.uuid}&pkg=${pack2.uuid}")
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.OK
    resp.body().contains("GoKBPackage-" + pack1.id + "_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".tsv")
    resp.body().contains("GoKBPackage-" + pack2.id + "_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".tsv")
    resp.headers["Content-Disposition"] == ["attachment; filename=\"gokbExport.zip\""]
  }


  void "test POST /packages/packageTSVExport/"() {
    def ids = [
      data: [ids: [pack1.uuid, pack2.uuid]]
    ]

    when:
    HttpRequest request = HttpRequest.POST("${urlPath}/packages/packageTSVExport/", ids as JSON)
    HttpResponse resp = http.toBlocking().exchange(request)

    then:
    resp.status == HttpStatus.OK
    resp.body().contains("GoKBPackage-" + pack1.id + "_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".tsv")
    resp.body().contains("GoKBPackage-" + pack2.id + "_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".tsv")
    resp.headers["Content-Disposition"] == ["attachment; filename=\"gokbExport.zip\""]
  }
}

