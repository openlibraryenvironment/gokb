package org.gokb


import grails.testing.mixin.integration.Integration
import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.Rollback
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

@Integration
@Transactional
@Rollback
class EzbCollectionServiceSpec extends Specification {

  @Autowired
  EzbCollectionService ezbCollectionService

  def setup() {
  }

  def cleanup() {
  }

  void "test buildPackageName"() {

  }
}
