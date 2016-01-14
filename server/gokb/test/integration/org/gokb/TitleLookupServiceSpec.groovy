package org.gokb

import grails.test.mixin.TestFor
import spock.lang.Specification
import org.gokb.cred.*

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@TestFor(TitleLookupService)
class TitleLookupServiceSpec extends Specification {

    def setup() {
      TitleLookupService tls = new TitleLookupService()
    }

    def cleanup() {
    }

    void "test something"() {
      given: "test given" 
      Identifier id = Identifier.lookupOrCreateCanonicalIdentifier('testNS','ID00001')
      TitleInstance ti = new TitleInstance(name:'TestTitle001');
      ti.ids.add(id);
      ti.save(flush:true, failOnError:true);
      when: "test when" 
      def id_list = [ ['ns':'testNS', 'value':'ID00001']  ]
      t = tls.matchClassOne(id_list)
      then: "test then" 
      assert t.name=='TestTitle001'
    }
}
