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
    IdentifierNamespace testidns = IdentifierNamespace.findByName('bulktitlenamespace') ?: new IdentifierNamespace(value: 'bulktitlenamespace').save(flush: true)
  }

  def cleanup() {
    CuratoryGroup.findByName('TestBulkCG')?.expunge()
    Platform.findByName('TestBulkPlt')?.expunge()
    Org.findByName('TestBulkOrg')?.expunge()
    Package.findByName('BulkTestPkgOne')?.expunge()
    BulkImportListConfig.findByCode('test_bulk_import')?.expunge()
  }

  void "Test create new bulk config"() {
    given:
    def json_record = [
      code: 'test_bulk_import',
      cfg: [
        collections: [
          [
            collection_name: "test_bulk_import_collection",
            scope: null,
            content_type: null,
            breakable: null,
            consistent: null,
            fixed: null,
            package_id_namespace: "kbplus",
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
                package_titlelist: "https://metadata.springernature.com/metadata/kbart/Springer_Global_Springer_Energy_eBooks_2013_English+International_2023-04-01.txt",
                package_id_namespace: null,
                package_content_type: "Journal",
                title_id_namespace: "DOI",
                package_created_date: null,
                package_changed_date: null
              ]
            ]
          ]
        ]
      ]
    ]
    when: "Caller asks for this bulk config to be created"

    RestResponse resp = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/assertBulkConfig") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    then: "The request is successful"
    resp.status == 200
    expect:
    def new_config = BulkImportListConfig.findByCode('test_bulk_import')
    new_config != null
    new_config.cfg != null
  }

  void "Test create use bulk"() {
    given:
    def json_record = [
      code: 'test_bulk_import',
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
            global_note: "TestBulkCollectionCG",
            package_list: [
              [
                package_name: "BulkTestPkgOne",
                package_id: "btp1",
                package_source: "kbplus",
                package_provider: Org.findByName('TestBulkOrg').uuid,
                package_nominal_platform: Platform.findByName('TestBulkPlt').uuid,
                package_curatory_group: 'TestBulkCG',
                package_titlelist: "https://metadata.springernature.com/metadata/kbart/Springer_Global_Springer_Energy_eBooks_2013_English+International_2023-04-01.txt",
                package_id_namespace: null,
                content_type: "Journal",
                title_id_namespace: "DOI",
                package_created_date: null,
                package_changed_date: null
              ]
            ]
          ]
        ]
      ]
    ]
    when: "Caller asks for this bulk config to be created"

    RestResponse resp_init = rest.post("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/integration/assertBulkConfig") {
      auth('admin', 'admin')
      body(json_record as JSON)
    }

    RestResponse resp = rest.get("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/admin/runBulkUpdate?dryRun=true&async=true") {
      auth('admin', 'admin')
    }

    then: "The request is successful"
    resp.status == 200

    expect:
    resp.json?.result == 'OK'
    def pkg = Package.findByName('BulkTestPkgOne')
    pkg != null
    pkg.provider == Org.findByName('TestBulkOrg')
    pkg.nominalPlatform == Platform.findByName('TestBulkPlt')
    pkg.curatoryGroups[0].name == CuratoryGroup.findByName('TestBulkCG')
    pkg.ids.size() == 1
    pkg.ids[0].namespace.value == 'bulktitlenamespace'
  }
}