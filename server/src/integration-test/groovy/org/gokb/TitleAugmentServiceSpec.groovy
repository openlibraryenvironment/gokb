package org.gokb

import com.k_int.ConcurrencyManagerService
import gokbg3.DateFormatService
import grails.testing.mixin.integration.Integration
import grails.testing.services.ServiceUnitTest
import java.time.LocalDateTime
import org.gokb.TitleAugmentService
import org.gokb.ZdbAPIService
import org.gokb.EzbAPIService
import org.gokb.cred.*
import org.hibernate.SessionFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.Rollback
import org.springframework.transaction.annotation.Transactional

import spock.lang.Specification

@Integration
@Transactional
@Rollback
class TitleAugmentServiceSpec extends Specification {

  @Autowired
  TitleAugmentService titleAugmentService

  @Autowired
  ZdbAPIService zdbAPIService

  @Autowired
  EzbAPIService ezbAPIService

  JournalInstance titleOne
  JournalInstance titleTwo
  Org publisher

  def setup() {
    publisher = Org.findByName("TitleAugmentTestOrg") ?: new Org(name: "TitleAugmentTestOrg").save(flush: true)
    IdentifierNamespace eissn_ns = IdentifierNamespace.findByValue('eissn')
    IdentifierNamespace issn_ns = IdentifierNamespace.findByValue('issn')
    IdentifierNamespace zdb_ns = IdentifierNamespace.findByValue('zdb')

    titleOne = JournalInstance.findByName("TitleAugmentTestTitleOne") ?: new JournalInstance(name: "TitleAugmentTestTitleOne").save(flush: true)
    Identifier eissn = Identifier.findByValueAndNamespace("2196-677X", eissn_ns) ?: new Identifier(value: '2196-677X', namespace: eissn_ns).save(flush: true)
    Identifier issn = Identifier.findByValueAndNamespace("0178-2312", issn_ns) ?: new Identifier(value: '0178-2312', namespace: issn_ns).save(flush: true)
    titleOne.ids << eissn
    titleOne.ids << issn
    titleOne.publisher << publisher
    titleOne.save(flush: true)

    titleTwo = JournalInstance.findByName("TitleAugmentTestTitleTwo") ?: new JournalInstance(name: "TitleAugmentTestTitleTwo").save(flush: true)
    Identifier zdb_id = Identifier.findByValueAndNamespace('2810346-4', zdb_ns) ?: new Identifier(value: '2810346-4', namespace: zdb_ns).save(flush: true)
    titleTwo.ids << zdb_id
    titleTwo.save(flush: true)
  }

  def cleanup() {
    titleOne.refresh().expunge()
    titleTwo.refresh().expunge()
  }

  void "test zdb augment with new id and name"() {
    when:
    titleAugmentService.augmentZdb(titleOne)
    then:
    sleep(500)
    titleOne.refresh()
    expect:
    titleOne.ids.size() == 3
    titleOne.name != 'TitleAugmentTestTitleOne'
    titleOne.publishedFrom != null
  }

  void "test zdb augment history linking"() {
    when:
    titleAugmentService.augmentZdb(titleOne)
    then:
    sleep(500)
    titleOne.refresh()
    expect:
    titleOne.titleHistory.size() == 1
  }

  void "test ezb augment"() {
    when:
    titleAugmentService.augmentEzb(titleOne)
    then:
    sleep(500)
    titleOne.refresh()
    expect:
    titleOne.ids.size() == 3
  }
}
