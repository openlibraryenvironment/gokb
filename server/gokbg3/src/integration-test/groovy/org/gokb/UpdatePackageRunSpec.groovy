package org.gokb

import grails.converters.JSON
import grails.core.GrailsApplication
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.*
import org.gokb.TitleLookupService
import org.gokb.cred.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.WebApplicationContext
import spock.lang.Shared
import spock.lang.Specification

// For @Autowired

@Integration
@Rollback
class UpdatePackageRunSpec extends Specification {

  GrailsApplication grailsApplication

  @Autowired
  WebApplicationContext ctx

  HttpClient http

  // extending IntegrationSpec means this works
  @Autowired
  TitleLookupService titleLookupService

  private String getUrlPath() {
    return "http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}".toString()
  }

  def setup() {
    def new_cg = CuratoryGroup.findByName('TestGroup1') ?: new CuratoryGroup(name: "TestGroup1").save(flush: true)
    def acs_org = Org.findByName("American Chemical Society") ?: new Org(name: "American Chemical Society").save(flush: true)
    def acs_test_plt = Platform.findByName('ACS Publications') ?: new Platform(name: 'ACS Publications', primaryUrl: 'https://pubs.acs.org').save(flush: true)
    def test_upd_org = Org.findByName('ACS TestOrg') ?: new Org(name: 'ACS TestOrg').save(flush: true)
    def test_upd_pkg = Package.findByName('TestPackage') ?: new Package(name: 'TestPackage').save(flush: true)
    def test_journal = JournalInstance.findByName('TestJournal') ?: new JournalInstance(name: 'TestJournal').save(flush: true)
    Identifier book_doi = Identifier.findByValueAndNamespace('10.1021/978-3-16-148410-0', IdentifierNamespace.findByValue('doi')) ?: new Identifier(value: '10.1021/978-3-16-148410-0', namespace: IdentifierNamespace.findByValue('doi'))
    Identifier book_isbn = Identifier.findByValueAndNamespace('978-3-16-148410-0', IdentifierNamespace.findByValue('isbn')) ?: new Identifier(value: '978-3-16-148410-0', namespace: IdentifierNamespace.findByValue('isbn'))
    Identifier serial_issn = Identifier.findByValueAndNamespace('9783-442X', IdentifierNamespace.findByValue('issn')) ?: new Identifier(value: '9783-442X', namespace: IdentifierNamespace.findByValue('issn'))
    Identifier serial_eissn = Identifier.findByValueAndNamespace('9783-4420', IdentifierNamespace.findByValue('eissn')) ?: new Identifier(value: '9783-4420', namespace: IdentifierNamespace.findByValue('eissn'))

    def test_book = BookInstance.findByName('TestBook') ?: new BookInstance(name: 'TestBook').save(flush: true)
    def test_tipp1 = TitleInstancePackagePlatform.findByName('TestJournalTIPP') ?: new TitleInstancePackagePlatform(
        ['pkg'            : test_upd_pkg,
         'title'          : test_journal,
         'hostPlatform'   : acs_test_plt,
         'status'         : RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT),
         'name'           : 'TestJournalTIPP',
         'publicationType': RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, 'Serial'),
         'ids'            : [serial_issn, serial_eissn],
         'importId'       : 'titleID']).save(flush: true)
    def test_tipp2 = TitleInstancePackagePlatform.findByName('TestBookTIPP') ?: new TitleInstancePackagePlatform(
        ['pkg'            : test_upd_pkg,
         'title'          : test_book,
         'hostPlatform'   : acs_test_plt,
         'status'         : RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT),
         'name'           : 'TestBookTIPP',
         'publicationType': RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, 'Monograph'),
         'ids'            : [book_doi, book_isbn],
         'importId'       : 'bookID']).save(flush: true)


    def user = User.findByUsername('ingestAgent')
    if (!user.apiUserStatus) {
      UserRole.create(user, Role.findByAuthority('ROLE_API'), true)
    }

    test_book.ids.add(book_doi)
    test_book.ids.add(book_isbn)
    test_book.save(flush: true)

    test_journal.ids.add(serial_issn)
    test_journal.ids.add(serial_eissn)
    test_journal.save(flush: true)
  }

  def cleanup() {
    CuratoryGroup.findByName('TestGroup1')?.expunge()
    CuratoryGroup.findByName('TestGroup2')?.expunge()
    Org.findByName("American Chemical Society")?.expunge()
    Org.findByName('ACS TestOrg')?.expunge()
    Platform.findByName('ACS Publications')?.expunge()
    ['TestPackage',
     "American Chemical Society: ACS Legacy Archives"
    ].each { pkgName ->
      Package.findByName(pkgName)?.expunge()
    }
    TitleInstance.findAllByName("TestJournal")?.each { title ->
      title.expunge()
    }
    TitleInstance.findAllByName("TestBook")?.each { title ->
      title.expunge()
    }
    TitleInstance.findAllByName("TestJournal_Dates")?.each { title ->
      title.expunge()
    }
    ['9783-442X', '9783-4420', '9784-442X', '978-3-16-148410-0', '10.1021/978-3-16-148410-0'].each {
      Identifier.findByValue(it)?.expunge()
    }
    ['Journal of agricultural and food chemistry', 'Book of agricultural and food chemistry'].each {
      TitleInstance.findByName(it)?.expunge()
    }
    ['TestJournalTIPP', 'TestBookTIPP', 'Journal of agricultural and food chemistry', 'Book of agricultural and food chemistry'].each {
      TitleInstancePackagePlatform.findByName(it)?.expunge()
    }
    ReviewRequestAllocationLog.executeUpdate("delete from ReviewRequestAllocationLog")
    AllocatedReviewGroup.executeUpdate("delete from AllocatedReviewGroup")
    ReviewRequest.executeUpdate("delete from ReviewRequest")
  }

  void "Test updatePackageTipps :: match book by one of two identifiers"() {

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
            "name"           : "TestPackage",
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
                "titleId"    : "bookTitleID", // different
                "identifiers": [
                    [
                        "type" : "doi",
                        "value": "10.1021/978-3-16-148410-3"  // different
                    ],
                    [
                        "type" : "isbn",
                        "value": "978-3-16-148410-0"  // same (priority)
                    ]
                ],
                "coverage"   : [],
                "medium"     : "Book",
                "platform"   : [
                    "name"      : "ACS Publications",
                    "primaryUrl": "https://pubs.acs.org"
                ],
                "status"     : "Current",
                "editStatus" : "In Progress",
                "title"      : [
                    "identifiers": [
                        [
                            "type" : "doi",
                            "value": "10.1021/978-3-16-148410-3"  // different
                        ],
                        [
                            "type" : "isbn",
                            "value": "978-3-16-148410-0"  // same (priority)
                        ]
                    ],
                    "name"       : "Book of agricultural and food chemistry",
                    "publicationType"       : "Monograph"
                ],
                "name"       : "Book of agricultural and food chemistry",
                "publicationType"       : "Monograph",
                "url"        : "http://pubs.acs.org/journal/jafcau"
            ]
        ]
    ]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/updatePackageTipps", json_record).basicAuth('admin', 'admin')
    HttpResponse resp = http.toBlocking().exchange(request)

    then: "The item is found in the database based on the issn"
    resp.body().message != null
    resp.body().message.startsWith('Created/Updated')
    expect: "Find pkg by name, which is connected to the new TIPP"
    def matching_pkgs = Package.findAllByName("TestPackage")
    matching_pkgs.size() == 1
    matching_pkgs[0].id == resp.body().pkgId
    matching_pkgs[0].tipps?.size() == 3
    def book
    matching_pkgs[0].tipps.each { tipp ->
      if (tipp.importId == "bookTitleID")
        book = tipp
    }
    book.ids.size() == 2
  }

  void "Test updatePackageTipps :: match journal by one of two identifiers"() {

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
            "name"           : "TestPackage",
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
                "titleId"    : "thirdTitleID", // different
                "identifiers": [
                    [
                        "type" : "issn",
                        "value": "9783-442X"  // same
                    ],
                    [
                        "type" : "eissn",
                        "value": "9783-4429"  // different
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
                            "type" : "issn",
                            "value": "9783-442X"  // same
                        ],
                        [
                            "type" : "eissn",
                            "value": "9783-4429"  // different
                        ]
                    ],
                    "name"       : "Journal of agricultural and food chemistry",
                    "publicationType"       : "Serial"
                ],
                "name"       : "Journal of agricultural and food chemistry",
                "publicationType"       : "Serial",
                "url"        : "http://pubs.acs.org/journal/jafcau"
            ]
        ]
    ]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/updatePackageTipps", json_record).basicAuth('admin', 'admin')
    HttpResponse resp = http.toBlocking().exchange(request)

    then: "The item is found in the database based on the issn"
    resp.body().message != null
    resp.body().message.startsWith('Created/Updated')
    expect: "Find pkg by name, which is connected to the new TIPP"
    def matching_pkgs = Package.findAllByName("TestPackage")
    matching_pkgs.size() == 1
    matching_pkgs[0].id == resp.body().pkgId
    matching_pkgs[0].tipps?.size() == 3
    def journal
    matching_pkgs[0].tipps.each { tipp ->
      if (tipp.importId == "thirdTitleID")
        journal = tipp
    }
    journal.ids.size() == 2
  }

  void "Test updatePackageTipps :: match journal only by identifier"() {

    when: "Caller asks for this record to be cross referenced"
    def json_record = [
        "packageHeader": [
            "breakable": "No",
            "consistent": "Yes",
            "editStatus": "In Progress",
            "fixed": "No",
            "global": "Consortium",
            "identifiers" : [
                [
                    "type": "isil",
                    "value": "ZDB-1-ACS"
                ]
            ],
            "listStatus": "In Progress",
            "name": "TestPackage",
            "nominalPlatform": [
                "name": "ACS Publications",
                "primaryUrl": "https://pubs.acs.org"
            ],
            "nominalProvider": "American Chemical Society"
        ],
        "tipps"        : [
            [
                "accessEnd": "",
                "accessStart": "",
                "titleId": null,
                "identifiers": [
                    [
                        "type": "issn",
                        "value": "9783-442X"
                    ]
                ],
                "coverage": [
                    [
                        "coverageDepth": "Fulltext",
                        "coverageNote": "NL-DE;  1.1953 - 43.1995",
                        "embargo": "",
                        "endDate": "1995-12-31 00:00:00.000",
                        "endIssue": "",
                        "endVolume": "43",
                        "startDate": "1953-01-01 00:00:00.000",
                        "startIssue": "",
                        "startVolume": "1"
                    ]
                ],
                "medium": "Journal",
                "platform": [
                    "name": "ACS Publications",
                    "primaryUrl": "https://pubs.acs.org"
                ],
                "status": "Current",
                "editStatus": "In Progress",
                "title": [
                    "identifiers": [
                        [
                            "type": "zdb",
                            "value": "1483109-0"
                        ],
                        [
                            "type": "issn",
                            "value": "9783-442X"
                        ]
                    ],
                    "name": "Journal of agricultural and food chemistry",
                    "publicationType": "Serial"
                ],
                "name": "Journal of agricultural and food chemistry",
                "publicationType": "Serial",
                "url": "http://pubs.acs.org/journal/jafcau"
            ]
        ]
    ]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/updatePackageTipps", json_record).basicAuth('admin', 'admin')
    HttpResponse resp = http.toBlocking().exchange(request)

    then: "The item is found in the database based on the issn"
    resp.body().message != null
    resp.body().message.startsWith('Created/Updated')
    expect: "Find pkg by name, which is connected to the new TIPP"
    def matching_pkgs = Package.findAllByName("TestPackage")
    matching_pkgs.size() == 1
    matching_pkgs[0].id == resp.body().pkgId
    matching_pkgs[0].tipps.size() == 2
  }

  void "Test updatePackageTipps :: match by importId"() {

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
            "name"           : "TestPackage",
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
                "titleId"    : "titleID",
                "identifiers": [],
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
                    "identifiers": [],
                    "name"       : "Journal of agricultural and food chemistry",
                    "publicationType"       : "Serial"
                ],
                "name"       : "Journal of agricultural and food chemistry",
                "publicationType"       : "Serial",
                "url"        : "http://pubs.acs.org/journal/jafcau"
            ]
        ]
    ]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/updatePackageTipps", json_record).basicAuth('admin', 'admin')
    HttpResponse resp = http.toBlocking().exchange(request)

    then: "The item is created in the database because it does not exist"
    resp.body().message != null
    resp.body().message.startsWith('Created/Updated')
    expect: "Find pkg by name, which is connected to the new TIPP"
    def matching_pkgs = Package.findAllByName("TestPackage")
    matching_pkgs.size() == 1
    matching_pkgs[0].id == resp.body().pkgId
    matching_pkgs[0].tipps.size() == 2
    def journal = null
    matching_pkgs[0].tipps.each {
        if (it.importId == 'titleID') {
            journal = it
        }
    }
  }

  void "Test updatePackageTipps :: new record"() {

    when: "Caller asks for this record to be cross referenced"
    def json_record = [
        "packageHeader": [
            "breakable"      : "No",
            "consistent"     : "Yes",
            "activeCuratoryGroupId": CuratoryGroup.findByName('TestGroup1').id,
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
                "titleId"    : "wildeTitleId",
                "identifiers": [
                    [
                        "type" : "doi",
                        "value": "wildeTitleId"
                    ],
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
                    "publicationType"       : "Serial"
                ],
                "name"       : "Journal of agricultural and food chemistry",
                "publicationType"       : "Serial",
                "url"        : "http://pubs.acs.org/journal/jafcau"
            ]
        ]
    ]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/updatePackageTipps", json_record).basicAuth('admin', 'admin')
    HttpResponse resp = http.toBlocking().exchange(request)

    then: "The item is created in the database because it does not exist"
    resp.body()?.message != null
    resp.body().message.startsWith('Created')
    expect: "Find pkg by name, which is connected to the new TIPP"
    def matching_pkgs = Package.findAllByName("American Chemical Society: ACS Legacy Archives")
    matching_pkgs.size() == 1
    matching_pkgs[0].id == resp.body().pkgId
    matching_pkgs[0].tipps?.size() == 1
    matching_pkgs[0].tipps[0].importId == "wildeTitleId"
    matching_pkgs[0].provider?.name == "American Chemical Society"
    matching_pkgs[0].ids?.size() == 1
  }

  void "Test updatePackageTipps :: New tipp due to match by importId and eissn conflict"() {

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
            "name"           : "TestPackage",
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
                "titleId"    : "titleID",
                "identifiers": [
                    [
                        "type" : "eissn",
                        "value": "1520-1766"
                    ],
                    [
                        "type" : "issn",
                        "value": "9783-442X"
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
                            "type" : "eissn",
                            "value": "1520-1766"
                        ],
                        [
                            "type" : "issn",
                            "value": "9783-442X"
                        ]
                    ],
                    "name"       : "Journal of agricultural and food chemistry",
                    "publicationType"       : "Serial"
                ],
                "name"       : "Journal of agricultural and food chemistry",
                "publicationType"       : "Serial",
                "url"        : "http://pubs.acs.org/journal/jafcau"
            ]
        ]
    ]

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/integration/updatePackageTipps", json_record).basicAuth('admin', 'admin')
    HttpResponse resp = http.toBlocking().exchange(request)

    then: "The item is created in the database because it does not exist"
    resp.body().message != null
    resp.body().message.startsWith('Created/Updated')
    expect: "Find pkg by name, which is connected to the new TIPP"
    def rr_mismatch = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Import Identifier Mismatch')
    def matching_pkgs = Package.findAllByName("TestPackage")
    matching_pkgs.size() == 1
    matching_pkgs[0].id == resp.body().pkgId
    matching_pkgs[0].tipps.size() == 3
    int titleIdMatches = 0
    matching_pkgs[0].tipps.each {
        if (it.importId == 'titleID') {
            titleIdMatches++
        }
    }
    titleIdMatches == 2
    def conflict_reviews = ReviewRequest.findAllByStdDesc(rr_mismatch)
    conflict_reviews.size() == 1
  }
}
