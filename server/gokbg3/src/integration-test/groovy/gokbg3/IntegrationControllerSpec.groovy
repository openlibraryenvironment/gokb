package org.gokb

import spock.lang.Specification
import org.gokb.cred.*

// For @Autowired
import org.springframework.beans.factory.annotation.*

import grails.testing.mixin.integration.Integration
import spock.lang.*
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.converters.JSON
import groovy.json.JsonSlurper
import grails.core.GrailsApplication
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.web.context.WebApplicationContext
import grails.transaction.Rollback
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Integration
@Rollback
class IntegrationControllerSpec extends Specification {

  GrailsApplication grailsApplication

  @Autowired
  WebApplicationContext ctx

  @Shared
  RestBuilder rest = new RestBuilder()

  // extending IntegrationSpec means this works
  @Autowired
  TitleLookupService titleLookupService

  def setup() {
    def new_cg = CuratoryGroup.findByName('TestGroup1') ?: new CuratoryGroup(name: "TestGroup1").save(flush: true)
    def acs_org = Org.findByName("American Chemical Society") ?: new Org(name: "American Chemical Society").save(flush: true)
    def acs_test_plt = Platform.findByName('ACS Publications') ?: new Platform(name: 'ACS Publications', primaryUrl: 'https://pubs.acs.org').save(flush: true)
    def test_upd_org = Org.findByName('ACS TestOrg') ?: new Org(name: 'ACS TestOrg').save(flush: true)
    def test_upd_pkg = Package.findByName('TestTokenPackage') ?: new Package(name: 'TestTokenPackage').save(flush: true)
    def user = User.findByUsername('ingestAgent')
    if (!user.apiUserStatus) {
      UserRole.create(user, Role.findByAuthority('ROLE_API'), true)
    }
    def pkg_token = UpdateToken.findByValue('TestUpdateToken') ?: new UpdateToken(value: 'TestUpdateToken', pkg: test_upd_pkg, updateUser: user).save(flush: true)
  }

  def cleanup() {
    CuratoryGroup.findByName('TestGroup1')?.expunge()
    CuratoryGroup.findByName('TestGroup2')?.expunge()
    Org.findByName("American Chemical Society")?.expunge()
    Org.findByName('ACS TestOrg')?.expunge()
    Platform.findByName('ACS Publications')?.expunge()
    Package pkg = Package.findByName('TestTokenPackage')
    pkg?.expunge()
    UpdateToken.findByValue('TestUpdateToken')?.delete()
    TitleInstance.findAllByName("Acta cytologica")?.each { title ->
      title.expunge()
    }
    TitleInstance.findAllByName("TestJournal_Dates")?.each { title ->
      title.expunge()
    }
    Identifier.findByValue('zdb:2256676-4')?.expunge()
  }

  void "Test assertGroup"() {

    when: "Caller asks for this record to be cross referenced"
    def json_record = [
      "name" : "TestGroup2",
      "owner": "admin"
    ]

    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/assertGroup") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    then: "The item is created up as it does not already exist"
    resp.json.groupId != null
    resp.json.message != null
    resp.json.message.startsWith('Created')
    expect: "Find item by name only returns one item"
    def matching_groups = CuratoryGroup.executeQuery('select cg from CuratoryGroup as cg where cg.name = :n', [n: json_record.name]);
    matching_groups.size() == 1
    matching_groups[0].id == resp.json.groupId
  }

  void "Test assertOrg :: Import new Org"() {

    when: "Caller asks for this record to be cross referenced"
    def json_record = [
      "identifiers": [
        [
          "type" : "global",
          "value": "org-test-id-acs"
        ]
      ],
      "name"       : "TestOrgAcs"
    ]
    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/assertOrg") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    then: "The item is created up as it does not already exist"
    resp.json.message != null
    resp.json.message.startsWith('Added')
    expect: "Find item by name only returns one item"
    def matching_orgs = Org.executeQuery('select o from Org as o where o.name = :n', [n: json_record.name]);
    matching_orgs.size() == 1
    matching_orgs[0].id == resp.json.orgId
    matching_orgs[0].ids?.size() == 1
  }

  void "Test crossReferencePlatform :: Import new Platform"() {

    when: "Caller asks for this record to be cross referenced"
    def json_record = [
      "platformName": "TestPlt1",
      "name"        : "TestPlt1",
      "platformUrl" : "https://acstest.url"
    ]
    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferencePlatform") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    then: "The item was created"
    resp.json.message != null
    resp.json.result == "OK"
    expect: "Find item by name only returns one item"
    sleep(500)
    def matching_platforms = Platform.executeQuery('select p from Platform as p where p.name = :n', [n: json_record.platformName]);
    matching_platforms.size() == 1
    matching_platforms[0].id == resp.json.platformId
  }

  void "Test crossReferenceTitle JOURNAL Case 1"() {

    when: "Caller asks for this record to be cross referenced"
    def json_record = [
      "identifiers"      : [
        [
          "type" : "zdb",
          "value": "1483109-0"
        ],
        [
          "type" : "eissn",
          "value": "1520-5118"
        ],
        [
          "type" : "issn",
          "value": "0021-8561"
        ]
      ],
      "name"             : "Journal of agricultural and food chemistry",
      "publishedFrom"    : "1953-01-01 00:00:00.000",
      "publishedTo"      : "",
      "publisher_history": [
        [
          "endDate"  : "",
          "name"     : "American Chemical Society",
          "startDate": "1953-01-01 00:00:00.000",
          "status"   : ""
        ]
      ],
      "type"             : "Serial"
    ]
    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferenceTitle") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    then: "The item is created in the database because it does not exist"
    resp.json.message != null
    resp.json.message.startsWith('Created')
    expect: "Find item by ID can now locate that item"
    def ids = [['ns': 'issn', 'value': '0021-8561']]
    def matching_with_class_one_ids = titleLookupService.matchClassOneComponentIds(ids)
    matching_with_class_one_ids?.size() == 1
    matching_with_class_one_ids[0] == resp.json.titleId
  }

  void "Test crossReferenceTitle with incomplete dates"() {

    when: "Caller asks for this record to be cross referenced"
    def json_record = [
      "identifiers"      : [
        [
          "type" : "zdb",
          "value": "1423434-0"
        ]
      ],
      "name"             : "TestJournal_Dates",
      "publishedFrom"    : "1953-01",
      "publishedTo"      : "2001",
      "publisher_history": [
        [
          "endDate"  : "",
          "name"     : "American Chemical Society",
          "startDate": "1953",
          "status"   : ""
        ]
      ],
      "type"             : "Serial"
    ]
    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferenceTitle") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    then: "The item is created in the database because it does not exist"
    resp.json.message != null
    resp.json.message.startsWith('Created')
    expect: "Find item by ID can now locate that item"
    def title = TitleInstance.findById(resp.json.titleId)
    title != null
    title.publishedFrom?.toString() == "1953-01-01 00:00:00.0"
    title.publishedTo?.toString() == "2001-12-31 00:00:00.0"
    title.getCombosByPropertyName('publisher')?.size() == 1
    title.getCombosByPropertyName('publisher')[0].startDate?.toString() == "1953-01-01 00:00:00.0"
  }

  void "Test crossReferenceTitle :: Journal with history"() {

    when: "Caller asks for this record to be cross referenced"
    def json_record = [
      "identifiers"      : [
        [
          "type" : "eissn",
          "value": "1549-960X"
        ],
        [
          "type" : "zdb",
          "value": "1491237-5"
        ]
      ],
      "name"             : "Journal of chemical information and modeling",
      "publishedFrom"    : "1982-01-01 00:00:00.000",
      "publishedTo"      : "",
      "historyEvents"    : [
        [
          "date": "1982-01-01 00:00:00.000",
          "from": [
            [
              "title"      : "Journal of chemical documentation",
              "identifiers": [
                [
                  "type" : "eissn",
                  "value": "1541-5732"
                ],
                [
                  "type" : "zdb",
                  "value": "2096906-5"
                ]
              ]
            ]
          ],
          "to"  : [
            [
              "title"      : "Journal of chemical information and modeling",
              "identifiers": [
                [
                  "type" : "eissn",
                  "value": "1549-960X"
                ],
                [
                  "type" : "zdb",
                  "value": "1491237-5"
                ]
              ]
            ]
          ]
        ]
      ],
      "publisher_history": [
        [
          "endDate"  : "",
          "name"     : "American Chemical Society",
          "startDate": "1982-01-01 00:00:00.000",
          "status"   : ""
        ]
      ],
      "type"             : "Serial"
    ]
    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferenceTitle") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    then: "The item is created in the database because it does not exist"
    resp.json.message != null
    resp.json.message.startsWith('Created')
    expect: "Find item by ID can now locate the history item"
    def ids = [['ns': 'eissn', 'value': '1541-5732']]
    def matching_with_class_one_ids = titleLookupService.matchClassOneComponentIds(ids)
    matching_with_class_one_ids?.size() == 1
  }

  void "Test crossReferencePackage :: Import new Package"() {

    when: "Caller asks for this record to be cross referenced"
    def json_record = [
      "packageHeader": [
        "breakable"      : "No",
        "consistent"     : "Yes",
        "editStatus"     : "In Progress",
        "fixed"          : "No",
        "global"         : "Consortium",
        "identifiers"    : [
          [
            "type" : "isil",
            "value": "ZDB-1-ACS"
          ]
        ],
        "listStatus"     : "In Progress",
        "name"           : "American Chemical Society: ACS Legacy Archives",
        "nominalPlatform": [
          "name"      : "ACS Publications",
          "primaryUrl": "https://pubs.acs.org"
        ],
        "nominalProvider": "American Chemical Society"
      ],
      "tipps"        : [
        [
          "accessEnd"  : "",
          "accessStart": "",
          "identifiers": [
            [
              "type" : "global",
              "value": "testTippId"
            ]
          ],
          "coverage"   : [
            [
              "coverageDepth": "Fulltext",
              "coverageNote" : "NL-DE;  1.1953 - 43.1995",
              "embargo"      : "",
              "endDate"      : "1995-12-31 00:00:00.000",
              "endIssue"     : "",
              "endVolume"    : "43",
              "startDate"    : "1953-01-01 00:00:00.000",
              "startIssue"   : "",
              "startVolume"  : "1"
            ]
          ],
          "medium"     : "Electronic",
          "platform"   : [
            "name"      : "ACS Publications",
            "primaryUrl": "https://pubs.acs.org"
          ],
          "status"     : "Current",
          "title"      : [
            "identifiers": [
              [
                "type" : "zdb",
                "value": "1483109-0"
              ],
              [
                "type" : "eissn",
                "value": "1520-5118"
              ],
              [
                "type" : "issn",
                "value": "0021-8561"
              ]
            ],
            "name"       : "Journal of agricultural and food chemistry",
            "type"       : "Serial"
          ],
          "url"        : "http://pubs.acs.org/journal/jafcau"
        ]
      ]
    ]

    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferencePackage") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    then: "The item is created in the database because it does not exist"
    resp.json.message != null
    resp.json.message.startsWith('Created')
    expect: "Find pkg by name, which is connected to the new TIPP"
    def matching_pkgs = Package.findAllByName("American Chemical Society: ACS Legacy Archives")
    matching_pkgs.size() == 1
    matching_pkgs[0].id == resp.json.pkgId
    matching_pkgs[0].tipps?.size() == 1
    matching_pkgs[0].provider?.name == "American Chemical Society"
    matching_pkgs[0].ids?.size() == 1
  }

  void "Test crossReferencePackage with incomplete coverage dates"() {

    when: "Caller asks for this record to be cross referenced"
    def json_record = [
      "packageHeader": [
        "breakable"      : "No",
        "consistent"     : "Yes",
        "editStatus"     : "In Progress",
        "fixed"          : "No",
        "global"         : "Consortium",
        "identifiers"    : [
          [
            "type" : "isil",
            "value": "ZDB-1-ACS"
          ]
        ],
        "listStatus"     : "In Progress",
        "name"           : "American Chemical Society: ACS Legacy Archives: CompleteDates",
        "nominalPlatform": [
          "name"      : "ACS Publications",
          "primaryUrl": "https://pubs.acs.org"
        ],
        "nominalProvider": "American Chemical Society"
      ],
      "tipps"        : [
        [
          "accessEnd"   : "",
          "accessStart" : "",
          "coverage"    : [
            [
              "coverageDepth": "Fulltext",
              "coverageNote" : "NL-DE;  1.1953 - 43.1995",
              "embargo"      : "",
              "endDate"      : "1995",
              "endIssue"     : "",
              "endVolume"    : "43",
              "startDate"    : "1953-01",
              "startIssue"   : "",
              "startVolume"  : "1"
            ]
          ],
          "hostPlatform": [
            "name"      : "ACS Publications",
            "primaryUrl": "https://pubs.acs.org"
          ],
          "status"      : "Current",
          "title"       : [
            "identifiers": [
              [
                "type" : "zdb",
                "value": "1483109-0"
              ],
              [
                "type" : "eissn",
                "value": "1520-5118"
              ],
              [
                "type" : "issn",
                "value": "0021-8561"
              ]
            ],
            "name"       : "Journal of agricultural and food chemistry",
            "type"       : "Serial"
          ],
          "url"         : "http://pubs.acs.org/journal/jafcau"
        ]
      ]
    ]

    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferencePackage") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    then: "The item is created in the database because it does not exist"
    resp.json.message != null
    resp.json.message.startsWith('Created')
    expect: "The TIPP coverage dates are correctly set"
    def pkg = Package.get(resp.json.pkgId)
    pkg.tipps?.size() == 1
    def coverageStatement = pkg.tipps[0].coverageStatements[0]
    coverageStatement != null
    coverageStatement.startDate == Date.from(LocalDate.of(1953, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant())
    coverageStatement.endDate == Date.from(LocalDate.of(1995, 12, 31).atStartOfDay(ZoneId.systemDefault()).toInstant())
  }

  void "Test crossReferenceTitle BOOK Case 1"() {

    when: "Caller asks for this record to be cross referenced"
    def json_record = [
      "identifiers"    : [
        [
          "type" : "isbn",
          "value": "978-13-12232-23-5"
        ],
        [
          "type" : "doi",
          "value": "10.1515/pdtc"
        ]
      ],
      "name"           : "Test Book 1",
      "type"           : "monograph",
      "editionNumber"  : "4",
      "volumeNumber"   : "3",
      "firstAuthor"    : "J. Smith",
      "dateFirstOnline": "2019-01-01 00:00:00.000"
    ]
    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferenceTitle") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    then: "The item is created in the database because it does not exist"
    resp.json.message != null
    resp.json.message.startsWith('Created')
    expect: "Find item by ID can now locate that item and the discriminator is set correctly"
    def ids = [['ns': 'isbn', 'value': '978-13-12232-23-5']]
    def obj = TitleInstance.get(resp.json.titleId)
    obj?.ids?.collect { it.value == ids[0].value }
  }

  void "Test crossReferenceTitle identifier lock"() {
    when: "Caller asks for this record to be cross referenced"
    def json_record = [
      [
        "identifiers"    : [
          [
            "type" : "isbn",
            "value": "978-13-12232-23-9"
          ]
        ],
        "type"           : "Monograph",
        "name"           : "Test Book 1",
        "editionNumber"  : "4",
        "volumeNumber"   : "3",
        "firstAuthor"    : "J. Smith",
        "dateFirstOnline": "2019-01-01 00:00:00.000"
      ],
      [
        "identifiers"    : [
          [
            "type" : "isbn",
            "value": "978-13-12232-23-9"
          ]
        ],
        "name"           : "Test Book 1",
        "type"           : "Monograph",
        "editionNumber"  : "4",
        "volumeNumber"   : "3",
        "firstAuthor"    : "J. Smith",
        "dateFirstOnline": "2019-01-01 00:00:00.000"
      ]
    ]
    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferenceTitle") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    then: "Item is created in the database"
    resp.json.results?.size() == 2
    expect: "Find item by ID can now locate that item and the discriminator is set correctly"
    def ids = [['ns': 'isbn', 'value': '978-13-12232-23-9']]
    resp.json.results[0].titleId == resp.json.results[1].titleId
  }

  void "Test crossReferenceTitle with duplicate identifier"() {
    when: "Caller asks for this record to be cross referenced"
    def json_record = [
      "identifiers"    : [
        [
          "type" : "isbn",
          "value": "978-13-12232-23-8"
        ],
        [
          "type" : "isbn",
          "value": "978-13-12232-23-8"
        ]
      ],
      "type"           : "Monograph",
      "name"           : "Test Book 1",
      "editionNumber"  : "4",
      "volumeNumber"   : "3",
      "firstAuthor"    : "J. Smith",
      "dateFirstOnline": "2019-01-01 00:00:00.000"
    ]
    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferenceTitle") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    then: "Item is created in the database"
    resp.json.message.startsWith('Created')
    expect: "Find item by ID can now locate that item and the discriminator is set correctly"
    def ids = [['ns': 'isbn', 'value': '978-13-12232-23-8']]
    def ns = IdentifierNamespace.findByValueIlike('isbn')
    def id_num = Identifier.findAllByValueAndNamespace('978-13-12232-23-8', ns)
    def matching_with_class_one_ids = titleLookupService.matchClassOneComponentIds(ids)
    id_num.size() == 1
    matching_with_class_one_ids?.size() == 1
  }

  void "Test crossReferenceTitle with wrong type"() {
    when: "Caller asks for this record to be cross referenced"
    def book_record = [
      "identifiers": [
        [
          "type" : "isbn",
          "value": "978-13-12324-23-8"
        ]
      ],
      "name"       : "Test Book Missing Type"
    ]

    RestResponse respBook = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferenceTitle") {
      auth('admin', 'admin')
      body(book_record as JSON)
    }

    then: "Item is rejected"
    respBook.json.result == 'ERROR'
  }

  void "Test crossReferenceTitle multithreading"() {
    given:
    Resource journals = new ClassPathResource("/karger_journals_test.json")
    def jsonSlurper = new JsonSlurper()
    def journals_json = jsonSlurper.parse(journals.getFile())
    when: "Caller asks for this list of titles to be cross referenced"

    RestResponse respOne = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferenceTitle?async=true") {
      auth('admin', 'admin')
      body(journals_json as JSON)
    }

    RestResponse respTwo = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferenceTitle?") {
      auth('admin', 'admin')
      body(journals_json as JSON)
    }

    then: "Both calls are successful"
    respOne.json.job_id != null
    respTwo.json.results.size() > 0
    expect: "Find item by ID can now locate that item and the discriminator is set correctly"
    journals_json.each {
      def ids = []
      it.identifiers.each { idr ->
        if (idr.type == 'zdb') {
          ids << [ns: idr.type, value: idr.value]
        }
      }
      def matching_with_class_one_ids = titleLookupService.matchClassOneComponentIds(ids)
      matching_with_class_one_ids?.size() == 1
    }
  }

  void "Test crossReferenceTitle with prices"() {
    given:
    Resource journal = new ClassPathResource("/journal_prices_test.json")
    def jsonSlurper = new JsonSlurper()
    def journal_json = jsonSlurper.parse(journal.getFile())
    when: "Caller asks for this list of titles to be cross referenced"

    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferenceTitle?") {
      auth('admin', 'admin')
      body(journal_json as JSON)
    }

    then: "call is successful"
    resp.status == 200

    expect: "prices are set correctly"
    sleep(400)
    def title = TitleInstance.findById(resp.json.results[0].titleId)
    title.prices?.size() == 2
  }

  void "Test package update"() {
    given:
    def json_record = [
      "packageHeader": [
        "breakable"      : "No",
        "consistent"     : "Yes",
        "editStatus"     : "In Progress",
        "listStatus"     : "Checked",
        "fixed"          : "No",
        "global"         : "Consortium",
        "identifiers"    : [
          [
            "type" : "isil",
            "value": "ZDB-1-ACS"
          ]
        ],
        "name"           : "American Chemical Society: ACS Legacy Archives: UpdateListStatus",
        "nominalPlatform": [
          "name"      : "ACS Publications",
          "primaryUrl": "https://pubs.acs.org"
        ],
        "nominalProvider": "American Chemical Society"
      ],
      "tipps"        : [
        [
          "accessEnd"  : "",
          "accessStart": "",
          "coverage"   : [
            [
              "coverageDepth": "Fulltext",
              "coverageNote" : "NL-DE;  1.1953 - 43.1995",
              "embargo"      : "",
              "endDate"      : "1995",
              "endIssue"     : "",
              "endVolume"    : "43",
              "startDate"    : "1953-01",
              "startIssue"   : "",
              "startVolume"  : "1"
            ]
          ],
          "medium"     : "Electronic",
          "name"       : "TIPP Name",
          "platform"   : [
            "name"      : "ACS Publications",
            "primaryUrl": "https://pubs.acs.org"
          ],
          "prices"     : [
            [
              "type"     : "list",
              "currency" : "EUR",
              "amount"   : 123.45,
              "startDate": "2010-01-31"
            ],
            [
              "type"     : "topup",
              "currency" : "USD",
              "amount"   : 43.12,
              "startDate": "2020-01-01"
            ]
          ],
          "series"     : "Mystery Cloud",
          "status"     : "Current",
          "subjectArea": "Fringe",
          "title"      : [
            "identifiers": [
              [
                "type" : "zdb",
                "value": "1483109-0"
              ],
              [
                "type" : "eissn",
                "value": "1520-5118"
              ],
              [
                "type" : "issn",
                "value": "0021-8561"
              ]
            ],
            "name"       : "Journal of agricultural and food chemistry",
            "type"       : "Serial"
          ],
          "url"        : "http://pubs.acs.org/journal/jafcau"
        ]
      ]
    ]
    when: "Caller asks for this record to be cross referenced"

    RestResponse resp1 = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferencePackage") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    RestResponse resp2 = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferencePackage") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    then: "The item is created in the database because it does not exist"
    resp1.json?.message?.startsWith('Created')
    resp2.json?.message?.startsWith('Created')
    expect: "The TIPP coverage dates are correctly set"
    def pkg = Package.get(resp1.json.pkgId)
    pkg.tipps?.size() == 1
    pkg.tipps[0].name == "TIPP Name"
    pkg.tipps[0].subjectArea == "Fringe"
    pkg.tipps[0].prices.size() == 2
    pkg.listStatus?.value == "In Progress"
  }

  void "test update package via token"() {
    given:
    def json_record = [
      "updateToken"  : "TestUpdateToken",
      "packageHeader": [
        "breakable"      : "No",
        "consistent"     : "Yes",
        "editStatus"     : "In Progress",
        "listStatus"     : "Checked",
        "fixed"          : "No",
        "global"         : "Consortium",
        "identifiers"    : [
          [
            "type" : "isil",
            "value": "ZDB-1-ACS"
          ]
        ],
        "name"           : "TestTokenPackageUpdate",
        "nominalPlatform": [
          "name"      : "ACS Publications",
          "primaryUrl": "https://pubs.acs.org"
        ],
        "nominalProvider": "American Chemical Society"
      ],
      "tipps"        : [
        [
          "accessEnd"  : "",
          "accessStart": "",
          "coverage"   : [
            [
              "coverageDepth": "Fulltext",
              "coverageNote" : "NL-DE;  1.1953 - 43.1995",
              "embargo"      : "",
              "endDate"      : "1995",
              "endIssue"     : "",
              "endVolume"    : "43",
              "startDate"    : "1953-01",
              "startIssue"   : "",
              "startVolume"  : "1"
            ]
          ],
          "medium"     : "Electronic",
          "name"       : "TippName for Journal of agricultural and food chemistry",
          "platform"   : [
            "name"      : "ACS Publications",
            "primaryUrl": "https://pubs.acs.org"
          ],
          "status"     : "Current",
          "prices"     : [
            [
              "type"     : "list",
              "currency" : "EUR",
              "amount"   : 123.45,
              "startDate": "2010-01-31"
            ],
            [
              "type"     : "topup",
              "currency" : "USD",
              "amount"   : 43.12,
              "startDate": "2020-01-01"
            ]
          ],
          "status"     : "Current",
          "series"     : "Mystery Cloud",
          "subjectArea": "Fringe",
          "title"      : [
            "identifiers"      : [
              [
                "type" : "zdb",
                "value": "1483109-0"
              ],
              [
                "type" : "eissn",
                "value": "1520-5118"
              ],
              [
                "type" : "issn",
                "value": "0021-8561"
              ]
            ],
            "publisher_history": [
              [
                "endDate"  : "",
                "name"     : "ACS TestOrg",
                "startDate": "1990",
                "status"   : ""
              ]
            ],
            "name"             : "Journal of agricultural and food chemistry",
            "type"             : "Serial"
          ],
          "url"        : "http://pubs.acs.org/journal/jafcau"
        ]
      ]
    ]
    when: "Caller asks for this record to be cross referenced"

    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferencePackage") {
      body(json_record as JSON)
    }

    then: "The request is sucessfully processed"
    resp.json?.message?.startsWith('Created')
    expect: "The Package updater is set correctly"
    sleep(200)
    def pkg = Package.get(resp.json.pkgId)
    pkg.tipps?.size() == 1
    pkg.tipps[0].name.startsWith("TippName")
    pkg.lastUpdatedBy == User.findByUsername('ingestAgent')
    pkg.name == "TestTokenPackageUpdate"
    def title = JournalInstance.findByName("Journal of agricultural and food chemistry")
    title.publisher?.size() == 1
    title.publisher[0].name == "ACS TestOrg"
  }

  void "Update Title remove VariantName via fullsync"() {
    when: "Caller asks for this record to be cross referenced"
    def json_record = [
      "identifiers"    : [
        [
          "type" : "isbn",
          "value": "978-13-12232-23-8"
        ]
      ],
      "variantNames"   : [
        "TestVariantBookName"
      ],
      "type"           : "Monograph",
      "name"           : "Test Book 1",
      "editionNumber"  : "4",
      "volumeNumber"   : "3",
      "firstAuthor"    : "J. Smith",
      "dateFirstOnline": "2019-01-01 00:00:00.000"
    ]

    def json_update_record = [
      "identifiers"    : [
        [
          "type" : "isbn",
          "value": "978-13-12232-23-8"
        ]
      ],
      "type"           : "Monograph",
      "name"           : "Test Book 1",
      "editionNumber"  : "4",
      "volumeNumber"   : "3",
      "firstAuthor"    : "J. Smith",
      "dateFirstOnline": "2019-01-01 00:00:00.000"
    ]


    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferenceTitle") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    RestResponse update_resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferenceTitle?fullsync=true") {
      auth('admin', 'admin')
      body(json_update_record as JSON)
    }

    then: "Item is created in the database"
    resp.json.message.startsWith('Created')
    update_resp.json.message.startsWith('Created')
    expect: "Find item by ID can now locate that item and the discriminator is set correctly"
    resp.json.titleId == update_resp.json.titleId
    def bookInstance = BookInstance.get(update_resp.json.titleId)
    bookInstance?.variantNames?.size() == 0
  }

  void "Create Title with problematic characters"() {
    when: "Caller asks for this record to be cross referenced"
    def json_record = [
      "identifiers"    : [
        [
          "type" : "isbn",
          "value": "978-13-12112-23-2"
        ]
      ],
      "type"           : "Monograph",
      "name"           : "TestVariantBookName \"Quotes Test\"",
      "editionNumber"  : "4",
      "volumeNumber"   : "3",
      "firstAuthor"    : "J. Smith",
      "dateFirstOnline": "2019-01-01 00:00:00.000"
    ]

    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/crossReferenceTitle") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    then: "Item is created in the database"
    resp.json.message.startsWith('Created')
    expect: "Find item by ID can now locate that item and the discriminator is set correctly"
    def bookInstance = BookInstance.get(resp.json.titleId)
    bookInstance?.name == 'TestVariantBookName "Quotes Test"'
  }
}
