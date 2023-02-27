package org.gokb

import grails.core.GrailsApplication
import grails.gorm.transactions.*
import grails.testing.mixin.integration.Integration

import groovy.json.JsonSlurper

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

import org.gokb.cred.*
import org.gokb.TitleLookupService
import org.springframework.beans.factory.annotation.*
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.web.context.WebApplicationContext

import spock.lang.Shared
import spock.lang.Specification

@Integration
@Rollback
class IntegrationControllerSpec extends Specification {

  GrailsApplication grailsApplication

  @Autowired
  WebApplicationContext ctx

  // extending IntegrationSpec means this works
  @Autowired
  TitleLookupService titleLookupService


  BlockingHttpClient client

  private String getUrlPath() {
    return "http://localhost:${serverPort}${grailsApplication.config.getProperty('server.contextPath', String) ?: ''}".toString()
  }

  def setup() {

    if (!client) {
      client = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }

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
    Org.findByName("American Chemical Society")?.expunge()
    Org.findByName('ACS TestOrg')?.expunge()
    Platform.findByName('ACS Publications')?.expunge()
    ['TestTokenPackageUpdate',
     'American Chemical Society: ACS Legacy Archives: CompleteDates',
     'TestTokenPackage',
     'American Chemical Society: ACS Legacy Archives: UpdateListStatus'].each {
      Package.findByName(it)?.expunge()
    }
    UpdateToken.findByValue('TestUpdateToken')?.delete()
    TitleInstance.findAllByName("Acta cytologica")?.each { title ->
      title.expunge()
    }
    TitleInstance.findAllByName("TestJournal_Dates")?.each { title ->
      title.expunge()
    }
    TitleInstancePackagePlatform.findAllByName("Journal of agricultural and food chemistry")?.each { title ->
      title.expunge()
    }
    def combo_plt = RefdataCategory.lookup('Combo.Type', 'Platform.HostedTipps')

    TitleInstancePackagePlatform.executeQuery("from TitleInstancePackagePlatform as t where not exists (select 1 from Combo where toComponent = t and type = :ct)", [ct: combo_plt])?.each { title ->
      title.expunge()
    }
    Identifier.findByValue('2256676-4')?.expunge()
  }

  void "Test assertGroup"() {

    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
        name: "TestGroup2",
        owner: "admin"
    ]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/assertGroup", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "The item is created up as it does not already exist"
    resp.body()?.groupId != null
    resp.body()?.message != null
    resp.body()?.result == 'OK'
    expect: "Find item by name only returns one item"
    def matching_groups = CuratoryGroup.executeQuery('select cg from CuratoryGroup as cg where cg.name = :n', [n: json_record.name]);
    matching_groups.size() == 1
    matching_groups[0].id == resp.body()?.groupId
  }

  void "Test assertOrg :: Import new Org"() {

    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
        "identifiers": [
            [
                "type" : "global",
                "value": "org-test-id-acs"
            ]
        ],
        "name"       : "TestOrgAcs"
    ]
    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/assertOrg", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "The item is created up as it does not already exist"
    resp.body()?.message != null
    resp.body()?.result == 'OK'
    expect: "Find item by name only returns one item"
    sleep(200)
    def matching_orgs = Org.executeQuery('select o from Org as o where o.name = :n', [n: json_record.name])
    matching_orgs.size() == 1
    matching_orgs[0].id == resp.body()?.orgId
    matching_orgs[0].ids?.size() == 1
  }

  void "Test crossReferencePlatform :: Import new Platform"() {

    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
        "platformName": "TestPlt1",
        "name"        : "TestPlt1",
        "platformUrl" : "https://acstest.url"
    ]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferencePlatform", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "The item was created"
    resp.body()?.message != null
    resp.body()?.result == "OK"
    expect: "Find item by name only returns one item"
    sleep(500)
    def matching_platforms = Platform.executeQuery('select p from Platform as p where p.name = :n', [n: json_record.platformName])
    matching_platforms.size() == 1
    matching_platforms[0].id == resp.body()?.platformId
  }

  void "Test crossReferenceTitle JOURNAL Case 1"() {

    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
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
                "value": "1021-8564"
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
    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "The item is created in the database because it does not exist"
    resp.body()?.message != null
    resp.body()?.result == 'OK'
    expect: "Find item by ID can now locate that item"
    def ids = [['ns': 'issn', 'value': '1021-8564']]
    def matching_with_class_one_ids = titleLookupService.matchClassOneComponentIds(ids)
    matching_with_class_one_ids?.size() == 1
    matching_with_class_one_ids[0] == resp.body()?.titleId
  }

  void "Test crossReferenceTitle with incomplete dates"() {

    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
        "identifiers"      : [
            [
                "type" : "zdb",
                "value": "2477443-1"
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
    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "The item is created in the database because it does not exist"
    resp.body()?.message != null
    resp.body()?.result == 'OK'
    expect: "Find item by ID can now locate that item"
    def title = TitleInstance.findById(resp.body()?.titleId)
    title != null
    title.publishedFrom?.toString() == "1953-01-01 00:00:00.0"
    title.publishedTo?.toString() == "2001-12-31 00:00:00.0"
    title.getCombosByPropertyName('publisher')?.size() == 1
    title.getCombosByPropertyName('publisher')[0].startDate?.toString() == "1953-01-01 00:00:00.0"
  }

  void "Test crossReferenceTitle :: Journal with history"() {

    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
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
    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "The item is created in the database because it does not exist"
    resp.body()?.message != null
    resp.body()?.result == 'OK'
    expect: "Find item by ID can now locate the history item"
    def ids = [['ns': 'eissn', 'value': '1541-5732']]
    def matching_with_class_one_ids = titleLookupService.matchClassOneComponentIds(ids)
    matching_with_class_one_ids?.size() == 1
  }

  void "Test updatePackageTipps :: Import a package without title matching"() {

    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
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
            "name"           : "American Chemical Society",
            "nominalPlatform": [
                "name"      : "ACS Publications",
                "primaryUrl": "https://pubs.acs.org"
            ],
            "nominalProvider": "American Chemical"
        ],
        "tipps"        : [
            [
                "accessEnd"  : "",
                "accessStart": "",
                "titleId"    : "wildeTitleId",
                "identifiers": [
                    [
                        "type" : "doi",
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
                "medium"     : "Journal",
                "platform"   : [
                    "name"      : "ACS Publications",
                    "primaryUrl": "https://pubs.acs.org"
                ],
                "status"     : "Current",
                "editStatus" : "In Progress",
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
                "name"       : "Journal of agricultural and food chemistry",
                "type"       : "Serial",
                "url"        : "http://pubs.acs.org/journal/jafcau"
            ]
        ]
    ]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "" +
        "/integration/updatePackageTipps", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "The item is created in the database because it does not exist"
    resp.body()?.message != null
    resp.body()?.result == 'OK'
    expect: "Find pkg by name, which is connected to the new TIPP"
    def matching_pkgs = Package.findAllByName("American Chemical Society")
    matching_pkgs.size() == 1
    matching_pkgs[0].id == resp.body()?.pkgId
    matching_pkgs[0].tipps?.size() == 1
    matching_pkgs[0].tipps[0].importId == "wildeTitleId"
    matching_pkgs[0].tipps[0].coverageStatements.size() == 1
    matching_pkgs[0].provider?.name == "American Chemical"
    matching_pkgs[0].ids?.size() == 1
  }

  void "Test crossReferencePackage with incomplete coverage dates"() {

    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
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

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferencePackage", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "The item is created in the database because it does not exist"
    resp.body()?.message != null
    resp.body()?.result == 'OK'
    expect: "The TIPP coverage dates are correctly set"
    def pkg = Package.get(resp.body()?.pkgId)
    pkg.tipps?.size() == 1
    def coverageStatement = pkg.tipps[0].coverageStatements[0]
    coverageStatement != null
    coverageStatement.startDate == Date.from(LocalDate.of(1953, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant())
    coverageStatement.endDate == Date.from(LocalDate.of(1995, 12, 31).atStartOfDay(ZoneId.systemDefault()).toInstant())
  }

  void "Test crossReferencePackage with additional TIPP props"() {

    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
        "packageHeader": [
            "breakable"      : "No",
            "consistent"     : "Yes",
            "editStatus"     : "In Progress",
            "fixed"          : "No",
            "global"         : "Consortium",
            "identifiers"    : [
                [
                    "type" : "isil",
                    "value": "ZDB-6-ACS"
                ]
            ],
            "listStatus"     : "In Progress",
            "name"           : "American Chemical Society with additional Props",
            "nominalPlatform": [
                "name"      : "ACS Publications",
                "primaryUrl": "https://pubs.acs.org"
            ],
            "nominalProvider": "American Chemical Society"
        ],
        "tipps"        : [
            [
                "accessEnd"                  : "",
                "accessStart"                : "",
                "coverage"                   : [
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
                "hostPlatform"               : [
                    "name"      : "ACS Publications",
                    "primaryUrl": "https://pubs.acs.org"
                ],
                "status"                     : "Current",
                "title"                      : [
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
                "firstAuthor"                : "erster Autor",
                "firstEditor"                : "erster Lektor",
                "publisherName"              : "publisher",
                "volumeNumber"               : "Volume 3",
                "editionStatement"           : "dritte Auflage",
                "parentPublicationTitleId"   : "elternPubTitelId",
                "precedingPublicationTitleId": "vorgängerPubTitelId",
                "publicationType"            : "Database",
                "medium"                     : "Other",
                "dateFirstInPrint"           : "2020-01-01",
                "dateFirstOnline"            : "2020-01-02",
                "lastChangedExternal"        : "2021-01-02",
                "url"                        : "http://pubs.acs.org/journal/jafcau"
            ]
        ]
    ]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferencePackage", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "The item is created in the database because it does not exist"
    resp.body()?.message != null
    resp.body()?.result == 'OK'
    expect: "The TIPP properties are correctly set"
    def pkg = Package.get(resp.body()?.pkgId)
    pkg.tipps?.size() == 1
    pkg.tipps[0].dateFirstInPrint != null
    pkg.tipps[0].medium.value == "Other"
    pkg.tipps[0].publicationType.value == "Database"
  }

  void "Test crossReferenceTitle BOOK Case 1"() {

    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
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
    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "The item is created in the database because it does not exist"
    resp.body()?.message != null
    resp.body()?.result == 'OK'
    expect: "Find item by ID can now locate that item and the discriminator is set correctly"
    def ids = [['ns': 'isbn', 'value': '978-13-12232-23-5']]
    def obj = TitleInstance.get(resp.body()?.titleId)
    obj?.ids?.collect { it.value == ids[0].value }
  }

  void "Test crossReferenceTitle identifier lock"() {
    when: "Caller asks for this record to be cross referenced"
    def json_record = [
        [
            "identifiers"    : [
                [
                    "type" : "isbn",
                    "value": "978-13-12232-24-2"
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
                    "value": "978-13-12232-24-2"
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
    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "Item is created in the database"
    resp.body()?.results?.size() == 2
    expect: "Find item by ID can now locate that item and the discriminator is set correctly"
    def ids = [['ns': 'isbn', 'value': '978-13-12232-24-2']]
    resp.body()?.results[0].titleId == resp.body()?.results[1].titleId
  }

  void "Test crossReferenceTitle with duplicate identifier"() {
    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
        "identifiers"    : [
            [
                "type" : "isbn",
                "value": "978-13-12232-25-9"
            ],
            [
                "type" : "isbn",
                "value": "978-13-12232-25-9"
            ]
        ],
        "type"           : "Monograph",
        "name"           : "Test Book 1",
        "editionNumber"  : "4",
        "volumeNumber"   : "3",
        "firstAuthor"    : "J. Smith",
        "dateFirstOnline": "2019-01-01 00:00:00.000"
    ]
    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "Item is created in the database"
    resp.body()?.result == 'OK'
    expect: "Find item by ID can now locate that item and the discriminator is set correctly"
    def ids = [['ns': 'isbn', 'value': '978-13-12232-25-9']]
    def ns = IdentifierNamespace.findByValueIlike('isbn')
    def id_num = Identifier.findAllByValueAndNamespace('978-13-12232-25-9', ns)
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

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", book_record).basicAuth('admin', 'admin')
    def resp = client.exchange(request, Map)

    then: "Item is rejected"
    resp.body()?.result == 'ERROR'
  }

  void "Test crossReferenceTitle with prices"() {
    given:
    Resource journal = new ClassPathResource("/journal_prices_test.json")
    def jsonSlurper = new JsonSlurper()
    def journal_json = jsonSlurper.parse(journal.getFile())
    when: "Caller asks for this list of titles to be cross referenced"

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle?", journal_json).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "call is successful"
    resp.status == HttpStatus.OK

    expect: "prices are set correctly"
    sleep(400)
    def title = TitleInstance.findById(resp.body()?.results[0].titleId)
    title.prices?.size() == 2
  }

  void "crossref package"() {
    given:
    Map json_record = [
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
                    "value": "ZDB-8-ACS"
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

    HttpRequest request1 = HttpRequest.POST(getUrlPath() + "/integration/crossReferencePackage", json_record).basicAuth('admin', 'admin')
    def resp1 =  client.exchange(request1, Map)

    HttpRequest request2 = HttpRequest.POST(getUrlPath() + "/integration/crossReferencePackage", json_record).basicAuth('admin', 'admin')
    def resp2 =  client.exchange(request2, Map)

    then: "The item is created in the database because it does not exist"
    resp1.body()?.result == 'OK'
    resp2.body()?.result == 'OK'
    expect: "The TIPP coverage dates are correctly set"
    def pkg = Package.get(resp1.body()?.pkgId)
    pkg.tipps?.size() == 1
    pkg.tipps[0].name == "TIPP Name"
    pkg.tipps[0].subjectArea == "Fringe"
    pkg.tipps[0].prices.size() == 2
    pkg.listStatus?.value == "In Progress"
  }

  void "crossref package with updateToken"() {
    given:
    Map json_record = [
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
                    "value": "ZDB-3-ACS"
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
                "accessEnd"      : "",
                "accessStart"    : "",
                "coverage"       : [
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
                "medium"         : "Electronic",
                "name"           : "TippName for Journal of agricultural and food chemistry",
                "platform"       : [
                    "name"      : "ACS Publications",
                    "primaryUrl": "https://pubs.acs.org"
                ],
                "status"         : "Current",
                "prices"         : [
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
                "status"         : "Current",
                "series"         : "Mystery Cloud",
                "subjectArea"    : "Fringe",
                "title"          : [
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
                    "publisherName"  : "ACS TestOrg",
                    "name"           : "Journal of agricultural and food chemistry",
                    "publicationType": "Serial",
                ],
                "publisherName"  : "ACS TestOrg",
                "publicationType": "Serial",
                "url"            : "http://pubs.acs.org/journal/jafcau"
            ]
        ]
    ]
    when: "Caller asks for this record to be cross referenced"

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferencePackage", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "The request is sucessfully processed"
    resp.body()?.result == 'OK'
    expect: "The Package updater is set correctly"
    sleep(200)
    def pkg = Package.get(resp.body()?.pkgId)
    pkg.tipps?.size() == 1
    pkg.tipps[0].name.startsWith("TippName")
    pkg.lastUpdatedBy.username == 'admin'
    pkg.name == "TestTokenPackageUpdate"
    def title = pkg.tipps[0].title //JournalInstance.findByName("Journal of agricultural and food chemistry")
    title.publisher?.size() == 1
    title.publisher[0].name == "ACS TestOrg"
    title.name =="Journal of agricultural and food chemistry"
  }

  void "Update Title remove VariantName via fullsync"() {
    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
        "identifiers"    : [
            [
                "type" : "isbn",
                "value": "978-13-12232-26-6"
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

    Map json_update_record = [
        "identifiers"    : [
            [
                "type" : "isbn",
                "value": "978-13-12232-26-6"
            ]
        ],
        "type"           : "Monograph",
        "name"           : "Test Book 1",
        "editionNumber"  : "4",
        "volumeNumber"   : "3",
        "firstAuthor"    : "J. Smith",
        "dateFirstOnline": "2019-01-01 00:00:00.000"
    ]


    HttpRequest request1 = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", json_record).basicAuth('admin', 'admin')
    def resp1 =  client.exchange(request1, Map)
    HttpRequest request2 = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle?fullsync=true", json_update_record).basicAuth('admin', 'admin')

    def resp2 =  client.exchange(request2, Map)

    then: "Item is created in the database"
    resp1.body().result == 'OK'
    resp2.body().result == 'OK'
    expect: "Find item by ID can now locate that item and the discriminator is set correctly"
    resp1.body()?.titleId == resp2.body()?.titleId
    def bookInstance = BookInstance.get(resp2.body()?.titleId)
    bookInstance?.variantNames?.size() == 0
  }

  void "Create Title with problematic characters"() {
    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
        "identifiers"    : [
            [
                "type" : "isbn",
                "value": "978-13-12112-23-0"
            ]
        ],
        "type"           : "Monograph",
        "name"           : "TestVariantBookName \"Quotes Test\"",
        "editionNumber"  : "4",
        "volumeNumber"   : "3",
        "firstAuthor"    : "J. Smith",
        "dateFirstOnline": "2019-01-01 00:00:00.000"
    ]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/crossReferenceTitle", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "Item is created in the database"
    resp.body()?.result == 'OK'
    expect: "Find item by ID can now locate that item and the discriminator is set correctly"
    def bookInstance = BookInstance.get(resp.body()?.titleId)
    bookInstance?.name == 'TestVariantBookName "Quotes Test"'
  }

  void "Test crossReferencePackage :: check response errors"() {

    when: "Caller asks for this record to be cross referenced"
    Map json_record = [
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
                "accessEnd"  : "1999-01-01",
                "accessStart": "1999-02-02",
                "identifiers": [
                    [
                        "type" : "global",
                        "value": "testTippId"
                    ],
                    [
                        "type" : "zdb",
                        "value": "1483109-0X"
                    ],
                    [
                        "type" : "eissn",
                        "value": "1520-5118-XXX"
                    ],
                    [
                        "type" : "issn",
                        "value": "0021-8561"
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
                            "value": "0021-8561-XXX"
                        ]
                    ],
                    "name"       : "Book of agricultural and food chemistry",
                    "firstAuthor": "Autor, extralong                                                                                                                                                                                                                                                                   ",
                    "firstEditor": "Editor, too long as well                                                                                                                                                                                                                                                           ",
                    "type"       : "Monograph"
                ],
                "url"        : "http://pubs.acs.org/journal/jafcau"
            ]
        ]
    ]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "" +
        "/integration/crossReferencePackage", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "The item is created in the database because it does not exist"
    resp.body()?.message != null
    resp.body()?.result == 'OK'
    expect: "Find errors in teh response JSON"
    resp.body()?.errors.tipps[0].index == 1
    resp.body()?.errors.tipps[0].title.identifiers.issn.baddata == "0021-8561-XXX"
    resp.body()?.errors.tipps[0].title.firstAuthor.message == "too long"
    resp.body()?.errors.tipps[0].title.firstEditor.message == "too long"
    resp.body()?.errors.tipps[0].tipp.identifiers.zdb.baddata == "1483109-0X"
  }

  void "Test updatePkgTipps update package via token"() {
    given:
    Map json_record = [
      updateToken: "TestUpdateToken",
      packageHeader: [
        breakable: "No",
        consistent: "Yes",
        editStatus: "In Progress",
        listStatus: "Checked",
        fixed: "No",
        global: "Consortium",
        identifiers: [
          [
            type: "isil",
            value: "ZDB-3-ACS"
          ]
        ],
        name: "TestTokenPackageUpdate",
        nominalPlatform: [
          name: "ACS Publications",
          primaryUrl: "https://pubs.acs.org"
        ],
        nominalProvider: "American Chemical Society"
      ],
      tipps: [
        [
          accessEnd: "",
          accessStart: "",
          medium: "Electronic",
          name: "TippName for Allgemeine und spezielle Pharmakologie",
          platform: [
            name: "ACS Publications",
            primaryUrl: "https://pubs.acs.org"
          ],
          prices: [
            [
              type: "list",
              currency: "EUR",
              amount: 123.45,
              startDate: "2010-01-31"
            ],
            [
              type: "topup",
              currency: "USD",
              amount: 43.12,
              startDate: "2020-01-01"
            ]
          ],
          status: "Current",
          series: "Mystery Cloud",
          subjectArea: "Fringe",
          dateFirstOnline: "2020-01-01",
          dateFirstInPrint: "2018",
          firstAuthor: "TestAuthor",
          firstEditor: "TestEditor",
          editionStatement: "1",
          volumeNumber: "87",
          coverage: [
            [
              coverageDepth: "fulltext"
            ]
          ],
          identifiers: [
            [
              type: "isbn",
              value: "978-3-437-42523-3"
            ]
          ],
          title: [
            identifiers: [
              [
                type: "isbn",
                value: "978-3-437-42523-3"
              ]
            ],
            publisher_history: [
              [
                endDate: "",
                name: "ACS TestOrg",
                startDate: "1990",
                status: ""
              ]
            ],
            name: "Allgemeine und spezielle Pharmakologie",
            publicationType: "Monograph"
          ],
          publisherName: "ACS TestOrg",
          publicationType: "Monograph",
          url: "http://pubs.acs.org/journal/jafcau"
        ]
      ]
    ]
    when: "Caller asks for this package to be imported"

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/updatePackageTipps?async=false", json_record).basicAuth('admin', 'admin')
    HttpResponse resp

    try {
      resp = client.exchange(request, Map)
    }
    catch (Exception e) {
      resp = e.response
    }

    then: "The request is sucessfully processed"
    resp.body()?.result == 'OK'
    expect: "The Package updater is set correctly"
    sleep(200)
    def pkg = Package.get(resp.body()?.pkgId)
    def current_tipps = TitleInstancePackagePlatform.executeQuery("from TitleInstancePackagePlatform as t where status = :sc and exists (select 1 from Combo where fromComponent = :pkg and toComponent = t)",
      [pkg: pkg, sc: RefdataCategory.lookup("KBComponent.Status", "Current")]
    )
    current_tipps?.size() == 1
    current_tipps[0].name.startsWith("TippName")
    current_tipps[0].dateFirstInPrint != null
    current_tipps[0].dateFirstOnline != null
    current_tipps[0].firstAuthor == "TestAuthor"
    current_tipps[0].firstEditor == "TestEditor"
    current_tipps[0].editionStatement != null
    current_tipps[0].volumeNumber != null
    pkg.name == "TestTokenPackageUpdate"
    def title = current_tipps[0].title //JournalInstance.findByName("Journal of agricultural and food chemistry")
    title?.publisher?.size() == 1
    title?.publisher[0].name == "ACS TestOrg"
    title?.name == current_tipps[0].name
  }
}
