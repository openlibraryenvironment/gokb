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
class PlatformServiceSpec extends Specification {

  @Autowired
  PlatformService platformService

  @Autowired
  TippUpsertService tippUpsertService

  @Autowired
  SessionFactory sessionFactory

  def setup() {
    Org publisher = Org.findByName("PlatformService Test Org") ?: new Org(name: "PlatformService Test Org").save(flush: true)
    Platform old_plt = Platform.findByName("PlatformService Test Platform Merge Old") ?: new Platform(name: "PlatformService Test Platform Merge Old", provider: publisher).save(flush: true)
    Platform new_plt = Platform.findByName("PlatformService Test Platform Merge New") ?: new Platform(name: "PlatformService Test Platform Merge New", provider: publisher).save(flush: true)
    Package pkg = Package.findByName("PlatformService Test Package") ?: new Package(name: "PlatformService Test Package", nominalPlatform: old_plt, provider: publisher).save(flush: true)

    IdentifierNamespace issn_ns = IdentifierNamespace.findByValue('issn')
    IdentifierNamespace eissn_ns = IdentifierNamespace.findByValue('eissn')

    Identifier issn = Identifier.findByNamespaceAndValue(issn_ns, '0128-5483') ?: new Identifier(namespace: issn_ns, value: '0128-5483')
    Identifier eissn = Identifier.findByNamespaceAndValue(eissn_ns, '2180-4338') ?: new Identifier(namespace: eissn_ns, value: '2180-4338')

    JournalInstance journal = JournalInstance.findByName("PlatformService Journal")

    if (!journal) {
      journal = new JournalInstance(name: "PlatformService Journal").save(flush:true)
      journal.ids.addAll([issn, eissn])
      journal.save(flush: true)
    }

    TitleInstancePackagePlatform test_tipp = TitleInstancePackagePlatform.findByName("Test TIPP platform change")

    if (!test_tipp) {
      def tmap = [
        pkg            : pkg.id,
        hostPlatform   : old_plt.id,
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
    Package.findByName("PlatformService Test Package")?.expunge()
    Platform.findByName("PlatformService Test Platform Merge Old")?.expunge()
    Platform.findByName("PlatformService Test Platform Merge New")?.expunge()
    Org.findByName("PlatformService Test Org")?.expunge()
    JournalInstance.findByName("PlatformService Journal")?.expunge()
  }

  void "test platform merge"() {
    given:
    def old_plt = Platform.findByName("PlatformService Test Platform Merge Old")
    def new_plt = Platform.findByName("PlatformService Test Platform Merge New")
    when:
    def result = platformService.merge(old_plt, new_plt)
    then:
    result.result == 'OK'
    result.tipps == 1
    result.tipls == 1
    result.pkgs == 1
    old_plt.status.value == 'Deleted'
    def moved_tipp = TitleInstancePackagePlatform.executeQuery("from TitleInstancePackagePlatform as t where exists (select 1 from Combo where fromComponent = :np and toComponent = t)", [np: new_plt])
    moved_tipp.size() == 1

    def moved_tipl = TitleInstancePlatform.executeQuery("from TitleInstancePlatform as t where exists (select 1 from Combo where fromComponent = :np and toComponent = t)", [np: new_plt])
    moved_tipl.size() == 1

    def pkg = Package.findByName("PlatformService Test Package")
    pkg.nominalPlatform == new_plt
  }
}
