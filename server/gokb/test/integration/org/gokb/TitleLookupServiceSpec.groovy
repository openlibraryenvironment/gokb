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

    void "Test find title by identifier"() {
      def t = null
      def name = null
      given: "Given a title with name TestTitle001 which has a single identifier of testNS:ID00001" 
        Identifier id = Identifier.lookupOrCreateCanonicalIdentifier('testNS','ID00001')
        TitleInstance ti = new TitleInstance(name:'TestTitle001');
        ti.save(flush:true, failOnError:true);
        ti.ids.add(id);
        ti.save(flush:true, failOnError:true);
      when: "When we use the matchClassOne method on titleLookupService" 
        def id_list = [ ['ns':'testNS', 'value':'ID00001']  ]
        t = titleLookupService.matchClassOnes(id_list)
      then: "Then we extract the name of the located title" 
      expect: "We expect to get the right title back"
        TitleInstance.list().size() == 1
        Identifier.findAllByValue('ID00001').size() == 1
        log.debug("OK");
        t?.size() == 1
        t[0]?.name=='TestTitle001'
    }

    void "Test Title Lookup By Identifier"() {
      given: "An list of candidate identifiers"
        def ids = [ ['ns':'testNS', 'value':'ID00001']  ]
      when: "Caller asks for a title id via identifier"
        def result = titleLookupService.matchClassOneComponentIds(ids)
      then: "then the service should return the approproate title"
      expect: "That only one title is returned with an id of"
        result.size() == 1
        result[0] == 1
    }
}
