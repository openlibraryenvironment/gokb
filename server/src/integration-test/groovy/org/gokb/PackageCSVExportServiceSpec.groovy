import com.k_int.ConcurrencyManagerService
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVParser
import com.opencsv.CSVParserBuilder

import gokbg3.DateFormatService

import grails.testing.mixin.integration.Integration
import grails.testing.services.ServiceUnitTest

import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime

import org.gokb.*
import org.gokb.cred.BookInstance
import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.JournalInstance
import org.gokb.cred.KBComponent
import org.gokb.cred.Org
import org.gokb.cred.Package
import org.gokb.cred.Platform
import org.gokb.cred.RefdataCategory
import org.gokb.cred.ReviewRequest
import org.gokb.cred.TitleInstance
import org.gokb.cred.TitleInstancePackagePlatform
import org.gokb.cred.Combo
import org.hibernate.SessionFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.Rollback
import org.springframework.transaction.annotation.Transactional

import spock.lang.Specification

@Integration
@Transactional
@Rollback
class PackageCSVExportServiceSpec extends Specification {

  @Autowired
  PackageCSVExportService packageCSVExportService

  @Autowired
  TippService tippService

  @Autowired
  TippUpsertService tippUpsertService

  @Autowired
  DateFormatService dateFormatService

  @Autowired
  SessionFactory sessionFactory

  @Autowired
  ConcurrencyManagerService concurrencyManagerService

  def grailsApplication

  String filePath

  IdentifierNamespace issn_ns
  IdentifierNamespace eissn_ns
  IdentifierNamespace isbn_ns

  Identifier isbn
  Identifier pisbn
  Identifier issn
  Identifier eissn
  Identifier eissn2

  Platform testPlt
  Org testOrg

  BookInstance book
  JournalInstance journal1
  JournalInstance journal2

  def setup() {
    filePath = packageCSVExportService.exportFilePath()

    testOrg = Org.findByName('PackageService Test Org') ?: new Org(name: 'PackageService Test Org').save(flush: true)
    testPlt = Platform.findByName('PackageService Test Platform') ?: new Platform(name: 'PackageService Test Platform', provider: testOrg).save(flush: true)

    Package testPkg1 = Package.findByName('PackageService Test Package') ?: new Package(name: 'PackageService Test Package', provider: testOrg).save(flush: true)
    Package testPkg2 = Package.findByName('PackageService Test AddJournal') ?: new Package(name: 'PackageService Test AddJournal', provider: testOrg).save(flush: true)
    Package testPkg3 = Package.findByName('PackageService Test FirstLine') ?: new Package(name: 'PackageService Test FirstLine', provider: testOrg).save(flush: true)


    if (!issn_ns) {
      issn_ns = IdentifierNamespace.findByValue('issn')
    }
    if (!eissn_ns) {
      eissn_ns = IdentifierNamespace.findByValue('eissn')
    }
    if (!isbn_ns) {
      isbn_ns = IdentifierNamespace.findByValue('isbn')
    }

    isbn = Identifier.findByNamespaceAndValue(isbn_ns, '979-11-655-6390-5') ?: new Identifier(namespace: isbn_ns, value: '979-11-655-6390-5').save(flush: true)
    pisbn = Identifier.findByNamespaceAndValue(isbn_ns, '979-11-655-6390-5') ?: new Identifier(namespace: isbn_ns, value: '979-11-655-6390-5').save(flush: true)
    issn = Identifier.findByNamespaceAndValue(issn_ns, '0128-5483') ?: new Identifier(namespace: issn_ns, value: '0128-5483')
    eissn = Identifier.findByNamespaceAndValue(eissn_ns, '2180-4338') ?: new Identifier(namespace: eissn_ns, value: '2180-4338')
    eissn2 = Identifier.findByNamespaceAndValue(eissn_ns, '1727-9445') ?: new Identifier(namespace: eissn_ns, value: '1727-9445')

    def book = BookInstance.findByName('PackageService Book 1')

    if (!book) {
      book = new BookInstance(name: 'PackageService Book 1').save(flush:true)
      book.ids.add(isbn)
      book.save(flush: true)
    }

    if (!TitleInstancePackagePlatform.findByName('PackageService BookTipp 1')) {

      def tipp_map = [
        pkg: testPkg1.id,
        hostPlatform: testPlt.id,
        name: 'PackageService BookTipp 1',
        url: 'https://package-caching-test.test/book1',
        editStatus: 'Approved',
        publicationType: 'Monograph',
        importId: 'pcsB1',
        firstAuthor: 'Author1',
        firstEditor: 'Editor1',
        volumeNumber: '1',
        editionStatement: '1st ed',
        hybridOA: 'No',
        paymentType: 'Unknown',
        accessStartDate: '2020-01-01',
        accessEndDate: '2030-12-31',
        subjectArea: 'Subject1',
        series: 'Series1',
        publisherName: 'PackageService Test Org',
        dateFirstInPrint: '2001-01-01',
        dateFirstOnline: '2019-01-01',
        medium: 'Book',
        coverage: [
          [
            coverageDepth: 'fulltext',
            startDate: '',
            startVolume: '',
            startIssue: '',
            endDate: '',
            endVolume: '',
            endIssue: '',
            coverageNote: ''
          ]
        ]
      ]

      TitleInstancePackagePlatform tipp = tippUpsertService.upsertDTO(tipp_map)


      tipp.title = book
      tipp.ids.add(isbn)
      tipp.save(flush: true)
    }

    if (!TitleInstancePackagePlatform.findByName('PackageService BookTipp 2')) {
      def tipp_map = [
        pkg: testPkg2.id,
        hostPlatform: testPlt.id,
        name: 'PackageService BookTipp 2',
        url: 'https://package-caching-test.test/book1',
        editStatus: 'Approved',
        publicationType: 'Monograph',
        importId: 'pcsB1',
        firstAuthor: 'Author1',
        firstEditor: 'Editor1',
        volumeNumber: '1',
        editionStatement: '1st ed',
        hybridOA: 'No',
        paymentType: 'Unknown',
        accessStartDate: '2020-01-01',
        accessEndDate: '2030-12-31',
        subjectArea: 'Subject1',
        series: 'Series1',
        publisherName: 'PackageService Test Org',
        dateFirstInPrint: '2001-01-01',
        dateFirstOnline: '2019-01-01',
        medium: 'Book',
        coverage: [
          [
            coverageDepth: 'fulltext',
            startDate: '',
            startVolume: '',
            startIssue: '',
            endDate: '',
            endVolume: '',
            endIssue: '',
            coverageNote: ''
          ]
        ]
      ]

      TitleInstancePackagePlatform tipp = tippUpsertService.upsertDTO(tipp_map)


      tipp.title = book
      tipp.ids.add(isbn)
      tipp.save(flush: true)
    }


    if (!TitleInstancePackagePlatform.findByName('PackageService BookTipp 3')) {
      def tipp_map = [
        pkg: testPkg3.id,
        hostPlatform: testPlt.id,
        name: 'PackageService BookTipp 3',
        url: 'https://package-caching-test.test/book1',
        editStatus: 'Approved',
        publicationType: 'Monograph',
        importId: 'pcsB1',
        firstAuthor: 'Author1',
        firstEditor: 'Editor1',
        volumeNumber: '1',
        editionStatement: '1st ed',
        hybridOA: 'No',
        paymentType: 'Unknown',
        accessStartDate: '2020-01-01',
        accessEndDate: '2030-12-31',
        subjectArea: 'Subject1',
        series: 'Series1',
        publisherName: 'PackageService Test Org',
        dateFirstInPrint: '2001-01-01',
        dateFirstOnline: '2019-01-01',
        medium: 'Book',
        coverage: [
          [
            coverageDepth: 'fulltext',
            startDate: '',
            startVolume: '',
            startIssue: '',
            endDate: '',
            endVolume: '',
            endIssue: '',
            coverageNote: ''
          ]
        ]
      ]

      TitleInstancePackagePlatform tipp = tippUpsertService.upsertDTO(tipp_map)


      tipp.title = book
      tipp.ids.add(isbn)
      tipp.save(flush: true)
    }
  }


  def cleanup() {
    [
      'PackageService Journal 1',
      'PackageService Journal 2',
      'PackageService Update Journal',
      'PackageService BookTipp 1',
      'PackageService BookTipp 2',
      'PackageService BookTipp 3',
      'PackageService Update Book',
    ].each {
      TitleInstancePackagePlatform.findByName(it)?.expunge()
    }

    [
      'PackageService Test Package',
      'PackageService Test FirstLine',
      'PackageService Test AddJournal'
    ].each {
      Package.findByName(it)?.expunge()
    }

    Platform.findByName('PackageService Test Platform')?.expunge()
    Org.findByName('PackageService Test Org')?.expunge()
    BookInstance.findByName('PackageService Book 1')?.expunge()
    BookInstance.findByName('PackageService Update Book')?.expunge()
    JournalInstance.findByName('PackageService Journal 1')?.expunge()
    JournalInstance.findByName('PackageService Journal 2')?.expunge()
  }

  void "Test caching new TIPP KBART - test new file with monograph"() {
    given:
    def testPkg = Package.findByName('PackageService Test Package')
    String old_fn_pattern = packageCSVExportService.generateExportFileName(testPkg, PackageCSVExportService.ExportType.KBART_TIPP)
    def old_filename = packageCSVExportService.getLatestFile(testPkg, filePath, old_fn_pattern, PackageCSVExportService.ExportType.KBART_TIPP)
    def old_file = new File(filePath + old_filename)

    if (old_file.isFile()) {
      assert old_file.delete()
    }

    when:
    sleep(1000)
    packageCSVExportService.createKbartExport(testPkg)

    then:
    sleep(3000)
    String new_fn_pattern = packageCSVExportService.generateExportFileName(testPkg, PackageCSVExportService.ExportType.KBART_TIPP)
    String latest_filename = packageCSVExportService.getLatestFile(testPkg, filePath, new_fn_pattern, PackageCSVExportService.ExportType.KBART_TIPP)
    File file = new File(filePath + latest_filename)

    assert file.isFile()

    def csv = packageCSVExportService.initReader(filePath + latest_filename)
    String[] header = csv.readNext().collect { it.toLowerCase().trim() }
    def col_positions = [:]
    int col_ctr = 0

    header.each { col ->
      col == packageCSVExportService.KBART_FIELDS[col_ctr]
      col_positions[col] = col_ctr++
    }

    String[] row_data = csv.readNext()

    row_data[col_positions['publication_title']] == 'PackageService BookTipp 1'
    row_data[col_positions['print_identifier']] == ''
    row_data[col_positions['online_identifier']] == '979-11-655-6390-5'
    row_data[col_positions['date_first_issue_online']] == ''
    row_data[col_positions['num_first_vol_online']] == ''
    row_data[col_positions['num_first_issue_online']] == ''
    row_data[col_positions['num_last_vol_online']] == ''
    row_data[col_positions['num_last_issue_online']] == ''
    row_data[col_positions['date_last_issue_online']] == ''
    row_data[col_positions['title_url']] == 'https://package-caching-test.test/book1'
    row_data[col_positions['first_author']] == 'Author1'
    row_data[col_positions['title_id']] == 'pcsB1'
    row_data[col_positions['embargo_info']] == ''
    row_data[col_positions['coverage_depth']] == 'fulltext'
    row_data[col_positions['coverage_notes']] == ''
    row_data[col_positions['publisher_name']] == 'PackageService Test Org'
    row_data[col_positions['publication_type']] == 'Monograph'
    row_data[col_positions['date_monograph_published_print']] == '2001-01-01'
    row_data[col_positions['date_monograph_published_online']] == '2019-01-01'
    row_data[col_positions['monograph_volume']] == '1'
    row_data[col_positions['first_editor']] == 'Editor1'
    row_data[col_positions['parent_publication_title_id']] == ''
    row_data[col_positions['preceding_publication_title_id']] == ''
    row_data[col_positions['access_type']] == 'P'

    csv.readNext() == null
    csv.close()
    file.delete() == true
  }

  void "Test caching updated TIPP KBART - new TIPP"() {
    given:
    def testPkgAdd = Package.findByName('PackageService Test AddJournal')
    String old_fn_pattern = packageCSVExportService.generateExportFileName(testPkgAdd, PackageCSVExportService.ExportType.KBART_TIPP)
    String old_filename = packageCSVExportService.getLatestFile(testPkgAdd, filePath, old_fn_pattern, PackageCSVExportService.ExportType.KBART_TIPP)
    File old_file = new File(filePath + old_filename)

    if (old_file.isFile()) {
      assert old_file.delete()
    }

    when:
    sleep(1000)
    packageCSVExportService.createKbartExport(testPkgAdd)
    sleep(5000)

    def tipp1_map = [
      pkg: testPkgAdd.id,
      hostPlatform: testPlt.id,
      name: 'PackageService Journal 1',
      url: 'https://package-caching-test.test/journal1',
      editStatus: 'Approved',
      publicationType: 'Serial',
      importId: 'pcsJ1',
      hybridOA: 'No',
      paymentType: 'Unknown',
      accessStartDate: '2020-01-01',
      accessEndDate: '',
      subjectArea: 'Subject1',
      series: 'Series1',
      publisherName: 'PackageService Test Org',
      coverage: [
        [
          coverageDepth: 'fulltext',
          startDate: '2020-01',
          startVolume: '1',
          startIssue: '1',
          endDate: '',
          endVolume: '',
          endIssue: '',
          coverageNote: ''
        ]
      ]
    ]

    TitleInstancePackagePlatform tipp1 = tippUpsertService.upsertDTO(tipp1_map)

    tipp1.title = journal1
    tipp1.ids.addAll([issn, eissn])
    tipp1.save(flush: true)

    sleep(1000)

    tipp1.pkg.lastSeen = new Date().getTime()
    tipp1.pkg.save(flush: true)

    sleep(5000)
    packageCSVExportService.createKbartExport(tipp1.pkg)
    sleep (2000)

    then:
    String latest_fn_pattern = packageCSVExportService.generateExportFileName(testPkgAdd, PackageCSVExportService.ExportType.KBART_TIPP)
    String latest_filename = packageCSVExportService.getLatestFile(testPkgAdd, filePath, latest_fn_pattern, PackageCSVExportService.ExportType.KBART_TIPP)
    File file = new File(filePath + latest_filename)

    file.isFile()

    def csv = packageCSVExportService.initReader(filePath + latest_filename)
    String[] header = csv.readNext().collect { it.toLowerCase().trim() }
    def col_positions = [:]
    int col_ctr = 0

    header.each { col ->
      col_positions[col] = col_ctr++
    }

    String[] row_data = csv.readNext()

    row_data[col_positions['publication_title']] == 'PackageService BookTipp 2'
    row_data[col_positions['print_identifier']] == ''
    row_data[col_positions['online_identifier']] == '979-11-655-6390-5'
    row_data[col_positions['date_first_issue_online']] == ''
    row_data[col_positions['num_first_vol_online']] == ''
    row_data[col_positions['num_first_issue_online']] == ''
    row_data[col_positions['num_last_vol_online']] == ''
    row_data[col_positions['num_last_issue_online']] == ''
    row_data[col_positions['date_last_issue_online']] == ''
    row_data[col_positions['title_url']] == 'https://package-caching-test.test/book1'
    row_data[col_positions['first_author']] == 'Author1'
    row_data[col_positions['title_id']] == 'pcsB1'
    row_data[col_positions['embargo_info']] == ''
    row_data[col_positions['coverage_depth']] == 'fulltext'
    row_data[col_positions['coverage_notes']] == ''
    row_data[col_positions['publisher_name']] == 'PackageService Test Org'
    row_data[col_positions['publication_type']] == 'Monograph'
    row_data[col_positions['date_monograph_published_print']] == '2001-01-01'
    row_data[col_positions['date_monograph_published_online']] == '2019-01-01'
    row_data[col_positions['monograph_volume']] == '1'
    row_data[col_positions['first_editor']] == 'Editor1'
    row_data[col_positions['parent_publication_title_id']] == ''
    row_data[col_positions['preceding_publication_title_id']] == ''
    row_data[col_positions['access_type']] == 'P'

    String[] row_data2 = csv.readNext()

    row_data2 != null
    row_data2[col_positions['publication_title']] == 'PackageService Journal 1'
    row_data2[col_positions['print_identifier']] == '0128-5483'
    row_data2[col_positions['online_identifier']] == '2180-4338'
    row_data2[col_positions['date_first_issue_online']] == '2020-01-01'
    row_data2[col_positions['num_first_vol_online']] == '1'
    row_data2[col_positions['num_first_issue_online']] == '1'
    row_data2[col_positions['num_last_vol_online']] == ''
    row_data2[col_positions['num_last_issue_online']] == ''
    row_data2[col_positions['date_last_issue_online']] == ''
    row_data2[col_positions['title_url']] == 'https://package-caching-test.test/journal1'
    row_data2[col_positions['title_id']] == 'pcsJ1'
    row_data2[col_positions['embargo_info']] == ''
    row_data2[col_positions['coverage_depth']] == 'fulltext'
    row_data2[col_positions['coverage_notes']] == ''
    row_data2[col_positions['publisher_name']] == 'PackageService Test Org'
    row_data2[col_positions['publication_type']] == 'Serial'
    row_data2[col_positions['date_monograph_published_print']] == ''
    row_data2[col_positions['date_monograph_published_online']] == ''
    row_data2[col_positions['monograph_volume']] == ''
    row_data2[col_positions['first_editor']] == ''
    row_data2[col_positions['parent_publication_title_id']] == ''
    row_data2[col_positions['preceding_publication_title_id']] == ''
    row_data2[col_positions['access_type']] == 'P'

    csv.close()
    file.delete() == true
  }

  void "Test caching updated TIPP KBART - updated TIPP fields"() {
    given:
    def testPkgUpdate = Package.findByName('PackageService Test FirstLine')
    String old_fn_pattern = packageCSVExportService.generateExportFileName(testPkgUpdate, PackageCSVExportService.ExportType.KBART_TIPP)
    String old_filename = packageCSVExportService.getLatestFile(testPkgUpdate, filePath, old_fn_pattern, PackageCSVExportService.ExportType.KBART_TIPP)
    File old_file = new File(filePath + old_filename)

    if (old_file.isFile()) {
      assert old_file.delete()
    }

    sleep(1000)
    packageCSVExportService.createKbartExport(testPkgUpdate)
    sleep(500)

    def tipp1 = TitleInstancePackagePlatform.findByName('PackageService BookTipp 3')
    tipp1.name = 'PackageService Update Book'
    tipp1.url = 'https://package-caching-test.test/book1update'
    tipp1.accessEndDate = null
    tipp1.dateFirstInPrint = dateFormatService.parseTimestamp('2012-01-01 00:00:00.000')
    tipp1.merge(flush: true)

    testPkgUpdate.lastSeen = new Date().getTime()
    testPkgUpdate.save(flush: true)

    when:
    packageCSVExportService.createKbartExport(testPkgUpdate)
    sleep(3000)

    then:
    String latest_fn_pattern = packageCSVExportService.generateExportFileName(testPkgUpdate, PackageCSVExportService.ExportType.KBART_TIPP)
    String latest_filename = packageCSVExportService.getLatestFile(testPkgUpdate, filePath, latest_fn_pattern, PackageCSVExportService.ExportType.KBART_TIPP)
    File file = new File(filePath + latest_filename)

    file.isFile()

    def csv = packageCSVExportService.initReader(filePath + latest_filename)
    String[] header = csv.readNext().collect { it.toLowerCase().trim() }
    def col_positions = [:]
    int col_ctr = 0

    header.each { col ->
      col_positions[col] = col_ctr++
    }

    String[] row_data = csv.readNext()

    row_data[col_positions['publication_title']] == 'PackageService Update Book'
    row_data[col_positions['print_identifier']] == ''
    row_data[col_positions['online_identifier']] == '979-11-655-6390-5'
    row_data[col_positions['date_first_issue_online']] == ''
    row_data[col_positions['num_first_vol_online']] == ''
    row_data[col_positions['num_first_issue_online']] == ''
    row_data[col_positions['num_last_vol_online']] == ''
    row_data[col_positions['num_last_issue_online']] == ''
    row_data[col_positions['date_last_issue_online']] == ''
    row_data[col_positions['title_url']] == 'https://package-caching-test.test/book1update'
    row_data[col_positions['first_author']] == 'Author1'
    row_data[col_positions['title_id']] == 'pcsB1'
    row_data[col_positions['embargo_info']] == ''
    row_data[col_positions['coverage_depth']] == 'fulltext'
    row_data[col_positions['coverage_notes']] == ''
    row_data[col_positions['publisher_name']] == 'PackageService Test Org'
    row_data[col_positions['publication_type']] == 'Monograph'
    row_data[col_positions['date_monograph_published_print']] == '2012-01-01'
    row_data[col_positions['date_monograph_published_online']] == '2019-01-01'
    row_data[col_positions['monograph_volume']] == '1'
    row_data[col_positions['first_editor']] == 'Editor1'
    row_data[col_positions['parent_publication_title_id']] == ''
    row_data[col_positions['preceding_publication_title_id']] == ''
    row_data[col_positions['access_type']] == 'P'

    String[] row_data2 = csv.readNext()

    csv.close()
    file.delete() == true
  }
}