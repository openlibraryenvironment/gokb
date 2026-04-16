package org.gokb

import grails.testing.mixin.integration.Integration

import java.time.*

import org.gokb.PackageCleanupService
import org.gokb.cred.*
import org.hibernate.SessionFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.Rollback
import org.springframework.transaction.annotation.Transactional

import spock.lang.Specification

@Integration
@Transactional
@Rollback
class PackageCleanupServiceSpec extends Specification {

  @Autowired
  PackageCleanupService packageCleanupService

  def autoTimestampEventListener
  LocalDate old_id_ld

  def setup() {
    RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    RefdataValue combo_type_id = RefdataCategory.lookup('Combo.Type', 'KBComponent.Ids')
    IdentifierNamespace ns_doi = IdentifierNamespace.findByValue('doi')
    old_id_ld = LocalDate.now().minusMonths(1)
    Date old_id_date = Date.from(LocalDateTime.now().minusMonths(1).atZone(ZoneId.systemDefault()).toInstant())

    BookInstance test_ti = BookInstance.findByName('PackageCleanupTitle')
    Identifier new_id = Identifier.findByValue('10.2333/663633444') ?: new Identifier(value: '10.2333/663633444', namespace: ns_doi).save(flush: true, failOnError: true)
    Identifier old_id = Identifier.findByValue('10.2333/345435535') ?: new Identifier(value: '10.2333/345435535', namespace: ns_doi).save(flush: true, failOnError: true)

    if (!test_ti) {
      test_ti = new BookInstance(name: 'PackageCleanupTitle').save(flush: true, failOnError: true)
      test_ti.ids << new_id
      test_ti.save(flush: true)

      autoTimestampEventListener.withoutTimestamps {
        Combo nc = new Combo(fromComponent: test_ti, toComponent: old_id, type: combo_type_id).save(flush: true, failOnError: true)
        nc.dateCreated = old_id_date
        nc.save(flush: true)
      }
    }

    Package package_cleanup_pkg = Package.findByName('PackageCleanupPackage')
    Org package_cleanup_provider = Org.findByName('PackageCleanupProvider') ?: new Org(name: 'PackageCleanupProvider').save(flush: true)
    Platform package_cleanup_platform = Platform.findByName('PackageCleanupPlatform') ?: new Platform(name: 'PackageCleanupProvider', provider: package_cleanup_provider).save(flush:true)

    if (!package_cleanup_pkg) {
      package_cleanup_pkg = new Package(name: 'PackageCleanupPackage', provider: package_cleanup_provider, nominalPlatform: package_cleanup_platform).save(flush: true)
    }

    TitleInstancePackagePlatform package_cleanup_tipp = TitleInstancePackagePlatform.findByName('PackageCleanupTipp')

    if (!package_cleanup_tipp) {
      package_cleanup_tipp = new TitleInstancePackagePlatform(name: 'PackageCleanupTipp', url: 'https://test.com/gokbcleanuptest', hostPlatform: package_cleanup_platform, pkg: package_cleanup_pkg, title: test_ti).save(flush: true, failOnError: true)
    }
  }

  def cleanup() {
    TitleInstancePackagePlatform.findByName('PackageCleanupTipp')?.expunge()
    BookInstance.findByName('PackageCleanupTitle')?.expunge()
  }

  void "test selective ID removal by date"() {
    given:
    Package package_cleanup_pkg = Package.findByName('PackageCleanupPackage')
    BookInstance test_ti = BookInstance.findByName('PackageCleanupTitle')

    Map total_pars = [
      pid: package_cleanup_pkg.id,
      cti: RefdataCategory.lookup('Combo.Type', 'KBComponent.Ids'),
      ctt: RefdataCategory.lookup('Combo.Type', 'TitleInstance.Tipps'),
      ctp: RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
    ]

    when:
    Map result = packageCleanupService.revertTitleIds(package_cleanup_pkg.id, old_id_ld)
    then:
    def combos = Combo.executeQuery('''select id, fromComponent.id from Combo as cid
                                        where type = :cti
                                        and exists (
                                          select 1 from Combo as ct
                                          where type = :ctt
                                          and fromComponent = cid.fromComponent
                                          and exists (
                                            select 1 from Combo as cp
                                            where type = :ctp
                                            and toComponent = ct.toComponent
                                            and fromComponent.id = :pid
                                          )
                                        )
                                        order by fromComponent.id''', total_pars)
    combos.size() == 1 || combos[1].dateCreated != null
    combos.size() == 1 || combos[0].dateCreated != null
    result.cleanups == 1
  }
}
