package gokbg3

import grails.testing.web.interceptor.InterceptorUnitTest
import spock.lang.Specification

class PreferencesInterceptorSpec extends Specification implements InterceptorUnitTest<PreferencesInterceptor> {

    def setup() {
    }

    def cleanup() {

    }

    void "Test preferences interceptor matching"() {
        when:"A request matches the interceptor"
            withRequest(controller:"resource")

        then:"The interceptor does match"
            interceptor.doesMatch()
    }
}
