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
  private TitleInstance book, journal
  private Identifier isbn
  private Org publisher

  @Autowired
  TippService tippService
  DateFormatService dateFormatService
  SessionFactory sessionFactory
  ConcurrencyManagerService concurrencyManagerService

  def setup() {
    pkg = new Package(name: "Test Package").save(flush: true)
    plt = new Platform(name: "Test Platform").save(flush: true)
    isbn = new Identifier(namespace: IdentifierNamespace.findByValue('isbn'), value: '979-11-655-6390-5').save(flush: true)
    book = new BookInstance(name: "Book 1").save(flush:true)
    book.ids.add(isbn)
    publisher = new Org(name: "Publizistenname").save(flush: true)
  }

  def cleanup() {
    pkg.expunge()
    plt.expunge()
    book.expunge()
    isbn.expunge()
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

  @Rollback
  void "Test Package Update from TIPPs"() {
    given: // a package with unmatched TIPPs
    def pack = new Package(name: "Import Package")
    def platform = new Platform(name: "Import Platform")
    def aISBN = new Identifier(namespace: IdentifierNamespace.findByValue('isbn'), value: '978-11-656-6370-8')
    def zdbId = new Identifier(namespace: IdentifierNamespace.findByValue('zdb'), value: '1655639-0')
    def book1 = new BookInstance(name: "Book 1")
    book1.ids.add(aISBN)
    def tipp1 = TitleInstancePackagePlatform.upsertDTO([
      name           : "Book 1",
      pkg            : pack.id,
      hostPlatform   : platform.id,
      publicationType: "Monograph"])
    tipp1.ids << aISBN
    tipp1.save(flush: true)

    def tipp2 = TitleInstancePackagePlatform.upsertDTO([
      name           : "Journal 1",
      pkg            : pack.id,
      hostPlatform   : platform.id,
      publicationType: "Serial"])
    tipp2.ids.add(zdbId)
    tipp2.save(flush: true)

    when:
    tippService.matchPackage(pack)

    then:
    tipp1.title == book1
    tipp2.title != null
  }
}
