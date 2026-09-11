package org.gokb

import grails.testing.mixin.integration.Integration
import org.gokb.CleanupService
import org.gokb.cred.*
import org.hibernate.SessionFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.Rollback
import org.springframework.transaction.annotation.Transactional

import spock.lang.Specification

@Integration
@Transactional
@Rollback
class CleanupServiceSpec extends Specification {

  @Autowired
  CleanupService cleanupService

  JournalInstance titleOne
  JournalInstance titleTwo
  TitleInstancePackagePlatform tippActive

  def setup() {
    RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')

    titleOne = JournalInstance.findByName("CleanupHistoryTestTitleOne") ?: new JournalInstance(name: "CleanupHistoryTestTitleOne").save(flush: true, failOnError: true)
    Org cleanupHistoryOrg = Org.findByName("CleanupHistoryOrg") ?: new Org(name: "CleanupHistoryOrg").save(flush: true, failOnError: true)
    Platform cleanupHistoryPlatform = Platform.findByName("CleanupHistoryPlatform") ?: new Platform(name: "CleanupHistoryPlatform", provider: cleanupHistoryOrg).save(flush: true, failOnError: true)

    Package cleanupHistoryPackage = Package.findByName("CleanupHistoryPackage") ?: new Package(name: "CleanupHistoryPackage", provider: cleanupHistoryOrg, nominalPlatform: cleanupHistoryPlatform).save(flush: true, failOnError: true)
    Identifier url_doi = Identifier.findByValue('http://doi.org/10.23242/354-234234-233-23') ?: new Identifier(value: 'http://doi.org/10.23242/354-234234-233-23', namespace: IdentifierNamespace.findByValue('doi')).save(flush: true, validate: false)

    tippActive = TitleInstancePackagePlatform.findByName("CleanupHistoryTestTipp")

    if (!tippActive) {
      Map tippInfo = [
        pkg: cleanupHistoryPackage,
        nominalPlatform: cleanupHistoryPlatform,
        title: titleOne,
        url: "http://tets-url.com/testcleanup",
        name: "CleanupHistoryTestTipp"
      ]

      tippActive = new TitleInstancePackagePlatform(tippInfo).save(flush: true)
      tippActive.addIdentifier(url_doi)
    }

    titleTwo = JournalInstance.findByName("CleanupHistoryTestTitleTwo") ?: new JournalInstance(name: "CleanupHistoryTestTitleTwo", status: status_deleted).save(flush: true, failOnError: true)

    if (titleTwo?.titleHistory?.size() == 0) {
      ComponentHistoryEvent event = new ComponentHistoryEvent(eventDate: new Date()).save(flush: true, failOnError: true)
      event.addToParticipants(participant: titleTwo, participantRole: 'out')
      event.addToParticipants(participant: titleOne, participantRole: 'in')
      event.save(flush: true, failOnError: true)
    }
    else {
      log.debug("Existing history!")
    }

    BookInstance book_doi = BookInstance.findByName("CleanupTestNewDoiBook") ?: new BookInstance(name: "CleanupTestNewDoiBook").save(flush: true, failOnError: true)

    TitleInstancePackagePlatform tipp_doi = TitleInstancePackagePlatform.findByName("CleanupTestNewDoiTipp")

    if (!tipp_doi) {
      Map tipp_map = [
        name: "CleanupTestNewDoiTipp",
        pkg: cleanupHistoryPackage,
        hostPlatform: cleanupHistoryPlatform,
        title: book_doi,
        importId: '10.23434/234666523X',
        url: 'http://doi.org/10.23434/234666523X'
      ]

      tipp_doi = new TitleInstancePackagePlatform(tipp_map).save(flush: true, failOnError: true)
    }
  }

  def cleanup() {
    TitleInstancePackagePlatform.findByName("CleanupHistoryTestTipp")?.refresh().expunge()
    TitleInstancePackagePlatform.findByName("CleanupTestNewDoiTipp")?.expunge()
    Package.findByName("CleanupHistoryPackage")?.refresh().expunge()
    Platform.findByName("CleanupHistoryPlatform")?.refresh().expunge()
    JournalInstance.findByName("CleanupHistoryTestTitleOne")?.expunge()
    JournalInstance.findByName("CleanupHistoryTestTitleTwo")?.expunge()
    BookInstance.findByName("CleanupTestNewDoiBook")?.expunge()
    Identifier.findByValue('http://doi.org/10.23242/354-234234-233-23')?.expunge()
    Identifier.findByValue('10.23242/354-234234-233-23')?.expunge()
    Identifier.findByValue('10.23434/234666523X')?.expunge()
  }

  void "test deleteOrphanedHistoryEvents"() {
    when:
    def eventDate = ComponentHistoryEventParticipant.findByParticipant(titleTwo).event.eventDate
    cleanupService.deleteOrphanedHistoryEvents()
    then:
    tippActive.refresh()
    titleOne.refresh()
    titleTwo.refresh()
    expect:
    eventDate != null
    titleOne.titleHistory.size() == 0
    tippActive.lastUpdated > eventDate
    titleOne.lastUpdated > eventDate
  }

  void "test fixDoiUrlIds"() {
    when:
    int result = cleanupService.fixDoiUrlIds()
    then:
    result == 1
    Identifier.findByValue('10.23242/354-234234-233-23') != null
  }

  void "test ensureTipls"() {
    when:
    Map result = cleanupService.ensureTipls()
    then:
    result.new_tipls == 1
  }

  void "test generateTitleDOIsFromTippInfo"() {
    when:
    Map result = cleanupService.generateTitleDOIsFromTippInfo()
    then:
    result.result == 'OK'
    result.counts['LINKED'] == 1
    BookInstance doi_ti = BookInstance.findByName("CleanupTestNewDoiBook")
    doi_ti.ids.size() == 1
    doi_ti.ids[0].value == '10.23434/234666523X'
  }
}
