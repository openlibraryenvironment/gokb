package org.gokb

import grails.core.GrailsApplication
import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.*
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.multipart.MultipartBody
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.beans.factory.annotation.*
import org.springframework.web.context.WebApplicationContext
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Shared


@Integration
@Ignore
class PackageUploadSpec extends Specification {


    GrailsApplication grailsApplication

    BlockingHttpClient http

    @Autowired
    WebApplicationContext ctx

    def setup() {
      if (!http) {
        http = HttpClient.create(new URL(getUrlPath())).toBlocking()
      }
    }

    def cleanup() {
    }


    void "testApiCall"() {
      // This is a place-holder for API call tests...
      true
    }

    // This is a test REST call
    void "test search"() {

      Resource jac_upload_file_resource = new ClassPathResource("/test_archival_format.tsv")

      when:
        // RestResponse resp = authRest.get("http://localhost:${serverPort}/search/search")
        MultipartBody requestBody = MultipartBody.builder()
        .addPart(
          "content",
          "test_archival_format.tsv",
          MediaType.TEXT_PLAIN_TYPE,
          jac_upload_file_resource.getFile()
        )
        .addPart('source', 'DAC_TEST')
        .addPart('fmt', 'DAC')
        .addPart('pkg','DAC Test Ingest')
        .addPart('platformUrl','http://dactest.com')
        .addPart('format','tsv')
        .addPart('providerName','DACTEST')
        .addPart('providerIdentifierNamespace','DACTEST')
        .addPart('reprocess','Y')
        .addPart('synchronous','Y')
        .addPart('flags','+ReviewNewTitles,+ReviewVariantTitles,+ReviewNewOrgs')
        .build()

        HttpRequest request = HttpRequest.POST("http://localhost:${serverPort}${grailsApplication.config.server.servlet.context-path ?: ''}/packages/deposit", requestBody)
          .basicAuth('admin', 'admin')
          .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        HttpResponse resp = http.exchange(request)


      then:
        // println(resp.json)
        resp.status == HttpStatus.OK
    }
}
