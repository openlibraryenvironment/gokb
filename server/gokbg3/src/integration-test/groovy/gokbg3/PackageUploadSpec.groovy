package gokbg3

import grails.testing.mixin.integration.Integration
import grails.transaction.*
import spock.lang.Specification
import spock.lang.Shared
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse


@Integration
@Rollback
class PackageUploadSpec extends Specification {

    @Shared
    RestBuilder rest = new RestBuilder()

    def setup() {
    }

    def cleanup() {
    }

    
    void "testApiCall"() {
      // This is a place-holder for API call tests...
      true
    }

    // This is a test REST call 
    // void "test search"() {
    //   when:
    //     // RestResponse resp = rest.get("http://localhost:${serverPort}/search/search")
    //     RestResponse resp = rest.get("http://localhost:${serverPort}/")

    //   then:
    //     // println(resp.json)
    //     resp.status == 200
    // }
}
