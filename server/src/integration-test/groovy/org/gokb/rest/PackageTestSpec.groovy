package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.gorm.transactions.*
import grails.testing.mixin.integration.Integration

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.uri.UriBuilder

import java.time.Duration

import org.gokb.cred.*
import org.gokb.TitleLookupService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.web.context.WebApplicationContext

import spock.lang.Specification
import spock.lang.Shared

@Integration
@Rollback
class PackageTestSpec extends AbstractAuthSpec {

  @Autowired
  WebApplicationContext ctx

  @Autowired
  TitleLookupService titleLookupService

  BlockingHttpClient http

  def setup() {
    if (!http) {
      http = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }

    CuratoryGroup testGroup = CuratoryGroup.findByName("cgtest1") ?: new CuratoryGroup(name: "cgtest1").save(flush: true)
    Identifier book_doi = Identifier.findByValueAndNamespace('10.1021/978-3-16-148410-0', IdentifierNamespace.findByValue('doi')) ?: new Identifier(value: '10.1021/978-3-16-148410-0', namespace: IdentifierNamespace.findByValue('doi'))
    Identifier book_isbn = Identifier.findByValueAndNamespace('978-3-16-148410-0', IdentifierNamespace.findByValue('isbn')) ?: new Identifier(value: '978-3-16-148410-0', namespace: IdentifierNamespace.findByValue('isbn'))
    Identifier serial_issn = Identifier.findByValueAndNamespace('0020-0255', IdentifierNamespace.findByValue('issn')) ?: new Identifier(value: '0020-0255', namespace: IdentifierNamespace.findByValue('issn'))
    Identifier serial_eissn = Identifier.findByValueAndNamespace('1872-6291', IdentifierNamespace.findByValue('eissn')) ?: new Identifier(value: '1872-6291', namespace: IdentifierNamespace.findByValue('eissn'))
    IdentifierNamespace testJournalNs = IdentifierNamespace.findByValue('testj') ?: new IdentifierNamespace(value: 'testj').save(flush: true)
    IdentifierNamespace testMonoNs = IdentifierNamespace.findByValue('testm') ?: new IdentifierNamespace(value: 'testm').save(flush: true)
    Org testOrg = Org.findByName("PackTestOrg") ?: new Org(name: "PackTestOrg").save(flush: true)
    Platform handlePlt = Platform.findByName("dx.doi.org") ?: new Platform(name: "dx.doi.org", primaryUrl: "http://dx.doi.org/", status: RefdataCategory.lookup('KBComponent.Status', 'Deleted')).save(flush: true, failOnError: true)
    Platform testPlt = Platform.findByName("PackTestPlt") ?: new Platform(name: "PackTestPlt", provider: testOrg).save(flush: true, failOnError: true)

    RefdataValue http_method = RefdataCategory.lookup('Source.DataSupplyMethod', 'HTTP Url').save(flush: true)
    RefdataValue kbart = RefdataCategory.lookup('Source.DataFormat', 'KBART').save(flush: true)
    RefdataValue freq = RefdataCategory.lookup('Source.Frequency', 'Weekly').save(flush: true)
    Source testSource = Source.findByName("TestPack") ?: new Source(
        name: "TestPack",
        url: "https://org/package",
        frequency: freq,
        defaultSupplyMethod: http_method,
        defaultDataFormat: kbart).save(flush: true)

    Package testPackage = Package.findByName("TestPack")
    Package urlTestPackage = Package.findByName("TestPackHandleUrl")
    Package testPackageError = Package.findByName("TestPackPartialError")
    Package testPackageInitNoDates = Package.findByName("TestPackInitNoDates")
    Package testPackageInitWithDates = Package.findByName("TestPackInitWithDates")
    Package testPackageUpdateDates = Package.findByName("TestPackUpdateDates")
    Package testPackageNormNameMatch = Package.findByName("Test: Package")

    if (!testPackage) {
      testPackage = new Package(name: "TestPack", source: testSource, nominalPlatform: testPlt, provider: testOrg).save(flush: true, failOnError: true)
    }

    if (!urlTestPackage) {
      urlTestPackage = new Package(name: "TestPackHandleUrl", nominalPlatform: testPlt, provider: testOrg).save(flush: true)
    }

    if (!testPackageError) {
      testPackageError = new Package(name: "TestPackPartialError", nominalPlatform: testPlt, provider: testOrg).save(flush: true)

    }

    if (!testPackageInitNoDates) {
      testPackageInitNoDates = new Package(name: "TestPackInitNoDates", nominalPlatform: testPlt, provider: testOrg).save(flush: true)
    }

    if (!testPackageInitWithDates) {
      testPackageInitWithDates = new Package(name: "TestPackInitWithDates", nominalPlatform: testPlt, provider: testOrg).save(flush: true)

    }

    if (!testPackageUpdateDates) {
      testPackageUpdateDates = new Package(name: "TestPackUpdateDates", nominalPlatform: testPlt, provider: testOrg).save(flush: true)
    }

    if (!testPackageNormNameMatch) {
      testPackageUpdateDates = new Package(name: "Test: Package", nominalPlatform: testPlt, provider: testOrg).save(flush: true)
    }

    JournalInstance testTitle = JournalInstance.findByName("PackTestTitle")

    if (!testTitle) {
      testTitle = new JournalInstance(name: "PackTestTitle").save(flush: true)
      testTitle.addIdentifiers([serial_issn, serial_eissn])
    }

    BookInstance test_book = BookInstance.findByName('PackTestBook')

    if (!test_book) {
      test_book = new BookInstance(name: 'PackTestBook').save(flush: true)
      test_book.addIdentifiers([book_doi, book_isbn])
      test_book.save(flush: true)
    }

    if (!TitleInstancePackagePlatform.findByName('TestPackJournalTIPP')) {
      TitleInstancePackagePlatform test_tipp1 = new TitleInstancePackagePlatform([
        pkg: testPackage,
        hostPlatform: testPlt,
        title: testTitle,
        name: 'TestPackJournalTIPP',
        publicationType: RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, 'Serial'),
        importId: 'packTitleID',
        url: 'https://test.url/journal']).save(flush: true)

      test_tipp1.addIdentifiers([serial_issn, serial_eissn])
      test_tipp1.save(flush: true)
    }

    if (!TitleInstancePackagePlatform.findByName('TestPackBookTIPP')) {
      TitleInstancePackagePlatform test_tipp2 = TitleInstancePackagePlatform.findByName('TestPackBookTIPP') ?: new TitleInstancePackagePlatform([
        pkg: testPackage,
        hostPlatform: testPlt,
        title: test_book,
        name: 'TestPackBookTIPP',
        publicationType: RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, 'Monograph'),
        importId: 'packBookID',
        url: 'https://test.url/book']).save(flush: true)

      test_tipp2.addIdentifiers([book_doi, book_isbn])
    }
  }

  def cleanup() {
    [
      'TestPackJournalTIPP',
      'TestJournalTIPPUpdate',
      'TestJournalTIPPInit',
      'TestJournalTIPPInitRetired',
      'TestPackBookTIPP',
      'TestPackMixedJournal',
      'TestPackMixedBook',
      'TestBookTIPPUpdate',
      'TestBookTIPPInit',
      'TestJournalTIPPSkip',
      'TIPP Name',
      'Journal of agricultural and food chemistry',
      'Book of agricultural and food chemistry',
      'TestPackOtherTitle1',
      'TestPackOtherTitle2'
    ].each {
      TitleInstancePackagePlatform.findByName(it)?.expunge()
    }

    Source.findByName("TestPack")?.expunge()

    [
      "TestPack",
      "UpdPack",
      "TestPackageWithTipps",
      "TestPackageWithProviderAndPlatform",
      "TestPackHandleUrl",
      "TestPackPartialError",
      "TestPackInitNoDates",
      "TestPackInitWithDates",
      "TestPackUpdateDates",
      "Test: Package"
    ].each {
      Package.findByName(it)?.expunge()
    }

    [
      'PackTestTitle',
      'PackTestBook',
      'TestPackJournalTIPP',
      'TestJournalTIPPUpdate',
      'TestJournalTIPPInit',
      'TestJournalTIPPInitRetired',
      'TestPackBookTIPP',
      'TestPackMixedJournal',
      'TestPackMixedBook',
      'TestBookTIPPUpdate',
      'TestBookTIPPInit',
      'TestJournalTIPPSkip',
      'TIPP Name',
      'Journal of agricultural and food chemistry',
      'Book of agricultural and food chemistry',
      'TestPackOtherTitle1',
      'TestPackOtherTitle2'
    ].each {
      TitleInstance.findByName(it)?.expunge()
    }

    CuratoryGroup.findByName("cgtest1")?.expunge()
    Platform.findByName("PackTestPlt")?.expunge()
    Platform.findByName("dx.doi.org")?.expunge()
    Org.findByName("PackTestOrg")?.expunge()
  }

  void "test /rest/packages/<id> without token"() {
    given:
    String urlPath = getUrlPath()
    Package testPackage = Package.findByName("TestPack")
    when:
    HttpRequest request = HttpRequest.GET("${urlPath}/rest/packages/${testPackage.id}")
    HttpResponse resp = http.exchange(request)

    then:
    resp.status == HttpStatus.OK
  }

  void "test /rest/packages with valid token"() {
    given:
    String urlPath = getUrlPath()
    Package testPackage = Package.findByName("TestPack")
    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.GET("${urlPath}/rest/packages/${testPackage.id}")
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().name == "TestPack"
  }

  void "test /rest/packages update name"() {
    given:
    String urlPath = getUrlPath()
    Package testPackage = Package.findByName("TestPack")

    Map upd_body = [
      name: 'UpdPack'
    ]

    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.PATCH("${urlPath}/rest/packages/${testPackage.id}", upd_body)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().name == "UpdPack"
  }

  void "test /rest/packages update comboList"() {
    given:
    CuratoryGroup testGroup = CuratoryGroup.findByName("cgtest1")
    String urlPath = getUrlPath()
    Package testPackage = Package.findByName("TestPack")

    Map upd_body = [
      curatoryGroups: [testGroup.id],
      provider: testPackage.provider.id,
      nominalPlatform: testPackage.nominalPlatform.id
    ]

    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.PUT("${urlPath}/rest/packages/${testPackage.id}", upd_body)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body()._embedded?.curatoryGroups?.size() == 1
    resp.body()._embedded?.curatoryGroups[0].id == testGroup.id
  }

  void "test /rest/packages post with provider, source and platform"() {
    given:
    Source testSource = Source.findByName("TestPack")
    Org testOrg = Org.findByName("PackTestOrg") ?: new Org(name: "PackTestOrg").save(flush: true)
    Platform testPlt = Platform.findByName("PackTestPlt") ?: new Platform(name: "PackTestPlt").save(flush: true)
    Map new_body = [
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
    String urlPath = getUrlPath()

    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.POST("${urlPath}/rest/packages", new_body)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.body().errors == null
    resp.status == HttpStatus.CREATED
    resp.body().source != null
    resp.body().provider != null
    resp.body().nominalPlatform != null
    resp.body().scope.name == "Front File"
    resp.body().globalNote == "Testing Consortium"
    resp.body().breakable.name == "Yes"
    resp.body()._embedded?.ids?.size() == 1
  }

  void "test /rest/packages post with duplicate name"() {
    given:
    Source testSource = Source.findByName("TestPack")
    Org testOrg = Org.findByName("PackTestOrg") ?: new Org(name: "PackTestOrg").save(flush: true)
    Platform testPlt = Platform.findByName("PackTestPlt") ?: new Platform(name: "PackTestPlt").save(flush: true)
    Map new_body = [
        name           : "Testpack",
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
    String urlPath = getUrlPath()

    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.POST("${urlPath}/rest/packages", new_body)
      .bearerAuth(accessToken)
    HttpResponse resp

    try {
      resp = http.exchange(request, Map)
    } catch (Exception e) {
      resp = e.response
    }

    then:
    resp.body().errors?.name != null
    Package.findAllByNameIlike("TestPack").size() == 1
  }

  void "test /rest/packages post with duplicate normname"() {
    given:
    Org testOrg = Org.findByName("PackTestOrg") ?: new Org(name: "PackTestOrg").save(flush: true)
    Platform testPlt = Platform.findByName("PackTestPlt") ?: new Platform(name: "PackTestPlt").save(flush: true)
    Map new_body = [
        name           : "Test : Package",
        provider       : testOrg.id,
        nominalPlatform: testPlt.id
    ]
    String urlPath = getUrlPath()

    when:
    String accessToken = getAccessToken()
    HttpRequest request = HttpRequest.POST("${urlPath}/rest/packages", new_body)
      .bearerAuth(accessToken)
    HttpResponse resp

    try {
      resp = http.exchange(request, Map)
    } catch (Exception e) {
      resp = e.response
    }

    then:
    resp.body().errors?.name != null
    Package.findAllByNameIlike("Test : Package").size() == 0
  }

  void "test /rest/packages post with new tipps"() {
    given:
    JournalInstance testTitle = JournalInstance.findByName("PackTestTitle")
    Platform testPlt = Platform.findByName("PackTestPlt")
    Org provider = Org.findByName("PackTestOrg")

    Map upd_body = [
        name : "TestPackageWithTipps",
        provider: provider.id,
        nominalPlatform: testPlt.id,
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
    String urlPath = getUrlPath()

    when:
    String accessToken = getAccessToken()
    URI uri = UriBuilder.of(urlPath)
      .path("/rest/packages")
      .queryParam('_embed', 'tipps')
      .build()

    HttpRequest request = HttpRequest.POST(uri, upd_body)
      .bearerAuth(accessToken)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.CREATED
    resp.body()?._embedded?.tipps?.size() == 1
    resp.body()?._embedded?.tipps[0].url == upd_body.tipps[0].url
    resp.body()?._embedded?.tipps[0].name == upd_body.tipps[0].name
  }

  void "test /rest/packages/<id>/ingest with matching tipps"() {
    given:
    String urlPath = getUrlPath()
    Resource kbart_file = new ClassPathResource("/test_rest_update.txt")
    Package pkg = Package.findByName("TestPack")
    Platform testPlt = Platform.findByName("PackTestPlt")

    when:
    String accessToken = getAccessToken()
    MultipartBody requestBody = MultipartBody.builder()
      .addPart(
        "submissionFile",
        "test_rest_update.txt",
        MediaType.TEXT_PLAIN_TYPE,
        kbart_file.getFile()
      )
      .addPart('async', 'false')
      .build()

    HttpRequest request = HttpRequest.POST("${urlPath}/rest/packages/${pkg.id}/ingest", requestBody)
      .bearerAuth(accessToken)
      .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().job_result.report?.matched == 2
    pkg.tipps.size() == 2
  }

  void "test /rest/packages/<id>/ingest with partial matching conflicts"() {
    given:
    String urlPath = getUrlPath()
    Resource kbart_file = new ClassPathResource("/test_rest_update_conflicts.txt")
    Package pkg = Package.findByName("TestPack")
    Platform testPlt = Platform.findByName("PackTestPlt")

    when:
    String accessToken = getAccessToken()
    MultipartBody requestBody = MultipartBody.builder()
      .addPart(
        "submissionFile",
        "test_rest_update_conflicts.txt",
        MediaType.TEXT_PLAIN_TYPE,
        kbart_file.getFile()
      )
      .addPart('async', 'false')
      .build()

    HttpRequest request = HttpRequest.POST("${urlPath}/rest/packages/${pkg.id}/ingest", requestBody)
      .bearerAuth(accessToken)
      .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().job_result?.report?.partial == 2
    resp.body().job_result?.report?.retired == 2
    resp.body().job_result?.report?.reviews > 0
  }

  void "test /rest/packages/<id>/ingest platform fallback"() {
    given:
    String urlPath = getUrlPath()
    Resource kbart_file = new ClassPathResource("/test_rest_import_platform_fallback.txt")
    Package pkg = Package.findByName("TestPackHandleUrl")
    Platform handlePlt = Platform.findByName("dx.doi.org")

    when:
    String accessToken = getAccessToken()
    MultipartBody requestBody = MultipartBody.builder()
      .addPart(
        "submissionFile",
        "test_rest_update_conflicts.txt",
        MediaType.TEXT_PLAIN_TYPE,
        kbart_file.getFile()
      )
      .addPart('async', 'false')
      .build()

    HttpRequest request = HttpRequest.POST("${urlPath}/rest/packages/${pkg.id}/ingest", requestBody)
      .bearerAuth(accessToken)
      .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().job_result?.report?.created == 2
    !pkg.tipps*.hostPlatform.contains(handlePlt)
  }

  void "test /rest/packages/<id>/ingest with single invalid line"() {
    given:
    String urlPath = getUrlPath()
    Resource kbart_file = new ClassPathResource("/test_rest_import_partial_error.txt")
    Package pkg = Package.findByName("TestPackPartialError")

    when:
    String accessToken = getAccessToken()
    MultipartBody requestBody = MultipartBody.builder()
      .addPart(
        "submissionFile",
        "test_rest_import_partial_error.txt",
        MediaType.TEXT_PLAIN_TYPE,
        kbart_file.getFile()
      )
      .addPart('async', 'false')
      .build()

    HttpRequest request = HttpRequest.POST("${urlPath}/rest/packages/${pkg.id}/ingest", requestBody)
      .bearerAuth(accessToken)
      .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().job_result?.report?.invalid == 1
    resp.body().job_result?.validation?.rows.error == 1
  }

  void "test /rest/packages/<id>/ingest with single invalid line & skipInvalid"() {
    given:
    String urlPath = getUrlPath()
    Resource kbart_file = new ClassPathResource("/test_rest_import_partial_error.txt")
    Package pkg = Package.findByName("TestPackPartialError")

    when:
    String accessToken = getAccessToken()
    MultipartBody requestBody = MultipartBody.builder()
      .addPart(
        "submissionFile",
        "test_rest_import_partial_error.txt",
        MediaType.TEXT_PLAIN_TYPE,
        kbart_file.getFile()
      )
      .addPart('async', 'false')
      .addPart('skipInvalid', 'true')
      .build()

    HttpRequest request = HttpRequest.POST("${urlPath}/rest/packages/${pkg.id}/ingest", requestBody)
      .bearerAuth(accessToken)
      .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().job_result?.report?.created == 1
  }

  void "test /rest/packages/<id>/ingest initial load without access_start_date"() {
    given:
    String urlPath = getUrlPath()
    Resource kbart_file = new ClassPathResource("/test_rest_initial_no_access.txt")
    Package pkg = Package.findByName("TestPackInitNoDates")

    when:
    String accessToken = getAccessToken()
    MultipartBody requestBody = MultipartBody.builder()
      .addPart(
        "submissionFile",
        "test_rest_initial_no_access.txt",
        MediaType.TEXT_PLAIN_TYPE,
        kbart_file.getFile()
      )
      .addPart('async', 'false')
      .build()

    HttpRequest request = HttpRequest.POST("${urlPath}/rest/packages/${pkg.id}/ingest", requestBody)
      .bearerAuth(accessToken)
      .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().job_result?.report?.created == 2
    TitleInstancePackagePlatform.findByName('TestJournalTIPPInit')?.accessStartDate == null
  }

  void "test /rest/packages/<id>/ingest initial load with access_start_date"() {
    given:
    String urlPath = getUrlPath()
    Resource kbart_file = new ClassPathResource("/test_rest_initial_access_dates.txt")
    Package pkg = Package.findByName("TestPackInitWithDates")

    when:
    String accessToken = getAccessToken()
    MultipartBody requestBody = MultipartBody.builder()
      .addPart(
        "submissionFile",
        "test_rest_initial_access_dates.txt",
        MediaType.TEXT_PLAIN_TYPE,
        kbart_file.getFile()
      )
      .addPart('async', 'false')
      .build()


    HttpRequest request = HttpRequest.POST("${urlPath}/rest/packages/${pkg.id}/ingest", requestBody)
      .bearerAuth(accessToken)
      .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().job_result?.report?.created == 4
    TitleInstancePackagePlatform.findByName('TestJournalTIPPInit')?.accessStartDate != null
    TitleInstancePackagePlatform.findByName('TestJournalTIPPInitRetired')?.accessEndDate != null
    TitleInstancePackagePlatform.findByName('TestBookTIPPInit')?.accessEndDate != null
    TitleInstancePackagePlatform.findByName('TestBookTIPPInit')?.status?.value == 'Retired'
  }

  void "test /rest/packages/<id>/ingest update access dates"() {
    given:
    String urlPath = getUrlPath()
    Resource kbart_file = new ClassPathResource("/test_rest_initial_no_access.txt")
    Resource kbart_file_update = new ClassPathResource("/test_rest_initial_access_dates.txt")
    Package pkg = Package.findByName("TestPackUpdateDates")

    when:
    String accessToken = getAccessToken()
    MultipartBody requestBody = MultipartBody.builder()
      .addPart(
        "submissionFile",
        "test_rest_initial_no_access.txt",
        MediaType.TEXT_PLAIN_TYPE,
        kbart_file.getFile()
      )
      .addPart('async', 'false')
      .build()

    HttpRequest request = HttpRequest.POST("${urlPath}/rest/packages/${pkg.id}/ingest", requestBody)
      .bearerAuth(accessToken)
      .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
    HttpResponse respInit = http.exchange(request, Map)

    MultipartBody requestBodyUpdate = MultipartBody.builder()
      .addPart(
        "submissionFile",
        "test_rest_initial_access_dates.txt",
        MediaType.TEXT_PLAIN_TYPE,
        kbart_file_update.getFile()
      )
      .addPart('async', 'false')
      .build()

    HttpRequest updateRequest = HttpRequest.POST("${urlPath}/rest/packages/${pkg.id}/ingest", requestBodyUpdate)
      .bearerAuth(accessToken)
      .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
    HttpResponse resp = http.exchange(updateRequest, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().job_result?.report?.matched == 2
    resp.body().job_result?.report?.created == 2
    TitleInstancePackagePlatform.findByName('TestJournalTIPPInit')?.accessStartDate != null
    TitleInstancePackagePlatform.findByName('TestJournalTIPPInitRetired')?.accessEndDate != null
    TitleInstancePackagePlatform.findByName('TestBookTIPPUpdate')?.accessStartDate != null
    TitleInstancePackagePlatform.findByName('TestBookTIPPInit')?.accessEndDate != null
    TitleInstancePackagePlatform.findByName('TestBookTIPPInit')?.status?.value == 'Retired'
  }


  void "test /rest/packages/<id>/ingest with mixed package and separate namespaces"() {
    given:
    String urlPath = getUrlPath()
    Resource kbart_file = new ClassPathResource("/test_rest_mixed_valid_separate_namespaces.txt")
    Package pkg = Package.findByName("TestPack")
    IdentifierNamespace testJournalNs = IdentifierNamespace.findByValue('testj')
    IdentifierNamespace testMonoNs = IdentifierNamespace.findByValue('testm')

    when:
    String accessToken = getAccessToken()
    MultipartBody requestBody = MultipartBody.builder()
      .addPart(
        "submissionFile",
        "test_rest_mixed_valid_separate_namespaces.txt",
        MediaType.TEXT_PLAIN_TYPE,
        kbart_file.getFile()
      )
      .addPart('async', 'false')
      .addPart('titleIdSerial', "${testJournalNs.id}")
      .addPart('titleIdMonograph', "${testMonoNs.id}")
      .build()

    HttpRequest request = HttpRequest.POST("${urlPath}/rest/packages/${pkg.id}/ingest", requestBody)
      .bearerAuth(accessToken)
      .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    TitleInstancePackagePlatform.findByName('TestPackMixedJournal')?.ids.find { it.namespace == testJournalNs }
    TitleInstancePackagePlatform.findByName('TestPackMixedBook')?.ids.find { it.namespace == testMonoNs }
  }

  void "test /rest/packages/<id>/ingest with publication_type 'other'"() {
    given:
    String urlPath = getUrlPath()
    Resource kbart_file = new ClassPathResource("/test_rest_other_separate_namespaces.txt")
    Package pkg = Package.findByName("TestPackHandleUrl")
    IdentifierNamespace testJournalNs = IdentifierNamespace.findByValue('testj')
    IdentifierNamespace testMonoNs = IdentifierNamespace.findByValue('testm')

    when:
    String accessToken = getAccessToken()
    MultipartBody requestBody = MultipartBody.builder()
      .addPart(
        "submissionFile",
        "test_rest_other_separate_namespaces.txt",
        MediaType.TEXT_PLAIN_TYPE,
        kbart_file.getFile()
      )
      .addPart('async', 'false')
      .addPart('titleIdSerial', "${testJournalNs.id}")
      .addPart('titleIdMonograph', "${testMonoNs.id}")
      .build()

    HttpRequest request = HttpRequest.POST("${urlPath}/rest/packages/${pkg.id}/ingest", requestBody)
      .bearerAuth(accessToken)
      .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
    HttpResponse resp = http.exchange(request, Map)

    then:
    resp.status == HttpStatus.OK
    resp.body().job_result.report.created == 2
    pkg.refresh().tipps?.size() == 2
    TitleInstancePackagePlatform.findByName('TestPackOtherTitle1')?.ids.find { it.namespace == testMonoNs }
    TitleInstancePackagePlatform.findByName('TestPackOtherTitle2')?.ids.find { it.namespace == testMonoNs }
  }
}
