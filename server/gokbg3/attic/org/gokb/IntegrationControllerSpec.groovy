package org.gokb

import spock.lang.Specification
import org.gokb.cred.*
// For @Autowired
import org.springframework.beans.factory.annotation.*

import grails.testing.mixin.integration.Integration
import grails.transaction.*
import spock.lang.*
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.Rollback;

import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.mock.web.MockMultipartHttpServletRequest


/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@Integration
class IntegrationControllerSpec extends Specification {

    // Stop grails from rolling back the transaction at the end of each call
    static transactional = false

    @Autowired
    WebApplicationContext ctx

    // extending IntegrationSpec means this works
    @Autowired
    TitleLookupService titleLookupService

    def setup() {
    }

    def cleanup() {
    }

    void "Test assertGroup"() {
      IntegrationController c = new IntegrationController()
      given "A JSON object containing a curatory group"
        def json_record = [
          "name" : "TestGroup1"
        ]
      when: "Caller asks for this record to be cross referenced"
        c.request.JSON = json_record
        c.assertGroup()
        println(c.response.json)
        def response = c.response.json
        // Give the background updates time to complete
        synchronized(this) {
          Thread.sleep(500)
        }
      then: "The item is created up as it does not already exist"
        response.message != null
        response.message.startsWith('Created')
      expect: "Find item by name only returns one item"
        def matching_groups = Platform.executeQuery('select cg from CuratoryGroup as cg where cg.name = :n',[n:json_record.name]);
        matching_platforms.size() == 1
        matching_platforms[0].id = response.platformId
    }

    void "Test assertOrg :: Import new Org"() {
      IntegrationController c = new IntegrationController()
      given "A JSON object containing a organisation that is not yet in the database"
        def json_record = [
          "identifiers" : [
            [
                "type" : "global",
                "value" : "http://d-nb.info/gnd/853-9"
            ]
          ],
          "name" : "American Chemical Society"
        ]
      when: "Caller asks for this record to be cross referenced"
        c.request.JSON = json_record
        c.assertOrg()
        println(c.response.json)
        def response = c.response.json
        // Give the background updates time to complete
        synchronized(this) {
          Thread.sleep(500)
        }
      then: "The item is created up as it does not already exist"
        response.message != null
        response.message.startsWith('Added')
      expect: "Find item by name only returns one item"
        def matching_groups = Platform.executeQuery('select o from Org as o where o.name = :n',[n:json_record.name]);
        matching_platforms.size() == 1
        matching_platforms[0].id = response.orgId
    }

    void "Test crossReferencePlatform :: Import new Platform"() {
      IntegrationController c = new IntegrationController()

      given: "A JSON object containing a platform that is not yet in the database"
        def json_record = [
          "platformName" : "pubs.acs.org",
          "platformUrl" : "https://pubs.acs.org",
          "provider" : "American Chemical Society"
        ]
      when: "Caller asks for this record to be cross referenced"
        c.request.JSON = json_record
        c.crossReferencePlatform()
        println(c.response.json)
        def response = c.response.json
        // Give the background updates time to complete
        synchronized(this) {
          Thread.sleep(200)
        }
      then: "The item is created up as it does not already exist"
        response.message != null
        response.message.startsWith('Created')
      expect: "Find item by name only returns one item"
        def matching_platforms = Platform.executeQuery('select p from Platform as p where p.name = :n',[n:json_record.platformName]);
        matching_platforms.size() == 1
        matching_platforms[0].id = response.platformId
        matching_platforms[0].provider?.name = json_record.provider
    }

    void "Test crossReferencePlatform :: Platform name matching"() {
      IntegrationController c = new IntegrationController()

      given: "A JSON object containing a platform that is already in the database"
        def json_record = [
          "platformName" : "pubs.acs.org",
          "platformUrl" : "https://pubs.acs.org"
        ]
      when: "Caller asks for this record to be cross referenced"
        c.request.JSON = json_record
        c.crossReferencePlatform()
        println(c.response.json)
        def response = c.response.json
        // Give the background updates time to complete
        synchronized(this) {
          Thread.sleep(200)
        }
      then: "The item is looked up"
        response.message != null
        response.message.startsWith('Created')
      expect: "Find item by name only returns one item"
        def matching_platforms = Platform.executeQuery('select p from Platform as p where p.name = :n',[n:json_record.platformName]);
        matching_platforms.size() == 1
        matching_platforms[0].id == response.platformId
    }

    void "Test crossReferenceTitle (JOURNAL) Case 1"() {
      IntegrationController c = new IntegrationController()
      given: "A Json record representing a instance record that is not yet in the database as an instance (Or work)"
        def json_record = [
          "identifiers" : [
            [
                "type" : "zdb",
                "value" : "1483109-0"
            ],
            [
                "type" : "eissn",
                "value" : "1520-5118"
            ],
            [
                "type" : "issn",
                "value" : "0021-8561"
            ]
          ],
          "name" : "Journal of agricultural and food chemistry",
          "publishedFrom" : "1953-01-01 00:00:00.000",
          "publishedTo" : "",
          "publisher_history" : [
            [
                "endDate" : "",
                "name" : "American Chemical Society",
                "startDate" : "1953-01-01 00:00:00.000",
                "status" : ""
            ]
          ],
          "type" : "Serial"
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
        def ids = [ ['ns':'issn', 'value':'0021-8561']  ]
        def matching_with_class_one_ids = titleLookupService.matchClassOneComponentIds(ids)
        matching_with_class_one_ids.size() == 1
        matching_with_class_one_ids[0] == response.titleId
    }

    void "Test crossReferencePackage :: Import new Package"() {
      IntegrationController controller = new IntegrationController()

      given: "A json package file with packageHeader and one TIPP, which are not yet present in the database"
        def json_record = [
          "packageHeader" : [
            "breakable" : "No",
            "consistent" : "Yes",
            "editStatus" : "In Progress",
            "fixed" : "No",
            "global" : "Consortium",
            "identifiers" : [
              [
                "type" : "isil",
                "value" : "ZDB-1-ACS"
              ]
            ],
            "listStatus" : "In Progress",
            "name" : "American Chemical Society: ACS Legacy Archives",
            "nominalPlatform" : [
              "name" : "ACS Publications",
              "primaryUrl" : "https://pubs.acs.org"
            ],
            "nominalProvider" : "American Chemical Society"
          ],
          "tipps" : [
            [
              "accessEnd" : "",
              "accessStart" : "",
              "coverage" : [
                  [
                    "coverageDepth" : "Fulltext",
                    "coverageNote" : "NL-DE;  1.1953 - 43.1995",
                    "embargo" : "",
                    "endDate" : "1995-12-31 00:00:00.000",
                    "endIssue" : "",
                    "endVolume" : "43",
                    "startDate" : "1953-01-01 00:00:00.000",
                    "startIssue" : "",
                    "startVolume" : "1"
                  ]
              ],
              "medium" : "Electronic",
              "platform" : [
                "name" : "pubs.acs.org",
                "primaryUrl" : "http://pubs.acs.org"
              ],
              "status" : "Current",
              "title" : [
                  "identifiers" : [
                    [
                        "type" : "zdb",
                        "value" : "1483109-0"
                    ],
                    [
                        "type" : "eissn",
                        "value" : "1520-5118"
                    ],
                    [
                        "type" : "issn",
                        "value" : "0021-8561"
                    ]
                  ],
                  "name" : "Journal of agricultural and food chemistry",
                  "type" : "Serial"
              ],
              "url" : "http://pubs.acs.org/journal/jafcau"
            ]
          ]
        ]
      when: "Caller asks for this record to be cross referenced"
        c.request.JSON = json_record
        c.crossReferencePackage()
        log.debug(c.response.json)
        def response = c.response.json
        // Give the background updates time to complete
        synchronized(this) {
          Thread.sleep(4000)
        }
      then: "The item is created in the database because it does not exist"
        response.message != null
        response.message.startsWith('Created')
      expect: "Find pkg by name, which is connected to the new TIPP"
        def matching_pkgs = Package.findAllByName("American Chemical Society: ACS Legacy Archives")
        matching_pkgs.size() == 1
        matching_pkgs[0].id == response.pkgId
        matching_pkgs[0].tipps?.size() == 1
        matching_pkgs[0].provider?.name = "American Chemical Society"
    }

    void "Test crossReferenceTitle (BOOK) Case 1"()
      IntegrationController c = new IntegrationController()
      given: "A Json record representing a instance record that is not yet in the database as an instance (Or work)"
        def json_record = [
          "identifiers" : [
            [
              "type" : "isbn",
              "value" : "987131223223X"
            ],
            [
              "type" : "doi",
              "value" : "10.1515/pdtc"
            ]
          ],
          "name" : "Test Book 1",
          "editionNumber" : "4",
          "volumeNumber" : "3",
          "firstAuthor" : "J. Smith",
          "dateFirstOnline" : "2019-01-01 00:00:00.000"
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
      expect: "Find item by ID can now locate that item and the discriminator is set correctly"
        def ids = [ ['ns':'isbn', 'value':'987-13-12232-23-X']  ]
        def matching_with_class_one_ids = titleLookupService.matchClassOneComponentIds(ids)
        matching_with_class_one_ids.size() == 1
        matching_with_class_one_ids[0] == response.titleId
        matching_with_class_one_ids[0].name == "Test Book 1"
        matching_with_class_one_ids[0].componentDiscriminator == "v.3ed.4a:jsmith"
}
