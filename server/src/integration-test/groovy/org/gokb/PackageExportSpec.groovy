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

import org.gokb.*
import org.gokb.cred.*

import spock.lang.Specification
import spock.lang.Shared


@Integration
@Rollback
class PackageExportSpec extends Specification {

  GrailsApplication grailsApplication

  BlockingHttpClient http

  @Autowired
  TippUpsertService tippUpsertService


  def setup() {
    if (!http) {
      http = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }

    Org org = Org.findByName("PackageExportOrg") ?: new Org(name: "PackageExportOrg").save(flush:true, failOnError: true)
    Platform plt = Platform.findByName("PackageExportPlatform") ?: new Platform(name: "PackageExportPlatform", provider: org).save(flush:true, failOnError: true)

    JournalInstance journal1 = JournalInstance.findByName("PackageExportJournal1") ?: new JournalInstance(name: "PackageExportJournal1").save(flush:true, failOnError: true)
    JournalInstance journal2 = JournalInstance.findByName("PackageExportJournal2") ?: new JournalInstance(name: "PackageExportJournal2").save(flush:true, failOnError: true)
    JournalInstance journal3 = JournalInstance.findByName("PackageExportJournal3") ?: new JournalInstance(name: "PackageExportJournal3").save(flush:true, failOnError: true)
    Package pack1 = Package.findByName("Packageexportpackage1") ?: new Package(name: "Packageexportpackage1", nominalPlatform: plt, provider: org).save(flush:true, failOnError: true)
    Package pack2 = Package.findByName("Packageexportpackage2") ?: new Package(name: "Packageexportpackage2", nominalPlatform: plt, provider: org).save(flush:true, failOnError: true)

    TitleInstancePackagePlatform.findByName("PackageExportJournalTIPP1") ?: new TitleInstancePackagePlatform(name: "PackageExportJournalTIPP1", hostPlatform: plt, pkg: pack1, title: journal1, accessStartDate: new Date(), importId: "pkgexporttipp1", url: "http://testexporttipp1.de").save(flush:true, failOnError: true)
    TitleInstancePackagePlatform.findByName("PackageExportJournalTIPP2") ?: new TitleInstancePackagePlatform(name: "PackageExportJournalTIPP2", hostPlatform: plt, pkg: pack1, title: journal2, accessStartDate: new Date(), importId: "pkgexporttipp2", url: "http://testexporttipp2.de").save(flush:true, failOnError: true)
    TitleInstancePackagePlatform.findByName("PackageExportJournalTIPP3") ?: new TitleInstancePackagePlatform(name: "PackageExportJournalTIPP3", hostPlatform: plt, pkg: pack2, title: journal3, accessStartDate: new Date(), importId: "pkgexporttipp3", url: "http://testexporttipp3.de").save(flush:true, failOnError: true)
    TitleInstancePackagePlatform.findByName("PackageExportJournalTIPP4") ?: new TitleInstancePackagePlatform(name: "PackageExportJournalTIPP4", hostPlatform: plt, pkg: pack2, title: journal2, accessStartDate: new Date(), importId: "pkgexporttipp4", url: "http://testexporttipp4.de").save(flush:true, failOnError: true)
  }

  def cleanup() {
    TitleInstancePackagePlatform.findByName("PackageExportJournalTIPP1")?.expunge()
    TitleInstancePackagePlatform.findByName("PackageExportJournalTIPP2")?.expunge()
    TitleInstancePackagePlatform.findByName("PackageExportJournalTIPP3")?.expunge()
    TitleInstancePackagePlatform.findByName("PackageExportJournalTIPP4")?.expunge()

    Package.findByName("Packageexportpackage1")?.expunge()
    Package.findByName("Packageexportpackage2")?.expunge()

    JournalInstance.findByName("PackageExportJournal1")?.expunge()
    JournalInstance.findByName("PackageExportJournal2")?.expunge()
    JournalInstance.findByName("PackageExportJournal3")?.expunge()
    Platform.findByName("PackageExportPlatform")?.expunge()
    Org.findByName("PackageExportOrg")?.expunge()
  }

  private String getUrlPath() {
    return "http://localhost:${serverPort}${grailsApplication.config.getProperty('server.servlet.context-path') ?: ''}".toString()
  }

  void "test GET /packages/kbart/<uuid>"() {
    given:
    def urlPath = getUrlPath()
    Package pack1 = Package.findByName("Packageexportpackage1")

    when:
    sleep(2000)
    HttpRequest request = HttpRequest.GET("${urlPath}/packages/kbart/${pack1.uuid}")
    HttpResponse resp = http.exchange(request, String)

    then:
    resp.status == HttpStatus.OK
    resp.body()?.contains("PackageExportJournalTIPP1")
    resp.body()?.contains("PackageExportJournalTIPP2")
    resp.header("Content-Disposition") == "attachment; filename=\"Packageexportorg_Global_Packageexportpackage1_${new SimpleDateFormat("yyyy-MM-dd").format(new Date())}.txt\""
  }

  void "test multiple results /packages/packageTSVExport/"() {
    given:
    def urlPath = getUrlPath()
    Package pack1 = Package.findByName("Packageexportpackage1")
    Package pack2 = Package.findByName("Packageexportpackage2")

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
    resp.header("Content-Disposition") == "attachment; filename=\"gokbExport.zip\""
    resp.body().contains("GoKBPackage-" + pack1.id + "_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".txt")
    resp.body().contains("GoKBPackage-" + pack2.id + "_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".txt")
  }


  void "test POST /packages/packageTSVExport/"() {
    Package pack1 = Package.findByName("Packageexportpackage1")
    Package pack2 = Package.findByName("Packageexportpackage2")
    Map ids = [
      data: [ids: [pack1.uuid, pack2.uuid]]
    ]

    when:
    HttpRequest request = HttpRequest.POST("${urlPath}/packages/packageTSVExport/", ids)
    HttpResponse resp = http.exchange(request, String)

    then:
    resp.status == HttpStatus.OK
    resp.header("Content-Disposition") == "attachment; filename=\"gokbExport.zip\""
    resp.body().contains("GoKBPackage-" + pack1.id + "_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".txt")
    resp.body().contains("GoKBPackage-" + pack2.id + "_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".txt")
  }
}
