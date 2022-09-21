package org.gokb

import geb.spock.GebSpec

import grails.converters.JSON
import grails.core.GrailsApplication
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback

import org.gokb.cred.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.web.context.WebApplicationContext

import spock.lang.Shared
import spock.lang.Specification

@Integration
@Rollback
class ValidationControllerSpec extends GebSpec {

  GrailsApplication grailsApplication

  @Shared
  RestBuilder rest = new RestBuilder()

  @Autowired
  WebApplicationContext ctx

  String baseUrl

  def setup() {
    baseUrl = "http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}"

    def existing_pkg = Package.findByName("Test Existing Name") ?: new Package(name: "Test Existing Name").save(flush:true)
  }

  def cleanup() {
    Package.findByName("Test Existing Name")?.expunge()
  }

  void "test /validation/kbart with valid serials KBART"() {
    given:
    def kbart_file = new ClassPathResource("/test_validation_serials_valid.csv")

    when:
    RestResponse resp = rest.post(baseUrl + "/validation/kbart") {
      accept('application/json')
      contentType("multipart/form-data")
      submissionFile=kbart_file.getFile()
    }
    then:
    resp.status == 200
    resp.json.result == 'OK'
    resp.json.report?.rows?.total == 1
    // File is missing Monograph columns
    resp.json.report?.warnings?.missingColumns?.size() > 0
  }

  void "test /validation/kbart with invalid ISSN"() {
    given:
    def kbart_file = new ClassPathResource("/test_validation_serials_errors.csv")
    when:
    RestResponse resp = rest.post(baseUrl + "/validation/kbart") {
      accept('application/json')
      contentType("multipart/form-data")
      submissionFile=kbart_file.getFile()
    }
    then:
    resp.status == 200
    resp.json.result == 'ERROR'
    resp.json.report?.rows?.error == 1
  }

  void "test /validation/kbart with invalid ISBN"() {
    given:
    def kbart_file = new ClassPathResource("/test_validation_monographs_errors.csv")
    when:
    RestResponse resp = rest.post(baseUrl + "/validation/kbart") {
      accept('application/json')
      contentType("multipart/form-data")
      submissionFile=kbart_file.getFile()
    }
    then:
    resp.status == 200
    resp.json.result == 'ERROR'
    resp.json.report?.rows?.error == 1
  }

  void "test /validation/componentName with valid TitleInstance name"() {
    given:
    def kbart_file = new ClassPathResource("/test_validation_monographs_errors.csv")
    when:
    RestResponse resp = rest.get(baseUrl + "/validation/componentName?value=Test+Valid+Name&componentType=Journal")
    then:
    resp.status == 200
    resp.json.result == 'OK'
  }

  void "test /validation/componentName with existing Package name"() {
    when:
    RestResponse resp = rest.get(baseUrl + "/validation/componentName?value=Test Existing Name&componentType=Package")
    then:
    resp.status == 200
    resp.json.result == 'ERROR'
    resp.json.errors.size() == 1
  }

  void "test /validation/identifier with invalid ISBN"() {
    when:
    RestResponse resp = rest.get(baseUrl + "/validation/identifier?value=978-1-137-05446-X&namespace=eissn")
    then:
    resp.status == 200
    resp.json.result == 'ERROR'
    resp.json.errors.size() == 1
  }

  void "test /validation/url with invalid URL"() {
    given:
    def request_body = [value: "http://notvalid.testurl"]
    when:
    RestResponse resp = rest.post(baseUrl + "/validation/url") {
      accept('application/json')
      contentType("application/json")
      body(request_body as JSON)
    }
    then:
    resp.status == 200
    resp.json.result == 'ERROR'
  }
}