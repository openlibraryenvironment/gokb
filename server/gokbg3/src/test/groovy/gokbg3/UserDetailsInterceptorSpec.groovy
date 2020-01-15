package gokbg3

import grails.testing.web.interceptor.InterceptorUnitTest
import spock.lang.Specification

class UserDetailsInterceptorSpec extends Specification implements InterceptorUnitTest<UserDetailsInterceptor> {

    def setup() {
    }

    def cleanup() {

    }

    void "Test userDetails interceptor matching"() {
        when:"A request matches the interceptor"
            withRequest(controller:"resource")

        then:"The interceptor does match"
            interceptor.doesMatch()
    }
}
