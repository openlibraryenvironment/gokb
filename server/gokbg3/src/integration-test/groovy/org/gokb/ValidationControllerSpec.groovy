package org.gokb

import grails.core.GrailsApplication
import grails.gorm.transactions.*
import grails.testing.mixin.integration.Integration

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.multipart.MultipartBody

import org.gokb.cred.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.web.context.WebApplicationContext

import spock.lang.Shared
import spock.lang.Specification

@Integration
@Rollback
class ValidationControllerSpec extends Specification {

  GrailsApplication grailsApplication

  @Autowired
  WebApplicationContext ctx

  BlockingHttpClient http

  String baseUrl

  def setup() {
    baseUrl = "http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}"

    if (!http) {
      http = HttpClient.create(new URL(baseUrl)).toBlocking()
    }

    def existing_pkg = Package.findByName("Test Existing Name") ?: new Package(name: "Test Existing Name").save(flush:true)
    def new_ns = IdentifierNamespace.findByValue('newtestns') ?: new IdentifierNamespace(value: 'newtestns', pattern: "^pack\\w+ID\$").save(flush:true)
  }

  def cleanup() {
    Package.findByName("Test Existing Name")?.expunge()
    IdentifierNamespace.findByValue('newtestns')?.delete(flush:true)
  }

  void "test /validation/kbart with valid serials KBART and missing monograph columns"() {
    given:
    def kbart_file = new ClassPathResource("/test_validation_serials_valid.txt")

    when:
    MultipartBody requestBody = MultipartBody.builder()
      .addPart(
        "submissionFile",
        "test_validation_serials_valid.txt",
        MediaType.TEXT_PLAIN_TYPE,
        kbart_file.getFile()
      )
      .build()

    HttpRequest request = HttpRequest.POST(baseUrl + "/validation/kbart", requestBody)
      .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
    HttpResponse resp = http.exchange(request, Map)
    then:
    resp.status == HttpStatus.OK
    resp.body().result == 'OK'
    resp.body().report?.rows?.total == 1
    // File is missing Monograph columns
    resp.body().report?.warnings?.missingColumns?.size() > 0
  }

  void "test /validation/kbart with invalid ISSN"() {
    given:
    def kbart_file = new ClassPathResource("/test_validation_serials_errors.txt")
    when:
    MultipartBody requestBody = MultipartBody.builder()
      .addPart(
        "submissionFile",
        "test_validation_serials_errors.txt",
        MediaType.TEXT_PLAIN_TYPE,
        kbart_file.getFile()
      )
      .build()

    HttpRequest request = HttpRequest.POST(baseUrl + "/validation/kbart", requestBody)
      .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
    HttpResponse resp = http.exchange(request, Map)
    then:
    resp.status == HttpStatus.OK
    resp.body().result == 'ERROR'
    resp.body().report?.rows?.error == 1
  }

  void "test /validation/kbart with invalid ISBN"() {
    given:
    def kbart_file = new ClassPathResource("/test_validation_monographs_errors.txt")
    when:
    MultipartBody requestBody = MultipartBody.builder()
      .addPart(
        "submissionFile",
        "test_validation_monographs_errors.txt",
        MediaType.TEXT_PLAIN_TYPE,
        kbart_file.getFile()
      )
      .build()

    HttpRequest request = HttpRequest.POST(baseUrl + "/validation/kbart", requestBody)
      .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
    HttpResponse resp = http.exchange(request, Map)
    then:
    resp.status == HttpStatus.OK
    resp.body().result == 'ERROR'
    resp.body().report?.rows?.error == 1
  }


  void "test /validation/kbart with valid mixed package and namespace"() {
    given:
    def kbart_file = new ClassPathResource("/test_validation_mixed_valid.txt")
    when:
    MultipartBody requestBody = MultipartBody.builder()
      .addPart(
        "submissionFile",
        "test_validation_mixed_valid.txt",
        MediaType.TEXT_PLAIN_TYPE,
        kbart_file.getFile()
      )
      .build()

    HttpRequest request = HttpRequest.POST(baseUrl + "/validation/kbart", requestBody)
      .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
    HttpResponse resp = http.exchange(request, Map)
    then:
    resp.status == HttpStatus.OK
    resp.body().result == 'OK'
    resp.body().report?.rows?.total == 2
  }


  void "test /validation/componentName with valid TitleInstance name"() {
    given:
    def kbart_file = new ClassPathResource("/test_validation_monographs_errors.txt")
    when:
    HttpRequest request = HttpRequest.GET(baseUrl + "/validation/componentName?value=Test+Valid+Name&componentType=Journal")
    HttpResponse resp = http.exchange(request, Map)
    then:
    resp.status == HttpStatus.OK
    resp.body().result == 'OK'
  }

  void "test /validation/componentName with existing Package name"() {
    when:
    HttpRequest request = HttpRequest.GET(baseUrl + "/validation/componentName?value=Test+Existing+Name&componentType=Package")
    HttpResponse resp = http.exchange(request, Map)
    then:
    resp.status == HttpStatus.OK
    resp.body().result == 'ERROR'
    resp.body().errors.size() == 1
  }

  void "test /validation/identifier with invalid ISBN"() {
    when:
    HttpRequest request = HttpRequest.GET(baseUrl + "/validation/identifier?value=978-1-137-05446-X&namespace=isbn")
    HttpResponse resp = http.exchange(request, Map)
    then:
    resp.status == HttpStatus.OK
    resp.body().result == 'ERROR'
    resp.body().errors.size() == 1
  }

  void "test /validation/identifier with valid ISSN"() {
    when:
    HttpRequest request = HttpRequest.GET(baseUrl + "/validation/identifier?value=1673-3436&namespace=issn")
    HttpResponse resp = http.exchange(request, Map)
    then:
    resp.status == HttpStatus.OK
    resp.body().result == 'OK'
  }

  void "test /validation/url with invalid URL"() {
    when:
    def request_body = [
      "value": "http://invalid-url.invalid"
    ]

    HttpRequest request = HttpRequest.POST(baseUrl + "/validation/url", request_body)
    def resp = http.exchange(request, Map)
    then:
    resp.status == HttpStatus.OK
    resp.body().result == 'ERROR'
  }
}