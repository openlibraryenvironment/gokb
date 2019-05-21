package gokbg3

import geb.spock.GebSpec
import grails.plugins.rest.client.RestBuilder
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.springframework.beans.factory.annotation.Value

/**
 * See http://www.gebish.org/manual/current/ for more instructions
 */
@Integration
@Rollback
class LoginRestApiSpec extends GebSpec {
    @Value('${local.server.port}')
    Integer serverPort

    def setup() {
        System.setProperty('webdriver.gecko.driver', '/home/volker/bin/geckodriver/geckodriver')

    }

    def cleanup() {
        System.clearProperty('webdriver.gecko.driver')
    }

    void "test REST login"() {
        given:
        RestBuilder rest = new RestBuilder()
        when:
        String uri = "http://localhost:${serverPort}/rest/login"
        def response = rest.post(uri) {
            headers['X-Requested-With'] = 'XMLHttpRequest'
            json {
                username = 'pad'
                password = '123456'
            }
        }
        then:
        response.status == 200
        response.json.access_token != null
        response.json.refresh_token != null
        response.json.username == 'pad'
        response.json.roles.size() > 0
    }
}


