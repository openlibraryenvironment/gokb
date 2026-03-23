package org.gokb

import grails.testing.mixin.integration.Integration
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

  def setup() {
    RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    RefdataValue combo_type_id = RefdataCategory.lookup('Combo.Type', 'KBComponent.Ids')
    IdentifierNamespace ns_doi = IdentifierNamespace.findByValue('doi')
    def old_id_date = java.sql.Date.valueOf(LocalDate.now().minusMonths(1))

    BookInstance test_ti = BookInstance.findByName('PackageCleanupTitle')
    Identifier new_id = Identifier.findByValue('10.2333/663633444') ?: new Identifier(value: '10.2333/663633444', namespace: ns_doi).save(flush: true, failOnError: true)
    Identifier old_id = Identifier.findByValue('10.2333/345435535') ?: new Identifier(value: '10.2333/345435535', namespace: ns_doi).save(flush: true, failOnError: true)

    if (!test_ti) {
      test_ti = new BookInstance(name: 'PackageCleanupTitle').save(flush: true, failOnError: true)
      test_ti.ids << new_id
      test_ti.save(flush: true)

      autoTimestampEventListener.withoutTimestamps {
        new Combo(fromComponent: test_ti, toComponent: old_id, type: combo_type_id, dateCreated: old_id_date).save(flush: true)
      }
    }
  }

  def cleanup() {

  }

  void "test selective ID removal by date"() {

  }
}
