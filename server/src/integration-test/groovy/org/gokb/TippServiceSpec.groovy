package org.gokb

import com.k_int.ConcurrencyManagerService

import gokbg3.DateFormatService

import grails.converters.JSON
import grails.testing.mixin.integration.Integration
import grails.testing.services.ServiceUnitTest

import java.time.LocalDateTime

import org.gokb.cred.*
import org.hibernate.SessionFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.Rollback
import org.springframework.transaction.annotation.Transactional

import spock.lang.Specification

@Integration
@Transactional
@Rollback
class TippServiceSpec extends Specification {

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

  IdentifierNamespace issn_ns
  IdentifierNamespace eissn_ns
  IdentifierNamespace isbn_ns

  def setup() {
    Org publisher = Org.findByName("TippService Test Org") ?: new Org(name: "TippService Test Org").save(flush: true)
    Platform plt = Platform.findByName("TippService Test Platform") ?: new Platform(name: "TippService Test Platform", provider: publisher).save(flush: true)
    Package pkg = Package.findByName("TippService Test Package") ?: new Package(name: "TippService Test Package").save(flush: true)

    if (!issn_ns) {
      issn_ns = IdentifierNamespace.findByValue('issn')
    }
    if (!eissn_ns) {
      eissn_ns = IdentifierNamespace.findByValue('eissn')
    }
    if (!isbn_ns) {
      isbn_ns = IdentifierNamespace.findByValue('isbn')
    }

    if (!BookInstance.findByName("TippService Book 1")) {
      Identifier isbn = new Identifier(namespace: isbn_ns, value: '979-11-655-6390-5').save(flush: true)
      BookInstance book = new BookInstance(name: "TippService Book 1").save(flush:true)
      book.ids.add(isbn)
      book.save(flush: true)
    }

    Identifier issn = Identifier.findByNamespaceAndValue(issn_ns, '0128-5483') ?: new Identifier(namespace: issn_ns, value: '0128-5483')
    Identifier eissn = Identifier.findByNamespaceAndValue(eissn_ns, '2180-4338') ?: new Identifier(namespace: eissn_ns, value: '2180-4338')

    JournalInstance journal = JournalInstance.findByName("TippService Journal 1")
    JournalInstance journal2 = JournalInstance.findByName("TippService Journal 2")

    if (!journal) {
      journal = new JournalInstance(name: "TippService Journal 1").save(flush:true)
      journal.ids.addAll([issn, eissn])
      journal.save(flush: true)
    }

    if (!journal2) {
      journal2 = new JournalInstance(name: "TippService Journal 2").save(flush:true)
      journal2.ids.addAll([issn])
      journal2.save(flush: true)
    }

    TitleInstancePackagePlatform rr_tipp = TitleInstancePackagePlatform.findByName("Test TIPP ambiguous review")

    if (!rr_tipp) {
      Package rr_pkg = Package.findByName("TippService Test reviewAmbiguousMatches Package") ?: new Package(name: "TippService Test reviewAmbiguousMatches Package").save(flush: true)

      def tmap = [
        pkg            : rr_pkg.id,
        hostPlatform   : plt.id,
        url            : "http://test-url.net/",
        status         : "Current",
        name           : "Test TIPP ambiguous review",
        editStatus     : "Approved",
        language       : "ger",
        publicationType: "Serial"
      ]

      rr_tipp = tippUpsertService.upsertDTO(tmap)

      rr_tipp.ids.addAll([issn, eissn])
      rr_tipp.save(flush: true)
    }

    RefdataValue rr_type = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Ambiguous Title Matches')
    RefdataValue rr_status_closed = RefdataCategory.lookup('ReviewRequest.Status', 'Closed')
    RefdataValue rr_status_open = RefdataCategory.lookup('ReviewRequest.Status', 'Open')
    ReviewRequest ambiguous_rr = ReviewRequest.findByComponentToReviewAndStdDesc(rr_tipp, rr_type)

    if (ambiguous_rr && ambiguous_rr.status == rr_status_closed) {
      ambiguous_rr.status = rr_status_open
      ambiguous_rr.save(flush: true)
    } else {
      Map additionalInfo = [
        otherComponents: [
          [
            id: journal.id,
            name: journal.name,
            uuid: journal.uuid
          ],
          [
            id: journal2.id,
            name: journal2.name,
            uuid: journal2.uuid
          ]
        ]
      ]

      ReviewRequest req = new ReviewRequest(
        status: rr_status_open,
        descriptionOfCause: "Ambiguous Title Matches",
        reviewRequest: "Select reference title to link",
        stdDesc: rr_type,
        additionalInfo: (additionalInfo as JSON).toString(),
        componentToReview: rr_tipp
      ).save(flush:true)
    }

    TitleInstancePackagePlatform update_tipp = TitleInstancePackagePlatform.findByName("Test TIPP updateTippFields")

    if (!update_tipp) {

      def tmap = [
        pkg            : pkg.id,
        hostPlatform   : plt.id,
        url            : "http://test-url.net/",
        status         : "Current",
        name           : "Test TIPP updateTippFields",
        editStatus     : "Approved",
        language       : "ger",
        publicationType: "Serial",
        paymentType    : "Paid",
        coverage: [
          [
            startDate: '2012-01',
            startVolume: '1',
            startIssue: '1',
            endDate: null,
            endVolume: null,
            endIssue: null,
            coverageDepth: 'Fulltext',
            coverageNote: 'No Embargo',
            embargo: null
          ]
        ]
      ]

      update_tipp = tippUpsertService.upsertDTO(tmap)

      update_tipp.ids.addAll([issn, eissn])
      update_tipp.save(flush: true)
    }
  }

  def cleanup() {
    [
      "Test Title from full TIPP",
      "Test Title from minimal TIPP",
      "TippService Journal 1",
      "TippService Journal 2",
      "Test TIPP idmatch",
      "TippService Update Journal",
      "TippService Book 1",
      "TippService Journal Conflict 1",
      "TippService Journal Conflict 2",
      "Test TIPP ambiguous review",
      "Test TIPP updateTippFields"
    ].each {
      TitleInstancePackagePlatform.findByName(it)?.expunge()
    }
    Package.findByName("TippService Test Package")?.expunge()
    Package.findByName("TippService Test reviewAmbiguousMatches Package")?.expunge()
    Platform.findByName("TippService Test Platform")?.expunge()
    Org.findByName("TippService Test Org")?.expunge()
    BookInstance.findByName("TippService Book 1")?.expunge()
    BookInstance.findByName("TippService Update Book")?.expunge()
    JournalInstance.findByName("TippService Journal 1")?.expunge()
    JournalInstance.findByName("TippService Journal Conflict 1")?.expunge()

  }

  void "Test create new title from a minimal TIPP"() {
    given:
    def pkg_id = Package.findByName("TippService Test Package").id
    def plt_id = Platform.findByName("TippService Test Platform").id

    def tmap = [
      pkg            : pkg_id,
      hostPlatform   : plt_id,
      url            : "http://test-url.net/",
      status         : "Current",
      name           : "Test Title from minimal TIPP",
      editStatus     : "Approved",
      language       : "ger",
      publicationType: "Monograph"
    ]

    when:
    def tipp = tippUpsertService.upsertDTO(tmap)
    sleep(300)
    def result = tippService.matchTitle(tipp.id)
    sleep(300)

    then:
    result.status == 'created'
    tipp.title != null
  }

  void "Test create new BookInstance from a full TIPP"() {
    given:
    def pkg_id = Package.findByName("TippService Test Package").id
    def plt_id = Platform.findByName("TippService Test Platform").id

    def tmap = [
      pkg            : pkg_id,
      hostPlatform   : plt_id,
      url                        : "http://test-url.net/",
      importId                   : "völlig egal",
      status                     : "Current",
      name                       : "Test Title from full TIPP",
      editStatus                 : "Approved",
      language                   : "ger",
      publicationType            : "Monograph",
      coverageNote               : "coverage Note",
      format                     : "Print",
      delayedOA                  : "No",
      delayedOAEmbargo           : "Embargo?",
      hybridOA                   : "No",
      hybridOAUrl                : "Hybris",
      primary                    : "No",
      paymentType                : "Unknown",
      accessStartDate            : "2001-01-01",
      accessEndDate              : "2030-12-31",
      subjectArea                : "Fachbereich",
      series                     : "Serie: Marathon",
      publisherName              : "TippService Test Org",
      dateFirstInPrint           : "2003-01-01",
      dateFirstOnline            : "2004-01-01",
      firstAuthor                : "erster Autor",
      volumeNumber               : "erster Band",
      editionStatement           : "3. völlig überarbeitete Auflage",
      firstEditor                : "erster Verleger",
      parentPublicationTitleId   : "Eltern importId",
      precedingPublicationTitleId: "Vorgänger importId",
      lastChangedExternal        : "${LocalDateTime.now()}",
      medium                     : "Book"
    ]

    when:
    def tipp = tippUpsertService.upsertDTO(tmap)
    tippService.updateCombos(tipp, [identifiers: [[type: 'isbn', value: '9783406730696'], [type: 'pisbn', value: '9783406718175']]])
    sleep(300)
    def result = tippService.matchTitle(tipp.id)
    sleep(300)

    then:
    result.status == 'created'
    tipp.title != null
    tipp.name == tipp.title.name
    tipp.firstEditor == tipp.title.firstEditor
    tipp.firstAuthor == tipp.title.firstAuthor
    tipp.editionStatement == tipp.title.editionStatement
    tipp.volumeNumber == tipp.title.volumeNumber
    tipp.dateFirstInPrint == tipp.title.dateFirstInPrint
    tipp.dateFirstOnline == tipp.title.dateFirstOnline
    tipp.medium.value == tipp.title.medium.value
    tipp.title.publisher*.name.contains(tipp.publisherName)
  }

  void "Test attach existing title with a TIPP by its IDs"() {
    given:
    Identifier my_isbn = Identifier.findByNamespaceAndValue(IdentifierNamespace.findByValue('isbn'), '979-11-655-6390-5') ?: new Identifier(namespace: IdentifierNamespace.findByValue('isbn'), value: '979-11-655-6390-5')
    def pkg_id = Package.findByName("TippService Test Package").id
    def plt_id = Platform.findByName("TippService Test Platform").id

    def tmap = [
      pkg            : pkg_id,
      hostPlatform   : plt_id,
      'url'            : "http://test-url.net/",
      'status'         : "Current",
      'name'           : "Test TIPP idmatch",
      'editStatus'     : "Approved",
      'language'       : 'ger',
      'publicationType': "Monograph",
    ]

    when:
    def tipp = tippUpsertService.upsertDTO(tmap)
    tipp.ids.add(my_isbn)
    tipp.save(flush: true)

    tippService.matchTitle(tipp.id)

    then:
    tipp.title == BookInstance.findByName("TippService Book 1")
  }

  void "Test match existing title with minor id conflict"() {
    given:
    Identifier issn = Identifier.findByNamespaceAndValue(issn_ns, '0894-8410') ?: new Identifier(namespace: issn_ns, value: '0894-8410')
    Identifier eissn = Identifier.findByNamespaceAndValue(eissn_ns, '2180-4338') ?: new Identifier(namespace: eissn_ns, value: '2180-4338')
    def pkg_id = Package.findByName("TippService Test Package").id
    def plt_id = Platform.findByName("TippService Test Platform").id

    def tmap = [
      pkg            : pkg_id,
      hostPlatform   : plt_id,
      'url'            : "http://test-url.net/",
      'status'         : "Current",
      'name'           : "TippService Journal 1",
      'publicationType': "Serial",
    ]

    when:
    def tipp = tippUpsertService.upsertDTO(tmap)
    tipp.ids.addAll([issn, eissn])
    tipp.save(flush: true)

    def result = tippService.matchTitle(tipp.id)

    then:
    result?.status == 'matched'
    result.reviewCreated == true
    tipp.title == JournalInstance.findByName("TippService Journal 1")
    def rdv_desc = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Secondary Identifier Conflict')
    ReviewRequest.findByComponentToReviewAndStdDesc(tipp, rdv_desc) != null
  }

  void "Test create new title due to id & name conflict"() {
    given:
    Identifier issn = Identifier.findByNamespaceAndValue(issn_ns, '0894-8410') ?: new Identifier(namespace: issn_ns, value: '0894-8410')
    Identifier eissn = Identifier.findByNamespaceAndValue(eissn_ns, '2180-4338') ?: new Identifier(namespace: eissn_ns, value: '2180-4338')
    def pkg_id = Package.findByName("TippService Test Package").id
    def plt_id = Platform.findByName("TippService Test Platform").id

    def tmap = [
      pkg            : pkg_id,
      hostPlatform   : plt_id,
      'url'            : "http://test-url.net/",
      'status'         : "Current",
      'name'           : "TippService Journal Conflict 1",
      'publicationType': "Serial",
    ]

    when:
    def tipp = tippUpsertService.upsertDTO(tmap)
    tipp.ids.addAll([issn, eissn])
    tipp.save(flush: true)

    def result = tippService.matchTitle(tipp.id)

    then:
    result?.status == 'created'
    result.reviewCreated == true
    tipp.title == JournalInstance.findByName("TippService Journal Conflict 1")
    def rdv_desc = RefdataCategory.lookup('ReviewRequest.StdDesc', 'Critical Identifier Conflict')
    ReviewRequest.findByComponentToReviewAndStdDesc(tipp.title, rdv_desc) != null
  }

  void "Test skip title linking due to ambiguous matches"() {
    given:
    Identifier issn = Identifier.findByNamespaceAndValue(issn_ns, '0128-5483') ?: new Identifier(namespace: issn_ns, value: '0128-5483')
    def pkg_id = Package.findByName("TippService Test Package").id
    def plt_id = Platform.findByName("TippService Test Platform").id

    def tmap = [
      pkg            : pkg_id,
      hostPlatform   : plt_id,
      'url'            : "http://test-url.net/",
      'status'         : "Current",
      'name'           : "TippService Journal Conflict 2",
      'publicationType': "Serial",
    ]

    when:
    def tipp = tippUpsertService.upsertDTO(tmap)
    tipp.ids.addAll([issn])
    tipp.save(flush: true)

    def result = tippService.matchTitle(tipp.id)

    then:
    result?.status == 'unmatched'
    result.reviewCreated == true
    tipp.title == null
    ReviewRequest.findByComponentToReviewAndStdDesc(tipp, RefdataCategory.lookup('ReviewRequest.StdDesc', 'Ambiguous Title Matches')) != null
    ReviewRequest.findByComponentToReviewAndStdDesc(JournalInstance.findByName("TippService Journal 2"), RefdataCategory.lookup('ReviewRequest.StdDesc', 'Critical Identifier Conflict')) != null
  }

  void "Test Package Update from TIPPs"() {
    given:
    def updPack = Package.findByName("TippService Test Package")
    def updPlt = Platform.findByName("TippService Test Platform")
    def updBook = new BookInstance(name: "TippService Update Book").save(flush: true)
    def updIsbn = new Identifier(value: '9783631725290', namespace: IdentifierNamespace.findByValue('isbn')).save(flush: true)
    updBook.ids.add(updIsbn)
    updBook.save(flush: true)
    def tBook = tippUpsertService.tiplAwareCreate([name: "TippService Book 1", pkg: updPack, hostPlatform: updPlt, url: 'http://tippservicebook.com/test'])
    tBook.ids.add(updIsbn)
    tBook.publicationType = RefdataCategory.lookup('TitleInstancePackagePlatform.PublicationType', 'Monograph')
    tBook.save(flush: true)
    def tJournal = tippUpsertService.tiplAwareCreate([name: "TippService Update Journal", pkg: updPack, hostPlatform: updPlt, url: 'http://tippservicejournal.com/test'])
    Identifier issn = new Identifier(namespace: IdentifierNamespace.findByValue('eissn'), value: '2209-7643')
    tJournal.ids.add(issn)
    tJournal.publicationType = RefdataCategory.lookup('TitleInstancePackagePlatform.PublicationType', 'Serial')
    tJournal.save(flush: true)
    when:
    def result = tippService.matchPackage(updPack.id)

    then:
    result.matched == 1
    result.created == 1
  }

  void "Test reviewAmbiguousMatches not closing review when multiple otherComponents are current" () {
    given:
    TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.findByName("Test TIPP ambiguous review")
    ReviewRequest review = ReviewRequest.findByComponentToReview(tipp)
    when:
    tippService.reviewAmbiguousMatches(tipp, [review])
    then:
    review.refresh().status == RefdataCategory.lookup('ReviewRequest.Status', 'Open')
  }

  void "Test reviewAmbiguousMatches closing review when only one otherComponent is current" () {
    given:
    TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.findByName("Test TIPP ambiguous review")
    JournalInstance journal2 = JournalInstance.findByName("TippService Journal 2")
    ReviewRequest review = ReviewRequest.findByComponentToReview(tipp)
    when:
    journal2.status = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    journal2.save(flush: true)

    tippService.reviewAmbiguousMatches(tipp, [review])
    tipp.refresh()
    review.refresh()
    then:

    review.status == RefdataCategory.lookup('ReviewRequest.Status', 'Closed')
    tipp.title != null
  }

  void "Test updateTippFields without changes"() {
    given:
    def tipp_to_update = TitleInstancePackagePlatform.findByName("Test TIPP updateTippFields")
    def old_update = tipp_to_update.lastUpdated
    def update_info = [
        url            : "http://test-url.net/",
        status         : "Current",
        name           : "Test TIPP updateTippFields",
        editStatus     : "Approved",
        language       : "ger",
        publicationType: "Serial",
        paymentType    : "Paid",
        identifiers: [
          [
            type: 'issn',
            value: '0128-5483'
          ],
          [
            type: 'eissn',
            value: '2180-4338'
          ]
        ]
      ]
    when:
    def result = tippService.updateTippFields(tipp_to_update, update_info)
    then:
    result == false
    tipp_to_update.refresh().lastUpdated == old_update
  }

  void "Test updateTippFields with changes in simple fields"() {
    given:
    def tipp_to_update = TitleInstancePackagePlatform.findByName("Test TIPP updateTippFields")
    def old_update = tipp_to_update.lastUpdated
    def update_info = [
        url            : "http://test-url.net/update",
        name           : "Test TIPP updateTippFields",
        language       : "ger",
        publicationType: "Serial",
        paymentType    : "Paid",
        identifiers: [
          [
            type: 'issn',
            value: '0128-5483'
          ],
          [
            type: 'eissn',
            value: '2180-4338'
          ]
        ]
      ]
    when:
    def result = tippService.updateTippFields(tipp_to_update, update_info)
    then:
    result == true
    tipp_to_update.refresh().lastUpdated != old_update
  }

  void "Test updateTippFields with removed identifier"() {
    given:
    def tipp_to_update = TitleInstancePackagePlatform.findByName("Test TIPP updateTippFields")
    def old_update = tipp_to_update.lastUpdated
    def update_info = [
        url            : "http://test-url.net/update",
        name           : "Test TIPP updateTippFields",
        language       : "ger",
        publicationType: "Serial",
        paymentType    : "Paid",
        identifiers: [
          [
            type: 'eissn',
            value: '2180-4338'
          ]
        ]
      ]
    when:
    def result = tippService.updateTippFields(tipp_to_update, update_info)
    then:
    result == true
    tipp_to_update.lastUpdated != old_update
  }
}
