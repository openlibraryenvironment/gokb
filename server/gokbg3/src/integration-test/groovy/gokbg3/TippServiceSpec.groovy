package gokbg3

import com.k_int.ConcurrencyManagerService
import grails.testing.mixin.integration.Integration
import grails.testing.services.ServiceUnitTest
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
    pkg = new Package(name: "Test Package").save()
    plt = new Platform(name: "Test Platform").save()
    isbn = new Identifier(namespace: IdentifierNamespace.findByValue('isbn'), value: '979-11-655-6390-5').save()
    book = new BookInstance(name: "Book 1", ids: [isbn]).save()
    publisher = new Org(name: "Publizistenname").save()
  }

  def cleanup() {
    pkg.expunge()
    plt.expunge()
    book.expunge()
    isbn.expunge()
    publisher.expunge()
  }

  void "Test create new title from a minimal TIPP"() {
    given:
    def tmap = [
      pkg            : pkg,
      title          : null,
      hostPlatform   : plt,
      url            : null,
      uuid           : UUID.randomUUID(),
      status         : RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT),
      name           : "Test Title Name from TIPP",
      editStatus     : RefdataCategory.lookup(KBComponent.RD_EDIT_STATUS, KBComponent.EDIT_STATUS_APPROVED),
      language       : RefdataCategory.lookup(KBComponent.RD_LANGUAGE, "ger"),
      publicationType: RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, "Monograph")
    ]

    when:
    TitleInstancePackagePlatform tipp = new TitleInstancePackagePlatform(tmap)

    tippService.matchTitle(tipp)

    then:
    tipp.title != null
    tipp.expunge()
  }

  void "Test create new BookInstance from a full TIPP"() {
    given:
    def tmap = [
      pkg                        : pkg,
      title                      : null,
      hostPlatform               : plt,
      url                        : "http://some.random.thing/",
      uuid                       : UUID.randomUUID(),
      importId                   : "völlig egal",
      status                     : RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT),
      name                       : "Test Title Name from TIPP",
      editStatus                 : RefdataCategory.lookup(KBComponent.RD_EDIT_STATUS, KBComponent.EDIT_STATUS_APPROVED),
      language                   : RefdataCategory.lookup(KBComponent.RD_LANGUAGE, "ger"),
      publicationType            : RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, "Monograph"),
      coverageNote               : "coverage Note",
      format                     : RefdataCategory.lookup(TitleInstancePackagePlatform.RD_FORMAT, "Print"),
      delayedOA                  : RefdataCategory.lookup(TitleInstancePackagePlatform.RD_DELAYED_OA, "No"),
      delayedOAEmbargo           : "Embargo?",
      hybridOA                   : RefdataCategory.lookup(TitleInstancePackagePlatform.RD_HYBRID_OA, "No"),
      hybridOAUrl                : "Hybris",
      primary                    : RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PRIMARY, "No"),
      paymentType                : RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PAYMENT_TYPE, "Unknown"),
      accessStartDate            : dateFormatService.parseDate("2001-01-01"),
      accessEndDate              : dateFormatService.parseDate("2030-12-31"),
      subjectArea                : "Fachbereich",
      series                     : "Serie: Marathon",
      publisherName              : "Publizistenname",
      dateFirstInPrint           : dateFormatService.parseDate("2003-01-01"),
      dateFirstOnline            : dateFormatService.parseDate("2004-01-01"),
      firstAuthor                : "erster Autor",
      volumeNumber               : "erster Band",
      editionStatement           : "3. völlig überarbeitete Auflage",
      firstEditor                : "erster Verleger",
      parentPublicationTitleId   : "Eltern importId",
      precedingPublicationTitleId: "Vorgänger importId",
      lastChangedExternal        : new Date(),
      medium                     : RefdataCategory.lookup(TitleInstancePackagePlatform.RD_MEDIUM, "Book")
    ]

    when:
    TitleInstancePackagePlatform tipp = new TitleInstancePackagePlatform(tmap)

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
    tipp.expunge()
  }

  void "Test attach existing title with a TIPP by its IDs"() {
    given:
    Identifier my_isbn = Identifier.findByNamespaceAndValue(IdentifierNamespace.findByValue('isbn'), '979-11-655-6390-5') ?: new Identifier(namespace: IdentifierNamespace.findByValue('isbn'), value: '979-11-655-6390-5')
    def tmap = [
      'pkg'            : pkg,
      'title'          : null,
      'hostPlatform'   : plt,
      'url'            : null,
      'uuid'           : UUID.randomUUID(),
      'status'         : RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT),
      'name'           : book.name,
      'editStatus'     : RefdataCategory.lookup(KBComponent.RD_EDIT_STATUS, KBComponent.EDIT_STATUS_APPROVED),
      'language'       : RefdataCategory.lookup(KBComponent.RD_LANGUAGE, 'ger'),
      'publicationType': RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, "Monograph"),
    ]

    when:
    TitleInstancePackagePlatform tipp = new TitleInstancePackagePlatform(tmap).save()
    tipp.ids.add(my_isbn)
    tipp.save(flush: true)

    tippService.matchTitle(tipp)

    then:
    tipp.title == book
    tipp.expunge()
  }

  @Rollback
  void "Test Package Update from TIPPs"() {
    given: // a package with unmatched TIPPs
    def pack = new Package(name: "Import Package")
    def platform = new Platform(name: "Import Platform")
    def aISBN = new Identifier(namespace: IdentifierNamespace.findByValue('isbn'), value: '978-11-656-6370-8')
    def book1 = new BookInstance(name: "Book 1", ids: [aISBN])
    def tipp1 = new TitleInstancePackagePlatform([
      name           : "Book 1",
      hostPlatform   : platform,
      ids            : [aISBN],
      publicationType: RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, "Monograph")])
    def tipp2 = new TitleInstancePackagePlatform([
      name           : "Journal 1",
      hostPlatform   : platform,
      ids            : [new Identifier(namespace: IdentifierNamespace.findByValue('zdb'), value: '655639-0')],
      publicationType: RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, "Serial")])
    pack.tipps << tipp1
    pack.tipps << tipp2
    pack.save(flush: true)

    when:
    tippService.matchPackage(pack)

    then:
    tipp1.title == book1
    tipp2.title != null
  }
}
