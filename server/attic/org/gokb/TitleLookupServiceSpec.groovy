package org.gokb

import spock.lang.Specification
import org.gokb.cred.*
// For @Autowired
import org.springframework.beans.factory.annotation.*

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.*
import spock.lang.*
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.Rollback;

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@Integration
class TitleLookupServiceSpec extends Specification {

    // Stop grails from rolling back the transaction at the end of each call
    static transactional = false

    // extending IntegrationSpec means this works
    @Autowired
    TitleLookupService titleLookupService

    def setup() {
    }

    def cleanup() {
    }

    void "Test find title by identifier"() {
      def titles_matching = null
      def name = null
      given: "Given a title with name TestTitle001 which has a single identifier of testNS:ID00001" 
        Identifier id = Identifier.lookupOrCreateCanonicalIdentifier('issn','ID00001')
        TitleInstance ti = new TitleInstance(name:'TestTitle001');
        ti.save(flush:true, failOnError:true);
        ti.ids.add(id);
        ti.save(flush:true, failOnError:true);
      when: "When we use the matchClassOne method on titleLookupService" 
        def id_list = [ ['ns':'issn', 'value':'ID00001']  ]
        titles_matching = titleLookupService.matchClassOnes(id_list)
      then: "Then we extract the name of the located title" 
      expect: "We expect to get the right title back"
        TitleInstance.list().size() == 1
        Identifier.findAllByValue('ID00001').size() == 1
        log.debug("OK");
        titles_matching?.size() == 1
        titles_matching[0]?.name=='TestTitle001'
    }

    void "Test Component ID Lookup By Identifier"() {
      def matching_with_class_one_ids = null;
      def ids = null;
      def new_title = null;

      given: "An list of candidate identifiers"
        Identifier id = Identifier.lookupOrCreateCanonicalIdentifier('issn','ID00002')
        TitleInstance ti = new TitleInstance(name:'TestTitle002');
        ti.save(flush:true, failOnError:true);
        ti.ids.add(id);
        new_title = ti.save(flush:true, failOnError:true);
      when: "Caller asks for a title id via identifier"
        ids = [ ['ns':'issn', 'value':'ID00002']  ]
        matching_with_class_one_ids = titleLookupService.matchClassOneComponentIds(ids)
      then: "then the service should return the approproate title"
      expect: "That only one title is returned with an id of"
        matching_with_class_one_ids.size() == 1
        matching_with_class_one_ids[0] == new_title.id
    }

    // IntegrationController::crossReferenceTitle is our canonical method for absorbing bib records
    // which describe Instances (See Bibframe::instance).
    // N.B. There is an argument that this test is better placed in the functional test suite. HOwever here we're
    // really exercising the services underpinning this controller, expect to see this test replicated in the func suite.
    void "Test IntegrationController::crossReferenceTitle (BOOK) Case 1"() {
      def c = new IntegrationController()
      given: "A Json record representing a instance record that is not yet in the database as an instance (Or work)"
        def json_record = [
          'name':'Brain of the Firm',
          'primaryAuthor':'Beer, Stafford',
          'identifiers':[['type':'isbn', 'value':'0 471 27687 1'],
                         ['type':'isbn', 'value':'0-471-94839-X']
                        ],
          'type':'Monograph'
        ]
      when: "Caller asks for this record to be cross referenced"
        c.request.JSON = json_record
        c.crossReferenceTitle()
        println(c.response.json)
        def response = c.response.json
        // Give the background updates time to complete
        synchronized(this) {
          Thread.sleep(4000)
        }
      then: "The item is created in the database because it does not exist"
        response.message != null
        response.message.startsWith('Created')
      expect: "Find item by ID can now locate that item"
        def ids = [ ['ns':'isbn', 'value':'0-471-94839-X']  ]
        def matching_with_class_one_ids = titleLookupService.matchClassOneComponentIds(ids)
        matching_with_class_one_ids.size() == 1
        matching_with_class_one_ids[0] == response.titleId
    }

    void "Test IntegrationController::crossReferenceTitle (JOURNAL) Case 1"() {
      def c = new IntegrationController()
      given: "A Json record representing a instance record that is not yet in the database as an instance (Or work)"
        def json_record = [
          'name':'Structured programming',
          'identifiers':[['type':'issn', 'value':'0935-1183']],
          'type':'Serial'
        ]
      when: "Caller asks for this record to be cross referenced"
        c.request.JSON = json_record
        c.crossReferenceTitle()
        log.debug(c.response.json)
        def response = c.response.json
        // Give the background updates time to complete
        synchronized(this) {
          Thread.sleep(4000)
        }
      then: "The item is created in the database because it does not exist"
        response.message != null
        response.message.startsWith('Created')
      expect: "Find item by ID can now locate that item"
        def ids = [ ['ns':'issn', 'value':'0935-1183']  ]
        def matching_with_class_one_ids = titleLookupService.matchClassOneComponentIds(ids)
        matching_with_class_one_ids.size() == 1
        matching_with_class_one_ids[0] == response.titleId
    }

    void "Test that work instances created"() {
      given: "The prior tests completed"
      when: "I wait for any work update threads to complete, then search for all works"
        synchronized(this) {
          Thread.sleep(4000)
        }
        def works = Work.executeQuery('select w from Work as w')
      then: "I should find two work records"
        works.size() == 2
    }

    void "Ensure we can Create a title that has no identifiers"() {
      def c = new IntegrationController()
      given: "A Json record representing a instance record (That has no identifiers) that is not yet in the database as an instance (Or work)"
        def json_record = [
          'name':'A.A.U.T.A. news bulletin',
          'identifiers':[],
          'type':'Serial'
        ]
      when: "Caller asks for this record to be cross referenced"
        c.request.JSON = json_record
        c.crossReferenceTitle()
        println(c.response.json)
        def response = c.response.json
        // Give the background updates time to complete
        synchronized(this) {
          Thread.sleep(4000)
        }
      then: "The item is created up as it does not already exist"
        response.message != null
        response.message.startsWith('Created')
      expect: "Find item by normname only returns one item"
        def normalised_title = GOKbTextUtils.norm2('A.A.U.T.A. news bulletin')
        normalised_title.equals('aautabulletinnews');
        def matching_titles = TitleInstance.executeQuery('select t from TitleInstance as t where t.normname = :n',[n:normalised_title]);
        matching_titles.size() == 1
    }


    void "Test Work Mappings for First Edition"() {
      def c = new IntegrationController()
      given: "A Json record representing a instance record that is not yet in the database as an instance (Or work)"
        // First edition, published 1972-03-01
        def json_record = [
          'name':'Brain of the Firm',
          'primaryAuthor':'Beer, Stafford',
          'identifiers':[['type':'isbn', 'value':'0713902191'],
                         ['type':'isbn', 'value':'9780713902198']
                        ],
          'editionStatement':'First edition',
          'type':'Monograph'
        ]
      when: "Caller asks for this record to be cross referenced"
        c.request.JSON = json_record
        c.crossReferenceTitle()
        println(c.response.json)
        def response = c.response.json
        // Give the background updates time to complete
        synchronized(this) {
          Thread.sleep(4000)
        }
      then: "The item is created in the database because it does not exist"
        response.message != null
        response.message.startsWith('Created')
      expect: "Find item by ID can now locate that item"
        def ids = [ ['ns':'isbn', 'value':'9780713902198']  ]
        def matching_with_class_one_ids = titleLookupService.matchClassOneComponentIds(ids)
        matching_with_class_one_ids.size() == 1
        matching_with_class_one_ids[0] == response.titleId
    }

    void "Test Problem Record Identified In GOKb load"() {
      def c = new IntegrationController()
      given: "A Json record representing a instance record that is not yet in the database as an instance (Or work)"
        def json_record = [
          'name':'ACM SIGICE Bulletin',
          'identifiers':[['type':'eissn', value:'1558-1144'], 
                         ['type':'issn', value:'1078-134X']
                        ],
          'type':'Serial'
        ]
      when: "Caller asks for this record to be cross referenced"
        c.request.JSON = json_record
        c.crossReferenceTitle()
        println(c.response.json)
        def response = c.response.json
        // Give the background updates time to complete
        synchronized(this) {
          Thread.sleep(4000)
        }
      then: "The item is created in the database because it does not exist"
        response.message != null
        response.message.startsWith('Created')
      expect: "Find item by ID can now locate that item"
        def ids = [ ['ns':'issn', 'value':'1078-134X']  ]
        def matching_with_class_one_ids = titleLookupService.matchClassOneComponentIds(ids)
        matching_with_class_one_ids.size() == 1
        matching_with_class_one_ids[0] == response.titleId
    }


    void "Test repeated load of duplicate item"() {

      // Check that we can find the existing item
      def precheck_ids = [ ['ns':'issn', 'value':'1078-134X']  ]
      def precheck_lookup_result = titleLookupService.matchClassOneComponentIds(precheck_ids)
      assert precheck_lookup_result.size() == 1

      def c = new IntegrationController()
      given: "A Json record representing a instance record that is not yet in the database as an instance (Or work)"
        def json_record = [
          'name':'ACM SIGICE Bulletin',
          'identifiers':[['type':'eissn', value:'1558-1144'],
                         ['type':'issn', value:'1078-134X'],
                         ['type':'wibbleNS', value:'Wibble99887766']
                        ],
          'type':'Serial'
        ]
      when: "Caller asks for this record to be cross referenced"
        c.request.JSON = json_record
        c.crossReferenceTitle()
        println(c.response.json)
        def response = c.response.json
        // Give the background updates time to complete
        synchronized(this) {
          Thread.sleep(4000)
        }
      then: "The item is looked up as it already exists"
        response.message != null
        response.message.startsWith('Created')
      expect: "Find item by ID still only returns one item"
        def ids = [ ['ns':'issn', 'value':'1078-134X'] ]
        def matching_with_class_one_ids = titleLookupService.matchClassOneComponentIds(ids)
        matching_with_class_one_ids.size() == 1
        matching_with_class_one_ids[0] == response.titleId
    }


    void "Attempt to Add a second title with the same title and no identifiers should upsert the original, not create a new title"() {
      def c = new IntegrationController()
      given: "A Json record representing a instance record (That has no identifiers) that is not yet in the database as an instance (Or work)"
        def json_record = [
          'name':'A.A.U.T.A. news bulletin',
          'identifiers':[],
          'type':'Serial'
        ]
      when: "Caller asks for this record to be cross referenced"
        c.request.JSON = json_record
        c.crossReferenceTitle()
        println(c.response.json)
        def response = c.response.json
        // Give the background updates time to complete
        synchronized(this) {
          Thread.sleep(4000)
        }
      then: "The item is created up as it does not already exist"
        response.message != null
        response.message.startsWith('Created')
      expect: "Find item by normname only returns one item"
        def normalised_title = GOKbTextUtils.norm2('A.A.U.T.A. news bulletin')
        normalised_title.equals('aautabulletinnews');
        def matching_titles = TitleInstance.executeQuery('select t from TitleInstance as t where t.normname = :n',[n:normalised_title]);
        matching_titles.size() == 1
    }
   
}
