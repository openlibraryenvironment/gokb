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
import org.gokb.cred.KBComponent
import org.gokb.cred.Org
import org.gokb.cred.Package
import org.gokb.cred.Platform
import org.gokb.cred.RefdataCategory
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
class TippServiceSpec extends Specification implements ServiceUnitTest<TippService> {

  private Package pkg
  private Platform plt
  private TitleInstance book
  private Identifier isbn
  private Identifier issn
  private Org publisher
  private Package packMatch

  @Autowired
  TippService tippService
  DateFormatService dateFormatService
  SessionFactory sessionFactory
  ConcurrencyManagerService concurrencyManagerService

  def setup() {
    pkg = new Package(name: "TS Test Package").save(flush: true)
    plt = new Platform(name: "TS Test Platform").save(flush: true)
    isbn = new Identifier(namespace: IdentifierNamespace.findByValue('isbn'), value: '979-11-655-6390-5')
    issn = new Identifier(namespace: IdentifierNamespace.findByValue('eissn'), value: '2209-7643')
    book = new BookInstance(name: "TS Book 1").save(flush:true)
    book.ids.add(isbn)
    book.save(flush: true)
    publisher = new Org(name: "Publizistenname").save(flush: true)
  }

  def cleanup() {
    pkg.expunge()
    plt.expunge()
    book.expunge()
  }

  void "Test create new title from a minimal TIPP"() {
    given:
    def tmap = [
      pkg            : pkg.id,
      hostPlatform   : plt.id,
      url            : null,
      status         : "Current",
      name           : "Test Title from minimal TIPP",
      editStatus     : "Approved",
      language       : "ger",
      publicationType: "Monograph"
    ]

    when:
    TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.upsertDTO(tmap)

    tippService.matchTitle(tipp)

    then:
    tipp.title != null
  }

  void "Test create new BookInstance from a full TIPP"() {
    given:
    def tmap = [
      pkg                        : pkg.id,
      hostPlatform               : plt.id,
      url                        : "http://some.random.thing/",
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
      publisherName              : "Publizistenname",
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
    TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.upsertDTO(tmap)
    tippService.matchTitle(tipp)

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
    def tmap = [
      'pkg'            : pkg.id,
      'hostPlatform'   : plt.id,
      'url'            : null,
      'status'         : "Current",
      'name'           : "Test TIPP idmatch",
      'editStatus'     : "Approved",
      'language'       : 'ger',
      'publicationType': "Monograph",
    ]

    when:
    TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.upsertDTO(tmap)
    tipp.ids.add(my_isbn)
    tipp.save(flush: true)

    tippService.matchTitle(tipp)

    then:
    tipp.title == book
  }

  void "Test Package Update from TIPPs"() {
    given:
    def updPack = new Package(name: "TS Import Package").save(flush: true)
    def updBook = new BookInstance(name: "TS Update Book").save(flush: true)
    def updIsbn = new Identifier(value: '9783631725290', namespace: IdentifierNamespace.findByValue('isbn')).save(flush: true)
    updBook.ids.add(updIsbn)
    updBook.save(flush: true)
    def tBook = TitleInstancePackagePlatform.tiplAwareCreate([name: "TS Book 1", pkg: updPack, hostPlatform: plt, url: 'http://tippservicebook.com/test'])
    tBook.ids.add(updIsbn)
    tBook.publicationType = RefdataCategory.lookup('TitleInstancePackagePlatform.PublicationType', 'Monograph')
    tBook.save(flush: true)
    def tJournal = TitleInstancePackagePlatform.tiplAwareCreate([name: "TS Update Journal", pkg: updPack, hostPlatform: plt, url: 'http://tippservicejournal.com/test'])
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
