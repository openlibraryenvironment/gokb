package org.gokb

import org.gokb.TitleLookupService
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
import org.springframework.web.context.WebApplicationContext
import grails.transaction.Rollback

@Integration
@Rollback
class BulkImportSpec extends Specification {
  GrailsApplication grailsApplication

  @Autowired
  WebApplicationContext ctx

  @Shared
  RestBuilder rest = new RestBuilder()

  def setup() {
    def test_bulk_org = Org.findByName('TestBulkOrg') ?: new Org(name: 'TestBulkOrg').save(flush: true)
    def test_bulk_plt = Platform.findByName('TestBulkPlt') ?: new Platform(name: 'TestBulkPlt', primaryUrl: 'https://testbulkplt.org').save(flush: true)
    def bulk_cg = CuratoryGroup.findByName('TestBulkCG') ?: new CuratoryGroup(name: "TestBulkCG").save(flush: true)
    IdentifierNamespace test_idns = IdentifierNamespace.findByValue('bulktitlenamespace') ?: new IdentifierNamespace(value: 'bulktitlenamespace').save(failOnError: true, flush: true)
  }

  def cleanup() {
    CuratoryGroup.findByName('TestBulkCG')?.expunge()
    Platform.findByName('TestBulkPlt')?.expunge()
    Org.findByName('TestBulkOrg')?.expunge()
    Package.findByName('BulkTestPkgOne')?.expunge()
  }

  void "Test create new bulk config"() {
    given:
    def json_record = [
      code: 'testbulkimport',
      cfg: [
        collections: [
          [
            collection_name: "test_bulk_import_collection",
            scope: null,
            content_type: null,
            breakable: null,
            consistent: null,
            fixed: null,
            package_id_namespace: "bulktitlenamespace",
            title_id_namespace: null,
            package_source: "kbplus",
            package_provider: null,
            package_nominal_platform: null,
            package_curatory_group: "TestBulkCG",
            global: "Consortium",
            global_note: "BIBSAM",
            package_list: [
              [
                package_name: "BulkTestPkgOne",
                package_id: "btp1",
                package_source: "kbplus",
                package_provider: Org.findByName('TestBulkOrg').uuid,
                package_nominal_platform: Platform.findByName('TestBulkPlt').uuid,
                package_curatory_group: 'TestBulkCG',
                package_titlelist: "https://metadata.springernature.com/metadata/kbart/Springer_Global_J.B._Metzler_Humanities_eBooks_2005_2023-04-01.txt",
                package_id_namespace: null,
                package_content_type: "Journal",
                title_id_namespace: "doi",
                package_created_date: null,
                package_changed_date: null
              ]
            ]
          ]
        ]
      ]
    ]
    when: "Caller asks for this bulk config to be created"

    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/bulkImport/assertBulkConfig") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    then: "The request is successful"
    resp.status == 200
    expect:
    def new_config = BulkImportListConfig.findByCode('testbulkimport')
    new_config != null
    new_config.cfg != null
  }

  void "Test bulk update"() {
    def json_record = [
      code: 'testbulkimport',
      cfg: [
        collections: [
          [
            collection_name: "test_bulk_import_collection",
            scope: null,
            content_type: null,
            breakable: null,
            consistent: null,
            fixed: null,
            package_id_namespace: "bulktitlenamespace",
            title_id_namespace: null,
            package_source: "kbplus",
            package_provider: null,
            package_nominal_platform: null,
            package_curatory_group: "TestBulkCG",
            global: "Consortium",
            global_note: "BIBSAM",
            package_list: [
              [
                package_name: "BulkTestPkgOne",
                package_id: "btp1",
                package_source: "kbplus",
                package_provider: Org.findByName('TestBulkOrg').uuid,
                package_nominal_platform: Platform.findByName('TestBulkPlt').uuid,
                package_curatory_group: 'TestBulkCG',
                package_titlelist: "https://metadata.springernature.com/metadata/kbart/Springer_Global_J.B._Metzler_Humanities_eBooks_2005_2023-04-01.txt",
                package_id_namespace: null,
                package_content_type: "Journal",
                title_id_namespace: "doi",
                package_created_date: null,
                package_changed_date: null
              ]
            ]
          ]
        ]
      ]
    ]
    when: "Caller asks for this bulk config to be processed"

    RestResponse init_resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/bulkImport/assertBulkConfig") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    RestResponse resp = rest.get("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/bulkImport/runBulkUpdate?dryRun=true&async=true&code=testbulkimport") {
      auth('admin', 'admin')
    }

    then: "The request is successful"
    resp.status == 200

    expect:
    resp.json?.result == 'FINISHED'
    def pkg = Package.findByName('BulkTestPkgOne')
    pkg != null
    pkg.provider == Org.findByName('TestBulkOrg')
    pkg.nominalPlatform == Platform.findByName('TestBulkPlt')
    pkg.curatoryGroups[0].name == CuratoryGroup.findByName('TestBulkCG').name
    pkg.ids.size() == 1
    pkg.ids[0].namespace.value == 'bulktitlenamespace'
  }
}