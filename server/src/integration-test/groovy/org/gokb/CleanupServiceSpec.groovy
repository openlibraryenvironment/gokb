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
    def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')

    titleOne = JournalInstance.findByName("CleanupHistoryTestTitleOne") ?: new JournalInstance(name: "CleanupHistoryTestTitleOne").save(flush: true, failOnError: true)

    def cleanupHistoryPackage = Package.findByName("CleanupHistoryPackage") ?: new Package(name: "CleanupHistoryPackage").save(flush: true, failOnError: true)
    def cleanupHistoryPlatform = Platform.findByName("CleanupHistoryPlatform") ?: new Platform(name: "CleanupHistoryPlatform").save(flush: true, failOnError: true)
    def url_doi = Identifier.findByValue('http://doi.org/10.23242/354-234234-233-23') ?: new Identifier(value: 'http://doi.org/10.23242/354-234234-233-23', namespace: IdentifierNamespace.findByValue('doi')).save(flush: true, validate: false)
    def test_doi

    tippActive = TitleInstancePackagePlatform.findByName("CleanupHistoryTestTipp") ?: new TitleInstancePackagePlatform(name: "CleanupHistoryTestTipp", url: "http://tets-url.com/testcleanup").save(flush: true, failOnError: true)

    if (tippActive.pkg == null) {
      tippActive.pkg = cleanupHistoryPackage
      tippActive.hostPlatform = cleanupHistoryPlatform
      tippActive.title = titleOne
      tippActive.ids << url_doi

      tippActive.save(flush: true, failOnError: true)
    }

    titleTwo = JournalInstance.findByName("CleanupHistoryTestTitleTwo") ?: new JournalInstance(name: "CleanupHistoryTestTitleTwo", status: status_deleted).save(flush: true, failOnError: true)

    if (titleTwo?.titleHistory?.size() == 0) {
      def event = new ComponentHistoryEvent(eventDate: new Date()).save(flush: true, failOnError: true)
      event.addToParticipants(participant: titleTwo, participantRole: 'out')
      event.addToParticipants(participant: titleOne, participantRole: 'in')
      event.save(flush: true, failOnError: true)
    }
    else {
      log.debug("Existing history!")
    }

    def book_doi = BookInstance.findByName("CleanupTestNewDoiBook") ?: new BookInstance(name: "CleanupTestNewDoiBook").save(flush: true, failOnError: true)

    def tipp_doi = TitleInstancePackagePlatform.findByName("CleanupTestNewDoiTipp")

    if (!tipp_doi) {
      def tipp_map = [
        name: "CleanupTestNewDoiTipp",
        pkg: cleanupHistoryPackage,
        hostPlatform: cleanupHistoryPlatform,
        title: book_doi,
        importId: '10.23434/234666523X'
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
    def result = cleanupService.fixDoiUrlIds()
    then:
    result == 1
    Identifier.findByValue('10.23242/354-234234-233-23') != null
  }

  void "test ensureTipls"() {
    when:
    def result = cleanupService.ensureTipls()
    then:
    result.new_tipls == 1
  }

  void "test generateTitleDOIsFromTippInfo"() {
    when:
    def result = cleanupService.generateTitleDOIsFromTippInfo()
    then:
    result.result == 'OK'
    result.counts['LINKED'] == 1
    def doi_ti = BookInstance.findByName("CleanupTestNewDoiBook")
    doi_ti.ids.size() == 1
    doi_ti.ids[0].value == '10.23434/234666523X'
  }
}
