package gokbg3

import grails.converters.JSON
import grails.core.GrailsApplication
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
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
    def test_upd_pkg = Package.findByName('TestPackage') ?: new Package(name: 'TestPackage').save(flush: true)
    def test_journal = JournalInstance.findByName('TestJournal') ?: new JournalInstance(name: 'TestJournal').save(flush: true)
    def test_tipp = TitleInstancePackagePlatform.findByName('TestTIPP') ?: new TitleInstancePackagePlatform(
        ['pkg'            : test_upd_pkg,
         'title'          : test_journal,
         'hostPlatform'   : acs_test_plt,
         'status'         : RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT),
         'name'           : 'TestTIPP',
         'publicationType': RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, 'Serial'),
         'ids'            : [Identifier.findByValue('9783-442X') ?: new Identifier(value: '9783-442X', namespace: IdentifierNamespace.findByValue('issn')),
                             Identifier.findByValue('9783-4420') ?: new Identifier(value: '9783-4420', namespace: IdentifierNamespace.findByValue('eissn'))],
         'importId'       : 'titleID']).save(flush: true)


    def user = User.findByUsername('ingestAgent')
    if (!user.apiUserStatus) {
      UserRole.create(user, Role.findByAuthority('ROLE_API'), true)
    }
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
    TitleInstance.findAllByName("TestJournal_Dates")?.each { title ->
      title.expunge()
    }
    ['9783-442X', '9783-4420'].each {
      Identifier.findByValue(it)?.expunge()
    }
  }

  void "Test updatePackageTipps :: match by one of two identifiers"() {

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
                            "type" : "eissn",
                            "value": "1520-5118"
                        ],
                        [
                            "type" : "issn",
                            "value": "9783-442X"
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

    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}" +
        "/integration/updatePackageTipps") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    then: "The item is found in the database based on the issn"
    resp.json.message != null
    resp.json.message.startsWith('Created/Updated')
    expect: "Find pkg by name, which is connected to the new TIPP"
    def matching_pkgs = Package.findAllByName("TestPackage")
    matching_pkgs.size() == 1
    matching_pkgs[0].id == resp.json.pkgId
    matching_pkgs[0].tipps?.size() == 1
    matching_pkgs[0].tipps[0].importId == "thirdTitleID"
    matching_pkgs[0].tipps[0].ids.size() == 3
    matching_pkgs[0].provider?.name == "American Chemical Society"
    matching_pkgs[0].ids?.size() == 1
  }

  void "Test updatePackageTipps :: match by identifier"() {

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
                "titleId"    : "otherTitleID",
                "identifiers": [
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
                            "type" : "zdb",
                            "value": "1483109-0"
                        ],
                        [
                            "type" : "eissn",
                            "value": "1520-5118"
                        ],
                        [
                            "type" : "issn",
                            "value": "9783-442X"
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

    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}" +
        "/integration/updatePackageTipps") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    then: "The item is found in the database based on the issn"
    resp.json.message != null
    resp.json.message.startsWith('Created/Updated')
    expect: "Find pkg by name, which is connected to the new TIPP"
    def matching_pkgs = Package.findAllByName("TestPackage")
    matching_pkgs.size() == 1
    matching_pkgs[0].id == resp.json.pkgId
    matching_pkgs[0].tipps?.size() == 1
    matching_pkgs[0].tipps[0].importId == "otherTitleID"
    matching_pkgs[0].provider?.name == "American Chemical Society"
    matching_pkgs[0].ids?.size() == 1
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
                "identifiers": [
                    [
                        "type" : "doi",
                        "value": "testTippId"
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
                    "type"       : "Serial"
                ],
                "name"       : "Journal of agricultural and food chemistry",
                "type"       : "Serial",
                "url"        : "http://pubs.acs.org/journal/jafcau"
            ]
        ]
    ]

    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}" +
        "/integration/updatePackageTipps") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    then: "The item is created in the database because it does not exist"
    resp.json.message != null
    resp.json.message.startsWith('Created/Updated')
    expect: "Find pkg by name, which is connected to the new TIPP"
    def matching_pkgs = Package.findAllByName("TestPackage")
    matching_pkgs.size() == 1
    matching_pkgs[0].id == resp.json.pkgId
    matching_pkgs[0].tipps?.size() == 1
    matching_pkgs[0].tipps[0].importId == "titleID"
    matching_pkgs[0].provider?.name == "American Chemical Society"
    matching_pkgs[0].ids?.size() == 1
  }

  void "Test updatePackageTipps :: new record"() {

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
                "titleId"    : "wildeTitleId",
                "identifiers": [
                    [
                        "type" : "doi",
                        "value": "testTippId"
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
                    "type"       : "Serial"
                ],
                "name"       : "Journal of agricultural and food chemistry",
                "type"       : "Serial",
                "url"        : "http://pubs.acs.org/journal/jafcau"
            ]
        ]
    ]

    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}" +
        "/integration/updatePackageTipps") {
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
    matching_pkgs[0].tipps[0].importId == "wildeTitleId"
    matching_pkgs[0].provider?.name == "American Chemical Society"
    matching_pkgs[0].ids?.size() == 1
  }

}
