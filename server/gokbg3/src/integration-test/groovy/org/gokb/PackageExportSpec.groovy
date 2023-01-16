package org.gokb

import grails.core.GrailsApplication
import grails.gorm.transactions.*
import grails.testing.mixin.integration.Integration

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.uri.UriBuilder

import java.text.SimpleDateFormat

import org.gokb.cred.JournalInstance
import org.gokb.cred.Package
import org.gokb.cred.Platform
import org.gokb.cred.TitleInstancePackagePlatform

import spock.lang.Specification
import spock.lang.Shared


@Integration
@Rollback
class PackageExportSpec extends Specification {

  GrailsApplication grailsApplication

  BlockingHttpClient http

  JournalInstance journal1, journal2, journal3
  Package pack1, pack2
  Platform plt
  TitleInstancePackagePlatform tipp1, tipp2, tipp3, tipp4

  def setup() {
    if (!http) {
      http = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }

    journal1 = JournalInstance.findByName("PackageExportJournal1") ?: new JournalInstance(name: "PackageExportJournal1").save(flush:true)
    journal2 = JournalInstance.findByName("PackageExportJournal2") ?: new JournalInstance(name: "PackageExportJournal2").save(flush:true)
    journal3 = JournalInstance.findByName("PackageExportJournal3") ?: new JournalInstance(name: "PackageExportJournal3").save(flush:true)
    pack1 = Package.findByName("Packageexportpackage1") ?: new Package(name: "Packageexportpackage1").save(flush:true)
    pack2 = Package.findByName("Packageexportpackage2") ?: new Package(name: "Packageexportpackage2").save(flush:true)
    plt = Platform.findByName("PackageExportPlatform") ?: new Platform(name: "PackageExportPlatform").save(flush:true)

    tipp1 = new TitleInstancePackagePlatform(hostPlatform: plt, pkg: pack1, title: journal1, accessStartDate: new Date(), importId: "pkgexporttipp1").save(flush:true)
    tipp2 = new TitleInstancePackagePlatform(hostPlatform: plt, pkg: pack1, title: journal2, accessStartDate: new Date(), importId: "pkgexporttipp2").save(flush:true)
    tipp3 = new TitleInstancePackagePlatform(hostPlatform: plt, pkg: pack2, title: journal3, accessStartDate: new Date(), importId: "pkgexporttipp3").save(flush:true)
    tipp4 = new TitleInstancePackagePlatform(hostPlatform: plt, pkg: pack2, title: journal2, accessStartDate: new Date(), importId: "pkgexporttipp4").save(flush:true)
  }

  def cleanup() {
    pack1?.expunge()
    pack2?.expunge()
    tipp1?.expunge()
    tipp2?.expunge()
    tipp3?.expunge()
    tipp4?.expunge()
    journal1?.expunge()
    journal2?.expunge()
    journal3?.expunge()
    plt?.expunge()
  }

  private String getUrlPath() {
    return "http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}".toString()
  }

  void "test GET /packages/kbart/<uuid>"() {
    given:
    def urlPath = getUrlPath()

    when:
    HttpRequest request = HttpRequest.GET("${urlPath}/packages/kbart/${pack1.uuid}")
    HttpResponse resp = http.exchange(request, String)

    then:
    resp.status == HttpStatus.OK
    resp.body().contains("PackageExportJournal1")
    resp.body().contains("PackageExportJournal2")
    resp.header("Content-Disposition") == "attachment; filename=\"UnknownProvider_${pack1.global.value}_${pack1.name}_${new SimpleDateFormat("yyyy-MM-dd").format(new Date())}.tsv\""
  }

  void "test multiple results /packages/packageTSVExport/"() {
    given:
    def urlPath = getUrlPath()

    when:
    URI uri = UriBuilder.of(urlPath)
      .path("/packages/packageTSVExport")
      .queryParam("pkg", pack1.uuid)
      .queryParam("pkg", pack2.uuid)
      .build()

    HttpRequest request = HttpRequest.GET(uri)
    HttpResponse resp = http.exchange(request, String)

    then:
    resp.status == HttpStatus.OK
    resp.body().contains("GoKBPackage-" + pack1.id + "_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".tsv")
    resp.body().contains("GoKBPackage-" + pack2.id + "_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".tsv")
    resp.header("Content-Disposition") == "attachment; filename=\"gokbExport.zip\""
  }


  void "test POST /packages/packageTSVExport/"() {
    Map ids = [
      data: [ids: [pack1.uuid, pack2.uuid]]
    ]

    when:
    HttpRequest request = HttpRequest.POST("${urlPath}/packages/packageTSVExport/", ids)
    HttpResponse resp = http.exchange(request, String)

    then:
    resp.status == HttpStatus.OK
    resp.body().contains("GoKBPackage-" + pack1.id + "_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".tsv")
    resp.body().contains("GoKBPackage-" + pack2.id + "_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".tsv")
    resp.header("Content-Disposition") == "attachment; filename=\"gokbExport.zip\""
  }
}

