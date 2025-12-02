package org.gokb

import grails.converters.JSON
import grails.core.GrailsApplication
import grails.gorm.transactions.*
import grails.testing.mixin.integration.Integration

import groovy.json.JsonSlurper

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.BlockingHttpClient

import org.gokb.cred.*
import org.springframework.beans.factory.annotation.*
import org.springframework.web.context.WebApplicationContext

import spock.lang.*

@Integration
@Rollback
class BulkImportSpec extends Specification {
  GrailsApplication grailsApplication

  @Autowired
  WebApplicationContext ctx

  BlockingHttpClient client

  private String getUrlPath() {
    return "http://localhost:${serverPort}${grailsApplication.config.getProperty('server.servlet.context-path', String) ?: ''}".toString()
  }

  def setup() {
    if (!client) {
      client = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }

    def test_bulk_org = Org.findByName('TestBulkOrg') ?: new Org(name: 'TestBulkOrg').save(flush: true)
    def test_bulk_plt = Platform.findByName('TestBulkPlt') ?: new Platform(name: 'TestBulkPlt', primaryUrl: 'https://testbulkplt.org').save(flush: true)
    def bulk_cg = CuratoryGroup.findByName('TestBulkCG') ?: new CuratoryGroup(name: "TestBulkCG").save(flush: true)
    def bulk_alt_cg = CuratoryGroup.findByName('TestBulkAlternativeCG') ?: new CuratoryGroup(name: "TestBulkAlternativeCG").save(flush: true)
    IdentifierNamespace test_idns = IdentifierNamespace.findByValue('bulktitlenamespace') ?: new IdentifierNamespace(value: 'bulktitlenamespace').save(failOnError: true, flush: true)

    Package test_bulk_pkg = Package.findByName('TestBulkPkgOld')

    if (!test_bulk_pkg) {
      Identifier pkg_id = Identifier.findByNamespaceAndValue(test_idns, "btp2") ?: new Identifier(namespace: test_idns, value: "btp2").save(flush: true, failOnError: true)
      test_bulk_pkg = new Package(name: 'TestBulkPkgOld', provider: test_bulk_org, nominalPlatform: test_bulk_plt).save(flush: true, failOnError: true)
      test_bulk_pkg.curatoryGroups << bulk_cg
      test_bulk_pkg.ids << pkg_id
      test_bulk_pkg.save(flush: true, failOnError: true)
    }
  }

  def cleanup() {
    Platform.findByName('TestBulkPlt')?.expunge()
    Org.findByName('TestBulkOrg')?.expunge()
    TitleInstancePackagePlatform.list().each {
      it.expunge()
    }
    TitleInstance.list().each {
      it.expunge()
    }

    ['BulkTestPkgOne','TestBulkPkgOld', 'TestBulkPkgNew'].each {
      Package.findByName(it)?.expunge()
      Source.findByName(it)?.expunge()
    }

    BulkImportListConfig.list().each {
      it.delete()
    }
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
                package_titlelist: "https://gokb.org/gokb/packages/kbart/60264065?exportType=tipp",
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

    HttpRequest request = HttpRequest.POST(getUrlPath() + "/bulkImport/assertBulkConfig", json_record).basicAuth('admin', 'admin')
    def resp =  client.exchange(request, Map)

    then: "The request is successful"
    resp.status == HttpStatus.OK
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
                package_titlelist: "https://gokb.org/gokb/packages/kbart/60264065?exportType=tipp",
                package_id_namespace: null,
                package_content_type: "Journal",
                title_id_namespace: "doi",
                package_created_date: null,
                package_changed_date: null,
                start_year: 2025,
                other_package_identifiers: [
                  [
                    namespace: 'isil',
                    value: 'ZDB-6-234'
                  ]
                ]
              ]
            ]
          ]
        ]
      ]
    ]
    when: "Caller asks for this bulk config to be processed"

    HttpRequest init_request = HttpRequest.POST(getUrlPath() + "/bulkImport/assertBulkConfig", json_record).basicAuth('admin', 'admin')
    client.exchange(init_request, Map)

    HttpRequest request = HttpRequest.GET(getUrlPath() + "/bulkImport/runBulkUpdate?dryRun=false&async=false&code=testbulkimport").basicAuth('admin', 'admin')
    HttpResponse resp = client.exchange(request, Map)

    then: "The request is successful"
    resp.status == HttpStatus.OK

    expect:
    resp.body().result == 'FINISHED'
    resp.body().report?.test_bulk_import_collection?.report != null
    def pkg = Package.findByName('BulkTestPkgOne')
    pkg != null
    pkg.provider == Org.findByName('TestBulkOrg')
    pkg.nominalPlatform == Platform.findByName('TestBulkPlt')
    pkg.curatoryGroups[0].name == CuratoryGroup.findByName('TestBulkCG').name
    pkg.ids.size() == 2
    pkg.ids.find { it.namespace.value == 'bulktitlenamespace' }
    pkg.ids.find { it.namespace.value == 'isil' }
  }

  void "Test bulk import with changed name"() {
    def json_record = [
      code: 'testbulkimport',
      updateOnly: false,
      updateNames: true,
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
                package_titlelist: "https://gokb.org/gokb/packages/kbart/60264065?exportType=tipp",
                package_id_namespace: null,
                package_content_type: "Journal",
                title_id_namespace: "doi",
                package_created_date: null,
                package_changed_date: null
              ],
              [
                package_name: "TestBulkPkgNew",
                package_id: "btp2",
                package_source: "kbplus",
                package_provider: Org.findByName('TestBulkOrg').uuid,
                package_nominal_platform: Platform.findByName('TestBulkPlt').uuid,
                package_curatory_group: 'TestBulkCG',
                package_titlelist: "https://gokb.org/gokb/packages/kbart/60264630?exportType=tipp",
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

    HttpRequest init_request = HttpRequest.POST(getUrlPath() + "/bulkImport/assertBulkConfig", json_record).basicAuth('admin', 'admin')
    client.exchange(init_request, Map)

    HttpRequest request = HttpRequest.GET(getUrlPath() + "/bulkImport/runBulkUpdate?dryRun=false&async=false&code=testbulkimport").basicAuth('admin', 'admin')
    HttpResponse resp = client.exchange(request, Map)

    then: "The request is successful"
    resp.status == HttpStatus.OK

    expect:
    resp.body().result == 'FINISHED'
    resp.body().report?.test_bulk_import_collection?.report != null
    def pkg = Package.findByName('BulkTestPkgOne')
    pkg != null
    pkg.provider == Org.findByName('TestBulkOrg')
    pkg.nominalPlatform == Platform.findByName('TestBulkPlt')
    pkg.curatoryGroups[0].name == CuratoryGroup.findByName('TestBulkCG').name
    pkg.ids.size() == 1
    pkg.ids[0].namespace.value == 'bulktitlenamespace'
    def pkg_updated = Package.findByName('TestBulkPkgNew')
  }

  void "Test bulk import update only"() {
    def json_record = [
      code: 'testbulkimport',
      updateOnly: true,
      updateNames: false,
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
                package_titlelist: "https://gokb.org/gokb/packages/kbart/60264065?exportType=tipp",
                package_id_namespace: null,
                package_content_type: "Journal",
                title_id_namespace: "doi",
                package_created_date: null,
                package_changed_date: null
              ],
              [
                package_name: "TestBulkPkgNew",
                package_id: "btp2",
                package_source: "kbplus",
                package_provider: Org.findByName('TestBulkOrg').uuid,
                package_nominal_platform: Platform.findByName('TestBulkPlt').uuid,
                package_curatory_group: 'TestBulkCG',
                package_titlelist: "https://gokb.org/gokb/packages/kbart/60264630?exportType=tipp",
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

    HttpRequest init_request = HttpRequest.POST(getUrlPath() + "/bulkImport/assertBulkConfig", json_record).basicAuth('admin', 'admin')
    client.exchange(init_request, Map)

    HttpRequest request = HttpRequest.GET(getUrlPath() + "/bulkImport/runBulkUpdate?dryRun=false&async=false&code=testbulkimport").basicAuth('admin', 'admin')
    HttpResponse resp = client.exchange(request, Map)

    then: "The request is successful"
    resp.status == HttpStatus.OK

    expect:
    resp.body().result == 'FINISHED'
    resp.body().report?.test_bulk_import_collection?.report != null
    Package.findByName('BulkTestPkgOne') == null
    Package.findByName('TestBulkPkgOld') != null
  }

  void "Test bulk import change curator"() {
    def json_record = [
      code: 'testbulkimport',
      curatorPolicy: 'New',
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
            global: "Consortium",
            global_note: "BIBSAM",
            package_list: [
              [
                package_name: "TestBulkPkgNew",
                package_id: "btp2",
                package_source: "kbplus",
                package_provider: Org.findByName('TestBulkOrg').uuid,
                package_nominal_platform: Platform.findByName('TestBulkPlt').uuid,
                package_curatory_group: 'TestBulkAlternativeCG',
                package_titlelist: "https://gokb.org/gokb/packages/kbart/60264630?exportType=tipp",
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

    HttpRequest init_request = HttpRequest.POST(getUrlPath() + "/bulkImport/assertBulkConfig", json_record).basicAuth('admin', 'admin')
    client.exchange(init_request, Map)

    HttpRequest request = HttpRequest.GET(getUrlPath() + "/bulkImport/runBulkUpdate?dryRun=false&async=false&code=testbulkimport").basicAuth('admin', 'admin')
    HttpResponse resp = client.exchange(request, Map)

    then: "The request is successful"
    resp.status == HttpStatus.OK

    expect:
    resp.body().result == 'FINISHED'
    resp.body().report?.test_bulk_import_collection?.report != null
    def pkg = Package.findByName('TestBulkPkgOld')
    def new_group = CuratoryGroup.findByName('TestBulkAlternativeCG')
    pkg != null
    pkg.refresh()
    sleep(1000)
    def new_curators = CuratoryGroup.executeQuery('''from CuratoryGroup as cg
                                                            where exists (
                                                              select 1 from Combo
                                                              where type = :cpcg
                                                              and toComponent = cg
                                                              and fromComponent = :pkg
                                                        )''', [cpcg: RefdataCategory.lookup('Combo.Type','Package.CuratoryGroups'), pkg: pkg])
    new_curators.size() == 1
    new_curators[0] == new_group

  }
}