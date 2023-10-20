package org.gokb

import grails.core.GrailsApplication
import grails.gorm.transactions.*
import grails.testing.mixin.integration.Integration

import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.uri.UriBuilder

import org.springframework.beans.factory.annotation.*
import org.springframework.web.context.WebApplicationContext
import org.gokb.cred.*

import spock.lang.Specification
import spock.lang.Shared


@groovy.util.logging.Slf4j
@Integration
@Rollback
class OaiSpec extends Specification {

  GrailsApplication grailsApplication


  BlockingHttpClient http

  @Autowired
  WebApplicationContext ctx

  //test data
  JournalInstance title1
  TitleInstancePackagePlatform tipp1
  Package test_pkg
  Platform test_plt
  Org test_org

  private String getUrlPath() {
    return "http://localhost:${serverPort}${grailsApplication.config.getProperty('server.servlet.context-path') ?: ''}".toString()
  }

  def setup() {
    if (!http) {
      http = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }

    def http = RefdataCategory.lookup('Source.DataSupplyMethod', 'HTTP Url').save(flush: true)
    def kbart = RefdataCategory.lookup('Source.DataFormat', 'KBART').save(flush: true)
    def freq = RefdataCategory.lookup('Source.Frequency', 'Weekly').save(flush: true)
    IdentifierNamespace ttl_ns = IdentifierNamespace.findByName('Test Title NS') ?: new IdentifierNamespace(name: 'Test Title NS', value: 'titleNStest')
    IdentifierNamespace pkg_ns = IdentifierNamespace.findByName('Test Package NS') ?: new IdentifierNamespace(name: 'Test Package NS', value: 'packageNStest')

    test_plt = Platform.findByName('Test Platform') ?: new Platform(name: 'Test Platform').save(flush: true)

    test_org = Org.findByName("OAI Test Org") ?: new Org(
      name: 'OAI Test Org',
      titleNamespace: ttl_ns,
      packageNamespace: pkg_ns
    )

    Source testSource = Source.findByName("PackTestSource") ?: new Source(
      name: "PackTestSource",
      url: "https://org/package",
      frequency: freq,
      defaultSupplyMethod: http,
      defaultDataFormat: kbart).save(flush: true)

    test_pkg = Package.findByName('OAI Test Package 1')

    if (!test_pkg) {
      test_pkg = new Package(name: 'OAI Test Package 1')
      test_pkg.source = testSource
      test_pkg.save(flush: true)

      test_pkg.curatoryGroups << CuratoryGroup.findByName('Local')
      test_pkg.save(flush: true)
    }

    if (test_pkg.ids?.size() == 0) {
      test_pkg.provider = test_org
      test_pkg.nominalPlatform = test_plt
      Identifier isil = Identifier.findByValue('ZDB-1-OAIT') ?: new Identifier(
        value: 'ZDB-1-OAIT',
        namespace: IdentifierNamespace.findByValue('isil')).save(flush: true)
      test_pkg.ids << isil
      test_pkg.save(flush: true)
    }

    title1 = JournalInstance.findByName('Test Title 1') ?: new JournalInstance(name: 'Test Title 1',
      series: 'Test Series Name').save(flush: true)

    Identifier eissn1 = Identifier.findByValue('1234-3456') ?: new Identifier(value: '1234-3456',
      namespace: IdentifierNamespace.findByValue('eissn')).save(flush: true)
    Identifier issn1 = Identifier.findByValue('1234-4567') ?: new Identifier(value: '1234-4567',
      namespace: IdentifierNamespace.findByValue('issn')).save(flush: true)

    title1.ids.addAll([eissn1, issn1])
    title1.save(flush: true)

    tipp1 = TitleInstancePackagePlatform.findByName('OaiTestTIPP')

    if (!tipp1) {
      tipp1 = new TitleInstancePackagePlatform(name: 'OaiTestTIPP').save(flush: true)
      tipp1.setPrice("list", "1234.56 EUR")
      tipp1.publisherName = "test Publisher"
      tipp1.accessStartDate = new Date()

      new Combo(fromComponent: test_pkg,
        toComponent: tipp1,
        type: RefdataCategory.lookup('Combo.Type', 'Package.Tipps'),
        status: RefdataCategory.lookup('Combo.Status', 'Active')
      ).save(flush: true)

      new Combo(fromComponent: test_plt,
        toComponent: tipp1,
        type: RefdataCategory.lookup('Combo.Type', 'Platform.HostedTipps'),
        status: RefdataCategory.lookup('Combo.Status', 'Active')
      ).save(flush: true)

      new Combo(fromComponent: title1,
        toComponent: tipp1,
        type: RefdataCategory.lookup('Combo.Type', 'TitleInstance.Tipps'),
        status: RefdataCategory.lookup('Combo.Status', 'Active')
      ).save(flush: true)

      tipp1.ids.addAll([eissn1, issn1])

      def coverageStatement = [
        startDate: new Date(),
        startVolume: "1",
        startIssue: "1"
      ]

      tipp1.addToCoverageStatements(coverageStatement)
      tipp1.save(flush: true)
    }
  }

  def cleanup() {
    TitleInstancePackagePlatform.findByName('OaiTestTIPP')?.expunge()
    JournalInstance.findByName('Test Title 1')?.expunge()
    Package.findByName('OAI Test Package 1')?.expunge()
    Source.findByName("PackTestSource")?.expunge()
    Platform.findByName('Test Platform')?.expunge()
    Org.findByName('OAI Test Org')?.expunge()
    Identifier.findByValue('1234-3456')?.expunge()
    Identifier.findByValue('1234-4567')?.expunge()
    Identifier.findByValue('ZDB-1-OAIT')?.expunge()
  }

  void "test ListRecords response status"() {
    when:
    URI uri = UriBuilder.of(getUrlPath())
      .path("/oai/packages")
      .queryParam('verb', 'ListRecords')
      .queryParam('metadataPrefix', 'gokb')
      .build()

    HttpRequest request = HttpRequest.GET(uri)
    HttpResponse resp = http.exchange(request)

    then:
    resp.status == HttpStatus.OK
  }

  void "test ListRecords package response"() {
    when:
    URI uri = UriBuilder.of(getUrlPath())
      .path("/oai/packages")
      .queryParam('verb', 'ListRecords')
      .queryParam('metadataPrefix', 'gokb')
      .build()

    HttpRequest request = HttpRequest.GET(uri)
    HttpResponse resp = http.exchange(request, String)

    then:
    resp.status == HttpStatus.OK
    def body = new XmlSlurper().parseText(resp.body())
    def pkg_node = body.ListRecords.record.metadata.gokb.package

    pkg_node?.isEmpty() == false
    pkg_node?.name == 'OAI Test Package 1'
    pkg_node?.identifiers?.identifier?.size() == 1
    pkg_node?.identifiers?.identifier?.@value == 'ZDB-1-OAIT'
    pkg_node?.identifiers?.identifier?.@namespace == 'isil'
    pkg_node?.identifiers?.identifier?.@namespaceName == 'ISIL'
    pkg_node?.nominalProvider?.@uuid == test_org.uuid
    pkg_node?.nominalPlatform?.@uuid == test_plt.uuid
    pkg_node?.curatoryGroups?.size() == 1
  }

  void "test GetRecord journal response"() {
    when:
    URI uri = UriBuilder.of(getUrlPath())
            .path("/oai/titles")
            .queryParam('verb', 'GetRecord')
            .queryParam('metadataPrefix', 'gokb')
            .queryParam('identifier', "org.gokb.cred.JournalInstance:$title1.id")
            .build()

    HttpRequest request = HttpRequest.GET(uri)
    HttpResponse resp = http.exchange(request, String)

    then:
    resp.status == HttpStatus.OK
    def body = new XmlSlurper().parseText(resp.body())
    def title_node = body.GetRecord.record.metadata.gokb.title

    title_node.isEmpty() == false
    title_node?.identifiers?.identifier?.size() == 2
    title_node.TIPPs.size() == 1
    title_node.TIPPs.TIPP.prices.price.type == 'list'
    title_node.TIPPs.TIPP.package.@uuid == test_pkg.uuid
    title_node.TIPPs.TIPP.package.@uuid == test_pkg.uuid
    title_node.TIPPs.TIPP.accessStartDate ==~ /^\d{4}-\d{2}-\d{2}$/
  }

  void "test GetRecord tipp response"() {
    when:
    URI uri = UriBuilder.of(getUrlPath())
            .path("/oai/tipps")
            .queryParam('verb', 'GetRecord')
            .queryParam('metadataPrefix', 'gokb')
            .queryParam('identifier', "$tipp1.uuid")
            .build()

    HttpRequest request = HttpRequest.GET(uri)
    HttpResponse resp = http.exchange(request, String)

    then:
    resp.status == HttpStatus.OK
    def body = new XmlSlurper().parseText(resp.body())
    def tipp_node = body.GetRecord.record.metadata.gokb.tipp

    tipp_node.isEmpty() == false
    tipp_node.name == 'OaiTestTIPP'
    tipp_node.package.@uuid == test_pkg.uuid
    tipp_node.title.@uuid == title1.uuid
    tipp_node.platform.@uuid == test_plt.uuid
    tipp_node.prices.price.type == 'list'
    tipp_node.prices.price.amount == '1234.56'
    tipp_node.prices.price.currency == 'EUR'
    tipp_node.publisherName == 'test Publisher'
    tipp_node?.identifiers?.identifier?.size() == 2
  }

  void "test GetRecord org response"() {
    when:
    URI uri = UriBuilder.of(getUrlPath())
      .path("/oai/orgs")
      .queryParam('verb', 'GetRecord')
      .queryParam('metadataPrefix', 'gokb')
      .queryParam('identifier', "org.gokb.cred.Org:$test_org.id")
      .build()

    HttpRequest request = HttpRequest.GET(uri)
    HttpResponse resp = http.exchange(request, String)

    then:
    resp.status == HttpStatus.OK
    GPathResult body = new XmlSlurper().parseText(resp.body())
    def org_node = body.GetRecord.record.metadata.gokb.org

    org_node.name == 'OAI Test Org'
    org_node.titleNamespace.@namespaceName == 'Test Title NS'
    org_node.packageNamespace.@value == 'packagenstest'
    org_node.providedPackages.size() == 1
  }

  void "test GetRecord package response"() {
    when:
    URI uri = UriBuilder.of(getUrlPath())
      .path("/oai/packages")
      .queryParam('verb', 'GetRecord')
      .queryParam('metadataPrefix', 'gokb')
      .queryParam('identifier', "org.gokb.cred.Package:$test_pkg.id")
      .build()

    HttpRequest request = HttpRequest.GET(uri)
    HttpResponse resp = http.exchange(request, String)

    then:
    resp.status == HttpStatus.OK
    GPathResult body = new XmlSlurper().parseText(resp.body())
    def pkg_node = body.GetRecord?.record?.metadata?.gokb?.package

    pkg_node?.name == 'OAI Test Package 1'
    pkg_node?.source?.name == "PackTestSource"
    pkg_node?.identifiers?.identifier?.size() == 1
    pkg_node?.TIPPs?.TIPP?.size() == 1
    pkg_node?.TIPPs?.TIPP?.name == 'OaiTestTIPP'
    pkg_node?.TIPPs?.TIPP?.identifiers?.identifier?.size() == 2
    pkg_node?.TIPPs?.TIPP?.title?.name == 'Test Title 1'
    pkg_node?.TIPPs?.TIPP?.title?.identifiers?.identifier?.size() == 2
  }
}
