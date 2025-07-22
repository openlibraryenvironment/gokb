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
class OrgServiceSpec extends Specification {

  @Autowired
  OrgService orgService

  @Autowired
  TippUpsertService tippUpsertService

  @Autowired
  TitleAugmentService titleAugmentService

  @Autowired
  SessionFactory sessionFactory

  def setup() {
    Org old_org = Org.findByName("OrgService Test Org Old") ?: new Org(name: "OrgService Test Org Old").save(flush: true, failOnError: true)

    def new_variant = old_org.ensureVariantName("TestOrgServiceVariant")
    new_variant.save(flush: true, failOnError: true)

    IdentifierNamespace viaf_ns = IdentifierNamespace.findByValue('viaf')
    Identifier viaf_id = Identifier.findByNamespaceAndValue(viaf_ns, '0125483') ?: new Identifier(namespace: viaf_ns, value: '0125483').save(flush: true, failOnError: true)

    old_org.ids << viaf_id
    old_org.save(flush:true, failOnError: true)

    Org new_org = Org.findByName("OrgService Test Org New") ?: new Org(name: "OrgService Test Org New").save(flush: true, failOnError: true)
    Platform plt = Platform.findByName("OrgService Test Platform") ?: new Platform(name: "OrgService Test Platform", provider: old_org).save(flush: true, failOnError: true)
    Package pkg = Package.findByName("OrgService Test Package") ?: new Package(name: "OrgService Test Package", nominalPlatform: plt, provider: old_org).save(flush: true, failOnError: true)

    IdentifierNamespace issn_ns = IdentifierNamespace.findByValue('issn')
    IdentifierNamespace eissn_ns = IdentifierNamespace.findByValue('eissn')

    Identifier issn = Identifier.findByNamespaceAndValue(issn_ns, '0128-5483') ?: new Identifier(namespace: issn_ns, value: '0128-5483').save(flush: true, failOnError: true)
    Identifier eissn = Identifier.findByNamespaceAndValue(eissn_ns, '2180-4338') ?: new Identifier(namespace: eissn_ns, value: '2180-4338').save(flush: true, failOnError: true)

    JournalInstance journal = JournalInstance.findByName("OrgService Journal")

    if (!journal) {
      journal = new JournalInstance(name: "OrgService Journal").save(flush:true)
      journal.ids.addAll([issn, eissn])
      journal.save(flush: true)

      journal.publisher << old_org
      journal.save(flush: true)
    }

    TitleInstancePackagePlatform test_tipp = TitleInstancePackagePlatform.findByName("Test TIPP platform change")

    if (!test_tipp) {
      def tmap = [
        pkg            : pkg.id,
        hostPlatform   : plt.id,
        title          : journal.id,
        url            : "http://test-url.net/",
        status         : "Current",
        name           : "Test TIPP platform change",
        editStatus     : "Approved",
        language       : "ger",
        publicationType: "Serial"
      ]

      test_tipp = tippUpsertService.upsertDTO(tmap)

      test_tipp.ids.addAll([issn, eissn])
      test_tipp.save(flush: true)
    }
  }

  def cleanup() {
    TitleInstancePackagePlatform.findByName("Test TIPP platform change")?.expunge()
    TitleInstancePlatform.findByUrl("http://test-url.net/")?.expunge()
    Package.findByName("OrgService Test Package")?.expunge()
    Platform.findByName("OrgService Test Platform")?.expunge()
    Org.findByName("OrgService Test Org Old")?.expunge()
    Org.findByName("OrgService Test Org New")?.expunge()
    JournalInstance.findByName("OrgService Journal")?.expunge()
  }

  void "test package transfer"() {
    given:
    def old_org = Org.findByName("OrgService Test Org Old")
    def new_org = Org.findByName("OrgService Test Org New")
    when:
    def result = orgService.transferPackages(old_org, new_org)
    then:
    result.result == 'OK'
    result.transferred == 1
    old_org.status.value == 'Current'

    def pkg = Package.findByName("OrgService Test Package")
    pkg.provider == new_org

    def title = JournalInstance.findByName("OrgService Journal")
    title.currentPublisher == old_org
  }

  void "test full merge"() {
    given:
    def old_org = Org.findByName("OrgService Test Org Old")
    def new_org = Org.findByName("OrgService Test Org New")
    def timestamp = new Date()
    when:
    sleep(1000)
    def result = orgService.mergeDuplicate(old_org, new_org)
    then:
    result.result == 'OK'
    result.ti == 1
    result.pkgs == 1
    result.plts == 1

    sleep(1000)
    old_org.status.value == 'Deleted'

    def pkg = Package.findByName("OrgService Test Package")
    pkg.provider == new_org

    def plt = Platform.findByName("OrgService Test Platform")
    plt.provider == new_org

    def title = JournalInstance.findByName("OrgService Journal")
    title.currentPublisher == new_org

    def tipp = TitleInstancePackagePlatform.findByName("Test TIPP platform change")
    tipp.refresh()
    tipp.lastUpdated >= timestamp

    new_org.refresh()
    new_org.variantNames.size() == 2

    new_org.ids?.size() == 1
  }
}
