package org.gokb

import com.k_int.ConcurrencyManagerService

import gokbg3.DateFormatService

import grails.testing.mixin.integration.Integration
import grails.testing.services.ServiceUnitTest

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

    if (!JournalInstance.findByName("TippService Journal 1")) {
      Identifier issn = Identifier.findByNamespaceAndValue(issn_ns, '0128-5483') ?: new Identifier(namespace: issn_ns, value: '0128-5483')
      Identifier eissn = Identifier.findByNamespaceAndValue(eissn_ns, '2180-4338') ?: new Identifier(namespace: eissn_ns, value: '2180-4338')

      JournalInstance journal = new JournalInstance(name: "TippService Journal 1").save(flush:true)
      journal.ids.addAll([issn, eissn])
      journal.save(flush: true)
    }
  }

  def cleanup() {
    ["Test Title from full TIPP","Test Title from minimal TIPP","TippService Journal 1", "Test TIPP idmatch","TippService Update Journal", "TippService Book 1", "TippService Journal Conflict 1"].each {
      TitleInstancePackagePlatform.findByName(it)?.expunge()
    }
    Package.findByName("TippService Test Package")?.expunge()
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
    sleep(100)
    def result = tippService.matchTitle(tipp.id)

    then:
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
    tippService.matchTitle(tipp.id)

    then:
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
}
