package gokbg3

import grails.testing.mixin.integration.Integration
import grails.transaction.*
import spock.lang.Specification
import spock.lang.Shared
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.beans.factory.annotation.*
import org.springframework.web.context.WebApplicationContext


@Integration
@Rollback
class PackageUploadSpec extends Specification {

    @Shared
    RestBuilder rest = new RestBuilder()

    @Autowired
    WebApplicationContext ctx

    def setup() {
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
        // RestResponse resp = rest.get("http://localhost:${serverPort}/search/search")
        RestResponse resp = rest.post("http://localhost:${serverPort}/packages/deposit") {
          auth 'admin', 'admin'
          contentType "multipart/form-data"
          // String properties
          source='DAC_TEST'.getBytes()
          fmt='DAC'.getBytes()
          pkg='DAC Test Ingest'.getBytes()
          platformUrl='http://dactest.com'.getBytes()
          format='tsv'.getBytes()
          providerName='DACTEST'.getBytes()
          providerIdentifierNamespace='DACTEST'.getBytes()
          reprocess='Y'.getBytes()
          synchronous='Y'.getBytes()
          flags='+ReviewNewTitles,+ReviewVariantTitles,+ReviewNewOrgs'.getBytes()
          // Upload file content
          content= jac_upload_file_resource.getFile();
        }

      then:
        // println(resp.json)
        resp.status == 200
    }
}
