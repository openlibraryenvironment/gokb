package org.gokb

import grails.core.GrailsApplication
import grails.gorm.transactions.*
import grails.testing.mixin.integration.Integration

import groovy.xml.XmlSlurper

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.uri.UriBuilder

import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
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
  Package test_pkg
  Org test_org

  private String getUrlPath() {
    return "http://localhost:${serverPort}${grailsApplication.config.server.servlet.context-path ?: ''}".toString()
  }

  def setup() {
    if (!http) {
      http = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }

    def http = RefdataCategory.lookup('Source.DataSupplyMethod', 'HTTP Url').save(flush: true)
    def kbart = RefdataCategory.lookup('Source.DataFormat', 'KBART').save(flush: true)
    def freq = RefdataCategory.lookup('Source.Frequency', 'Weekly').save(flush: true)
    Source testSource = Source.findByName("PackTestSource") ?: new Source(
      name: "PackTestSource",
      url: "https://org/package",
      frequency: freq,
      defaultSupplyMethod: http,
      defaultDataFormat: kbart).save(flush: true)
    test_pkg = Package.findByName('Test Package 1')
    if (!test_pkg) {
      test_pkg = new Package(name: 'Test Package 1')
      test_pkg.source = testSource
      test_pkg.save(flush: true)
    }
    def test_plt = Platform.findByName('Test Platform') ?: new Platform(name: 'Test Platform').save(flush: true)
    test_org = Org.findByName("Test Org") ?: new Org(
      name: 'Test Org',
      titleNamespace: IdentifierNamespace.findByName('Test Title NS') ?: new IdentifierNamespace(name: 'Test Title NS', value: 'titleNStest'),
      packageNamespace: IdentifierNamespace.findByName('Test Package NS') ?: new IdentifierNamespace(name: 'Test Package NS', value: 'packageNStest'))
    title1 = JournalInstance.findByName('Test Title 1') ?: new JournalInstance(name: 'Test Title 1', series: 'Test Series Name').save(flush: true)
    //title1.setPrice('list', '12.54 GBP', new Date(2020, 01, 01))

    def eissn1 = Identifier.findByValue('1234-3456') ?: new Identifier(value: '1234-3456', namespace: IdentifierNamespace.findByValue('eissn')).save(flush: true)
    def issn1 = Identifier.findByValue('1234-4567') ?: new Identifier(value: '1234-4567', namespace: IdentifierNamespace.findByValue('issn')).save(flush: true)

    def tipp1 = new TitleInstancePackagePlatform(name:"testTIPP")
    tipp1.setPrice("list", "1234.56 EUR")
    tipp1.setPublisherName("test Publisher")

    new Combo(fromComponent: test_pkg, toComponent: tipp1, type: RefdataCategory.lookup('Combo.Type', 'Package.Tipps'), status: RefdataCategory.lookup('Combo.Status', 'Active')).save(flush: true)
    new Combo(fromComponent: test_plt, toComponent: tipp1, type: RefdataCategory.lookup('Combo.Type', 'Platform.HostedTipps'), status: RefdataCategory.lookup('Combo.Status', 'Active')).save(flush: true)
    new Combo(fromComponent: title1, toComponent: tipp1, type: RefdataCategory.lookup('Combo.Type', 'TitleInstance.Tipps'), status: RefdataCategory.lookup('Combo.Status', 'Active')).save(flush: true)

    def coverageStatement = [startDate: new Date(), startVolume: "1", startIssue: "1"]

    tipp1.addToCoverageStatements(coverageStatement)
    tipp1.save(flush: true)
  }

  def cleanup() {
    TitleInstancePackagePlatform.findByName('testTIPP')?.expunge()
    JournalInstance.findByName('Test Title 1')?.expunge()
    Package.findByName('Test Package 1')?.expunge()
    Source.findByName("PackTestSource")?.expunge()
    Platform.findByName('Test Platform')?.expunge()
    Identifier.findByValue('1234-3456')?.expunge()
    Identifier.findByValue('1234-4567')?.expunge()
  }

  // This is a test REST call
  void "test ListRecords response status"() {
    when:
    // RestResponse resp = authRest.get("http://localhost:${serverPort}/search/search")
    URI uri = UriBuilder.of(getUrlPath())
      .path("/oai/packages")
      .queryParam('verb', 'ListRecords')
      .queryParam('metadataPrefix', 'gokb')
      .build()

    HttpRequest request = HttpRequest.GET(uri)
    HttpResponse resp = http.exchange(request)

    then:
    // println(resp.json)
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
    body.'OAI-PMH'?.'ListRecords'?.'record'?.'metadata'?.'gokb'?.'package'?.'name'?.text() != null
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
    body.'OAI-PMH'.'GetRecord'.'record'.'metadata'.'gokb'.'title'.'TIPPs'.'TIPP'.'prices'.'price'.'type'.'list'.text() != null
    body.'OAI-PMH'.'GetRecord'.'record'.'metadata'.'gokb'.'title'.'TIPPs'.'TIPP'.'publisherName'.text() != null
  }

  void "test GetRecord org response"() {
    when:
    URI uri = UriBuilder.of(getUrlPath())
      .path("/oai/orgs")
      .queryParam('verb', 'GetRecord')
      .queryParam('metadataPrefix', 'gokb')
      .queryParam('identifier', "org.gokb.cred.OrgInstance:$test_org.id")
      .build()

    HttpRequest request = HttpRequest.GET(uri)
    HttpResponse resp = http.exchange(request, String)

    then:
    resp.status == HttpStatus.OK
    def body = new XmlSlurper().parseText(resp.body())
    body.'OAI-PMH'.'GetRecord'.'record'.'metadata'.'gokb'.'org'.'titleNamespace'.'namespaceName' != null
    body.'OAI-PMH'.'GetRecord'.'record'.'metadata'.'gokb'.'org'.'packageNamespace'.'value' != null
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
    def body = new XmlSlurper().parseText(resp.body())
    body.'OAI-PMH'?.'GetRecord'?.'record'?.'metadata'?.'gokb'?.'package'?.'source' != null
  }
}
