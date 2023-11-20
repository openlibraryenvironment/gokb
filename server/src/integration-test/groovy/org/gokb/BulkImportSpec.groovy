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
    IdentifierNamespace test_idns = IdentifierNamespace.findByValue('bulktitlenamespace') ?: new IdentifierNamespace(value: 'bulktitlenamespace').save(failOnError: true, flush: true)
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
  }
}