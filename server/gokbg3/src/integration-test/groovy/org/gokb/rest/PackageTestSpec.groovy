package org.gokb.rest

import grails.gorm.transactions.Transactional
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.*
import grails.converters.JSON
import org.gokb.cred.RefdataCategory
import org.gokb.cred.Source
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.web.context.WebApplicationContext

@Integration
@Rollback
class PackageTestSpec extends AbstractAuthSpec {

  @Autowired
  WebApplicationContext ctx

  private RestBuilder rest = new RestBuilder()

  def setup() {
    CuratoryGroup testGroup = CuratoryGroup.findByName("cgtest1") ?: new CuratoryGroup(name: "cgtest1").save(flush: true)
    Identifier book_doi = Identifier.findByValueAndNamespace('10.1021/978-3-16-148410-0', IdentifierNamespace.findByValue('doi')) ?: new Identifier(value: '10.1021/978-3-16-148410-0', namespace: IdentifierNamespace.findByValue('doi'))
    Identifier book_isbn = Identifier.findByValueAndNamespace('978-3-16-148410-0', IdentifierNamespace.findByValue('isbn')) ?: new Identifier(value: '978-3-16-148410-0', namespace: IdentifierNamespace.findByValue('isbn'))
    Identifier serial_issn = Identifier.findByValueAndNamespace('0020-0255', IdentifierNamespace.findByValue('issn')) ?: new Identifier(value: '0020-0255', namespace: IdentifierNamespace.findByValue('issn'))
    Identifier serial_eissn = Identifier.findByValueAndNamespace('1872-6291', IdentifierNamespace.findByValue('eissn')) ?: new Identifier(value: '1872-6291', namespace: IdentifierNamespace.findByValue('eissn'))
    Platform handlePlt = Platform.findByName("dx.doi.org") ?: new Platform(name: "dx.doi.org", primaryUrl: "http://dx.doi.org/", status: RefdataCategory.lookup('KBComponent.Status', 'Deleted')).save(flush: true)
    Platform testPlt = Platform.findByName("PackTestPlt") ?: new Platform(name: "PackTestPlt").save(flush: true)
    Org testOrg = Org.findByName("PackTestOrg") ?: new Org(name: "PackTestOrg").save(flush: true)
    testPlt.provider = testOrg
    testPlt.save(flush: true)

    def http = RefdataCategory.lookup('Source.DataSupplyMethod', 'HTTP Url').save(flush: true)
    def kbart = RefdataCategory.lookup('Source.DataFormat', 'KBART').save(flush: true)
    def freq = RefdataCategory.lookup('Source.Frequency', 'Weekly').save(flush: true)
    def combo_ids = RefdataCategory.lookup('Combo.Type', 'KBComponent.Ids').save(flush: true)
    Source testSource = Source.findByName("TestPack") ?: new Source(
        name: "TestPack",
        url: "https://org/package",
        frequency: freq,
        defaultSupplyMethod: http,
        defaultDataFormat: kbart).save(flush: true)

    Package testPackage = Package.findByName("TestPack") ?: new Package(name: "TestPack", source: testSource).save(flush: true)
    testPackage.nominalPlatform = testPlt
    testPackage.provider = testOrg
    testPackage.save(flush:true)

    Package urlTestPackage = new Package(name: "TestPackHandleUrl").save(flush: true)
    urlTestPackage.nominalPlatform = testPlt
    urlTestPackage.provider = testOrg
    urlTestPackage.save(flush: true)

    Package partialErrorPackage = new Package(name: "TestPackPartialError").save(flush: true)
    partialErrorPackage.nominalPlatform = testPlt
    partialErrorPackage.provider = testOrg
    partialErrorPackage.save(flush: true)

    JournalInstance testTitle = JournalInstance.findByName("PackTestTitle") ?: new JournalInstance(name: "PackTestTitle").save(flush: true)
    testTitle.ids.add(serial_issn)
    testTitle.ids.add(serial_eissn)
    testTitle.save(flush: true)

    def test_book = BookInstance.findByName('PackTestBook') ?: new BookInstance(name: 'PackTestBook').save(flush: true)
    test_book.ids.add(book_doi)
    test_book.ids.add(book_isbn)
    test_book.save(flush: true)

    if (!TitleInstancePackagePlatform.findByName('TestPackJournalTIPP')) {
      def test_tipp1 = new TitleInstancePackagePlatform([
          'name'           : 'TestPackJournalTIPP',
          'publicationType': RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, 'Serial'),
          'importId'       : 'packTitleID',
          'url'            : 'https://test.url/journal']).save(flush: true)

      test_tipp1.pkg = testPackage
      test_tipp1.title = testTitle
      test_tipp1.hostPlatform = testPlt
      test_tipp1.save(flush: true)

      new Combo(fromComponent: test_tipp1, toComponent: serial_issn, type: combo_ids).save(flush: true)
      new Combo(fromComponent: test_tipp1, toComponent: serial_eissn, type: combo_ids).save(flush: true)
    }

    if (!TitleInstancePackagePlatform.findByName('TestPackBookTIPP')) {
      def test_tipp2 = TitleInstancePackagePlatform.findByName('TestPackBookTIPP') ?: new TitleInstancePackagePlatform([
          'name'           : 'TestPackBookTIPP',
          'publicationType': RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, 'Monograph'),
          'importId'       : 'packBookID',
          'url'            : 'https://test.url/book']).save(flush: true)

      test_tipp2.pkg = testPackage
      test_tipp2.title = test_book
      test_tipp2.hostPlatform = testPlt
      test_tipp2.save(flush: true)

      new Combo(fromComponent: test_tipp2, toComponent: book_doi, type: combo_ids).save(flush: true)
      new Combo(fromComponent: test_tipp2, toComponent: book_isbn, type: combo_ids).save(flush: true)
    }
  }

  def cleanup() {
    ['TestPackJournalTIPP', 'TestJournalTIPPUpdate', 'TestPackBookTIPP', 'TestBookTIPPUpdate', 'TestJournalTIPPSkip', 'TIPP Name', 'Journal of agricultural and food chemistry', 'Book of agricultural and food chemistry'].each {
      TitleInstancePackagePlatform.findByName(it)?.expunge()
    }
    ["TestPack","UpdPack","TestPackageWithTipps","TestPackageWithProviderAndPlatform", "TestPackHandleUrl", "TestPackPartialError"].each {
      Package.findByName(it)?.expunge()
    }
    CuratoryGroup.findByName("cgtest1")?.expunge()
    JournalInstance.findByName("PackTestTitle")?.expunge()
    Platform.findByName("PackTestPlt")?.expunge()
    Platform.findByName("dx.doi.org")?.expunge()
    Org.findByName("PackTestOrg")?.expunge()
    Source.findByName("TestPack")?.expunge()
    BookInstance.findByName('PackTestBook')?.expunge()
  }

  void "test /rest/packages/<id> without token"() {
    given:
    def urlPath = getUrlPath()
    def testPackage = Package.findByName("TestPack")
    when:
    RestResponse resp = rest.get("${urlPath}/rest/packages/${testPackage.id}") {
      // headers
      accept('application/json')
    }
    then:
    resp.status == 200 // OK
  }

  void "test /rest/packages with valid token"() {
    given:
    def urlPath = getUrlPath()
    def testPackage = Package.findByName("TestPack")
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.get("${urlPath}/rest/packages/${testPackage.id}") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
    }
    then:
    resp.status == 200 // OK
    resp.json.name == "TestPack"
  }

  void "test /rest/packages update name"() {
    given:
    def upd_body = [name: 'UpdPack']
    def urlPath = getUrlPath()
    def testPackage = Package.findByName("TestPack")
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.put("${urlPath}/rest/packages/${testPackage.id}") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
      body(upd_body as JSON)
    }
    then:
    resp.status == 200 // OK
    resp.json.name == "UpdPack"
  }

  void "test /rest/packages update comboList"() {
    given:
    CuratoryGroup testGroup = CuratoryGroup.findByName("cgtest1")
    def upd_body = [curatoryGroups: [testGroup.id]]
    def urlPath = getUrlPath()
    def testPackage = Package.findByName("TestPack")
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.put("${urlPath}/rest/packages/${testPackage.id}") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
      body(upd_body as JSON)
    }
    then:
    resp.status == 200 // OK
    resp.json._embedded?.curatoryGroups?.size() == 1
    resp.json._embedded?.curatoryGroups[0].id == testGroup.id
  }

  void "test /rest/packages post with provider, source and platform"() {
    given:
    def testSource = Source.findByName("TestPack")
    Org testOrg = Org.findByName("PackTestOrg") ?: new Org(name: "PackTestOrg").save(flush: true)
    Platform testPlt = Platform.findByName("PackTestPlt") ?: new Platform(name: "PackTestPlt").save(flush: true)
    def new_body = [
        name           : "TestPackageWithProviderAndPlatform",
        breakable      : "Yes",
        consistent     : "Yes",
        description    : "kjkljslkdfsdf",
        descriptionURL : "https://heise.de",
        fixed          : "Yes",
        global         : "Consortium",
        globalNote     : "Testing Consortium",
        ids            : [
            [
                "value"    : "ZDB-1-TEST",
                "namespace": "isil"
            ]
        ],
        provider       : testOrg.id,
        nominalPlatform: testPlt.id,
        source         : testSource.id,
        scope          : [name: "Front File"]
    ]
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.post("${urlPath}/rest/packages") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
      body(new_body as JSON)
    }
    then:
    resp.json.errors == null
    resp.status == 201 // OK
    resp.json.source != null
    resp.json.provider != null
    resp.json.nominalPlatform != null
    resp.json.scope.name == "Front File"
    resp.json.globalNote == "Testing Consortium"
    resp.json._embedded?.ids?.size() == 1
  }

  void "test /rest/packages post with new tipps"() {
    given:
    JournalInstance testTitle = JournalInstance.findByName("PackTestTitle")
    Platform testPlt = Platform.findByName("PackTestPlt")
    def upd_body = [
        name : "TestPackageWithTipps",
        tipps: [
            [
                title       : testTitle.id,
                hostPlatform: testPlt.id,
                url         : "http://testpkgwithtipp.test",
                name        : "TIPP Name",
                prices      : [
                    [
                        type    : 'list',
                        amount  : 34.50,
                        currency: 'GBP'
                    ]
                ]
            ]
        ]
    ]
    def urlPath = getUrlPath()
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.post("${urlPath}/rest/packages?_embed=tipps") {
      // headers
      accept('application/json')
      auth("Bearer $accessToken")
      body(upd_body as JSON)
    }
    then:
    resp.status == 201 // OK
    resp.json?._embedded?.tipps?.size() == 1
    resp.json?._embedded?.tipps[0].url == upd_body.tipps[0].url
    resp.json?._embedded?.tipps[0].name == upd_body.tipps[0].name
  }

  void "test /rest/packages/<id>/ingest with matching tipps"() {
    given:
    def urlPath = getUrlPath()
    Resource kbart_file = new ClassPathResource("/test_rest_update.txt")
    def pkg = Package.findByName("TestPack")
    Platform testPlt = Platform.findByName("PackTestPlt")
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.post("${urlPath}/rest/packages/${pkg.id}/ingest?async=false") {
      accept('application/json')
      contentType("multipart/form-data")
      auth("Bearer $accessToken")
      submissionFile=kbart_file.getFile()
    }
    then:
    resp.status == 200
    resp.json.job_result.report?.matched == 2
    pkg.tipps.size() == 2
  }

  void "test /rest/packages/<id>/ingest with partial matching conflicts"() {
    given:
    def urlPath = getUrlPath()
    Resource kbart_file = new ClassPathResource("/test_rest_update_conflicts.txt")
    def pkg = Package.findByName("TestPack")
    Platform testPlt = Platform.findByName("PackTestPlt")
    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.post("${urlPath}/rest/packages/${pkg.id}/ingest?async=false") {
      accept('application/json')
      contentType("multipart/form-data")
      auth("Bearer $accessToken")
      submissionFile=kbart_file.getFile()
    }
    then:
    resp.status == 200
    resp.json?.job_result?.report?.partial == 2
    resp.json?.job_result?.report?.retired == 2
    resp.json?.job_result?.report?.reviews > 0
  }

  void "test /rest/packages/<id>/ingest platform fallback"() {
    given:
    def urlPath = getUrlPath()
    Resource kbart_file = new ClassPathResource("/test_rest_import_platform_fallback.txt")
    Package pkg = Package.findByName("TestPackHandleUrl")
    Platform handlePlt = Platform.findByName("dx.doi.org")

    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.post("${urlPath}/rest/packages/${pkg.id}/ingest?async=false") {
      accept('application/json')
      contentType("multipart/form-data")
      auth("Bearer $accessToken")
      submissionFile=kbart_file.getFile()
    }
    then:
    resp.status == 200
    resp.json?.job_result?.report?.created == 2
    !pkg.tipps*.hostPlatform.contains(handlePlt)
  }

  void "test /rest/packages/<id>/ingest with single invalid line"() {
    given:
    def urlPath = getUrlPath()
    Resource kbart_file = new ClassPathResource("/test_rest_import_partial_error.txt")
    Package pkg = Package.findByName("TestPackPartialError")

    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.post("${urlPath}/rest/packages/${pkg.id}/ingest?async=false") {
      accept('application/json')
      contentType("multipart/form-data")
      auth("Bearer $accessToken")
      submissionFile=kbart_file.getFile()
    }
    then:
    resp.status == 200
    resp.json?.job_result.report?.invalid == 1
    resp.json?.job_result?.validation?.rows.error == 1
  }

  void "test /rest/packages/<id>/ingest with single invalid line & skipInvalid"() {
    given:
    def urlPath = getUrlPath()
    Resource kbart_file = new ClassPathResource("/test_rest_import_partial_error.txt")
    Package pkg = Package.findByName("TestPackPartialError")

    when:
    String accessToken = getAccessToken()
    RestResponse resp = rest.post("${urlPath}/rest/packages/${pkg.id}/ingest?async=false&skipInvalid=true") {
      accept('application/json')
      contentType("multipart/form-data")
      auth("Bearer $accessToken")
      submissionFile=kbart_file.getFile()
    }
    then:
    resp.status == 200
    resp.json?.job_result?.report?.created == 1
    def invalid_tipps = TitleInstancePackagePlatform.executeQuery("from TitleInstancePackagePlatform as t where exists (select 1 from Combo where fromComponent = :pkg and type = :ct) and name = :inv", [pkg: pkg, ct: RefdataValue.findByValue('Package.Tipps'), inv: 'TestJournalTIPPSkip'])
    invalid_tipps.size() == 0
  }
}
