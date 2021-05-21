package gokbg3

import com.k_int.ConcurrencyManagerService
import grails.testing.mixin.integration.Integration
import grails.testing.services.ServiceUnitTest
import org.gokb.*
import org.gokb.cred.BookInstance
import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.KBComponent
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

  @Autowired
  TippService tippService
  SessionFactory sessionFactory
  ConcurrencyManagerService concurrencyManagerService

  def setup() {
    pkg = new Package(name: "Test Package").save()
    plt = new Platform(name: "Test Platform").save()
    isbn = new Identifier(namespace: IdentifierNamespace.findByValue('isbn'), value: '979-11-655-6390-5')
    book = new BookInstance(name: "Book 1", ids: [isbn])
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
        'pkg'            : pkg,
        'title'          : null,
        'hostPlatform'   : plt,
        'url'            : null,
        'uuid'           : UUID.randomUUID(),
        'status'         : RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT),
        'name'           : "Test Title Name from TIPP",
        'editStatus'     : RefdataCategory.lookup(KBComponent.RD_EDIT_STATUS, KBComponent.EDIT_STATUS_APPROVED),
        'language'       : RefdataCategory.lookup(KBComponent.RD_LANGUAGE, 'ger'),
        'publicationType': RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, "Monograph")
    ]

    when:
    TitleInstancePackagePlatform tipp = new TitleInstancePackagePlatform(tmap)

    tippService.matchTitle(tipp)

    then:
    tipp.title != null
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
        'ids'            : [my_isbn]
    ]

    when:
    TitleInstancePackagePlatform tipp = new TitleInstancePackagePlatform(tmap)
//    tipp.ids = [my_isbn]

    tippService.matchTitle(tipp)

    then:
    tipp.title == book
  }

  @Rollback
  void "Test Package Update from TIPPs"() {
    given: // a package with unmatched TIPPs
    def pack = new Package(name: "Import Package")
    def platform = new Platform(name: "Import Platform")
    def aISBN = new Identifier(namespace: IdentifierNamespace.findByValue('isbn'), value: '978-11-655-6370-8')
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

    when:
    tippService.matchPackage(pack)

    then:
    tipp1.title == book1
    tipp2.title != null
  }
}
