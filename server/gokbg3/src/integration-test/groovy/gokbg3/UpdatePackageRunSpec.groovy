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

  void "Test updatePackageTipps :: Import a package without title matching"() {

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
                "titleId": "wildeTitleId",
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
                "editStatus"     : "In Progress",
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
