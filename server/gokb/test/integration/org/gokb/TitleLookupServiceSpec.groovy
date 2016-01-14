package org.gokb

import grails.test.mixin.TestFor
import spock.lang.Specification
import org.gokb.cred.*
import grails.test.spock.IntegrationSpec
// For @Autowired
import org.springframework.beans.factory.annotation.*

import grails.test.mixin.integration.Integration
import grails.transaction.*
import spock.lang.*
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.Rollback;

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@Integration
class TitleLookupServiceSpec extends Specification {

    // extending IntegrationSpec means this works
    @Autowired
    TitleLookupService titleLookupService

    def setup() {
    }

    def cleanup() {
    }

    void "test something"() {
      given: "Given a title with name TestTitle001 which has a single identifier of testNS:ID00001" 
        Identifier id = Identifier.lookupOrCreateCanonicalIdentifier('testNS','ID00001')
        TitleInstance ti = new TitleInstance(name:'TestTitle001');
        ti.ids.add(id);
        ti.save(flush:true, failOnError:true);
      when: "When we use the matchClassOne method on titleLookupService" 
        def id_list = [ ['ns':'testNS', 'value':'ID00001']  ]
        def t = titleLookupService.matchClassOne(id_list)
      then: "Then we extract the name of the located title" 
        def name = t[0].name
      expect: "We expect to get the right title back"
        t[0].name=='TestTitle001'
    }
}
