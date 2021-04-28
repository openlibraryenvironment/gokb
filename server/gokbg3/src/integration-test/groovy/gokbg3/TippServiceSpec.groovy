package gokbg3

import grails.testing.mixin.integration.Integration
import grails.testing.services.ServiceUnitTest
import org.gokb.*
import org.gokb.cred.BookInstance
import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.Package
import org.gokb.cred.Platform
import org.gokb.cred.RefdataCategory
import org.gokb.cred.TitleInstance
import org.gokb.cred.TitleInstancePackagePlatform
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.Rollback
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

@Integration
@Rollback
@Transactional
class TippServiceSpec extends Specification implements ServiceUnitTest<TippService> {

  private Package pkg
  private Platform plt
  private TitleInstance book, journal
  private Identifier isbn

  @Autowired
  TippService tippService

  def setup() {
    pkg = new Package(name: "Test Package").save()
    plt = new Platform(name: "Test Platform").save()
    isbn = new Identifier(namespace: IdentifierNamespace.findByValue('isbn'), value: '979-11-655-6390-5')
    book = new BookInstance(name:"Book 1", ids: [isbn])
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
        'pkg'         : pkg,
        'title'       : null,
        'hostPlatform': plt,
        'url'         : null,
        'uuid'        : UUID.randomUUID(),
        'status'      : "Current",
        'name'        : "Test Title Name from TIPP",
        'editStatus'  : "Approved",
        'language'    : "ger",
        'type': "Monograph"
    ]

    when:
    TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.tiplAwareCreate(tmap)
    tipp.publicationType = RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, "Monograph")

    tippService.matchTitle(tipp)

    then:
    tipp.title != null
  }

  void "Test attach existing title with a TIPP by its IDs"() {
    given:
    Identifier my_isbn = Identifier.findByNamespaceAndValue(IdentifierNamespace.findByValue('isbn'),'979-11-655-6390-5') ?: new Identifier(namespace: IdentifierNamespace.findByValue('isbn'), value: '979-11-655-6390-5')
    def tmap = [
        'pkg'         : pkg,
        'title'       : null,
        'hostPlatform': plt,
        'url'         : null,
        'uuid'        : UUID.randomUUID(),
        'status'      : "Current",
        'name'        : "Book 1",
        'editStatus'  : "Approved",
        'language'    : "ger",
        'type'        : "Monograph"
    ]

    when:
    TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.tiplAwareCreate(tmap)
    tipp.publicationType = RefdataCategory.lookup(TitleInstancePackagePlatform.RD_PUBLICATION_TYPE, "Monograph")
    tipp.ids = [my_isbn]

    tippService.matchTitle(tipp)

    then:
    tipp.title == book
  }
}
