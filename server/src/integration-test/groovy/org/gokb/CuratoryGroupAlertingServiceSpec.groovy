package org.gokb

import com.icegreen.greenmail.util.GreenMailUtil
import grails.plugin.greenmail.GreenMail
import grails.plugins.mail.MailService
import grails.testing.mixin.integration.Integration
import javax.mail.internet.MimeMessage
import org.gokb.CleanupService
import org.gokb.CuratoryGroupAlertingService
import org.gokb.TippUpsertService
import org.gokb.cred.*
import org.hibernate.SessionFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.Rollback
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

@Integration
@Transactional
@Rollback
class CuratoryGroupAlertingServiceSpec extends Specification {

  @Autowired
  CuratoryGroupAlertingService cgAlertingService

  @Autowired
  GreenMail greenMail

  @Autowired
  MailService mailService

  @Autowired
  TippUpsertService tippUpsertService

  Package pkg

  void setup () {
    CuratoryGroup group = CuratoryGroup.findByName('GroupAlertsGroup') ?: new CuratoryGroup(name: 'GroupAlertsGroup', email: 'test@gokb.org', newReviewsAlerts: true).save(flush: true)
    Org publisher = Org.findByName("GroupAlertingOrg") ?: new Org(name: "GroupAlertingOrg").save(flush: true)
    Platform plt = Platform.findByName("GroupAlertingPlatform") ?: new Platform(name: "GroupAlertingPlatform", provider: publisher).save(flush: true)

    Map source_info = [
      name: "GroupAlertingPackageTest",
      url: 'http://test-url.com/test'
    ]

    Source pkg_source = Source.findByName("GroupAlertingPackageTest") ?: new Source(source_info).save(flush: true)

    if (!Package.findByName("GroupAlertingPackageTest")) {
      pkg = new Package(name: "GroupAlertingPackageTest", source: pkg_source, provider: publisher, nominalPlatform: plt).save(flush: true)
      pkg.curatoryGroups.add(group)
      pkg.save(flush: true)
    }

    TitleInstancePackagePlatform rr_tipp = TitleInstancePackagePlatform.findByName("GroupAlertingTipp")

    if (!rr_tipp) {
      rr_tipp = tippUpsertService.tiplAwareCreate(name: "GroupAlertingTipp", pkg: pkg, hostPlatform: plt, url: 'http://groupalerttest.test/test', publicationType: "Serial")

      rr_tipp.save(flush: true)

      ReviewRequest.raise(rr_tipp, "Check IDs")

      JobResult jr = new JobResult(
        uuid        : UUID.randomUUID().toString(),
        description : "Test group alerting JobResult",
        resultObject: null,
        type        : RefdataCategory.lookup('Job.Type', 'KBARTSourceIngest'),
        statusText  : 'OK',
        groupId     : (group.id),
        startTime   : (new Date()),
        endTime     : (new Date()),
        linkedItemId: (pkg.id)
      ).save(flush: true)
    }
  }

  void cleanup() {
    greenMail.deleteAllMessages()
    Package.findByName("GroupAlertingPackageTest")?.expunge()
    Source.findByName("GroupAlertingPackageTest")?.expunge()
    CuratoryGroup.findByName('GroupAlertsGroup')?.expunge()
    TitleInstancePackagePlatform.findByName("GroupAlertingTipp")?.expunge()
    Platform.findByName("GroupAlertingPlatform")?.expunge()
    Org.findByName("GroupAlertingOrg")?.expunge()

    def all_reviews_ids = ReviewRequest.list().collect { it.id }

    all_reviews_ids.each { rrid ->
      ReviewRequest.findById(rrid)?.expunge()
    }
  }

  void "test triggerDailyJobsAlert"() {
    given:
    def group = CuratoryGroup.findByName('GroupAlertsGroup')
    def testJobResults = [
      [
        linkedItemId: pkg.id,
        linkedItemName: pkg.name,
        messageCode: 'kbart.errors.url.mimeType'
      ]
    ]

    when:
    def result = cgAlertingService.triggerDailyJobsAlert(group.id, testJobResults)
    sleep(2000)

    then:
    result.result == 'OK'
    greenMail.receivedMessages.length == 1
  }

  void "test triggerDailyReviewsAlert"() {
    given:
    def group = CuratoryGroup.findByName('GroupAlertsGroup')
    when:
    def result = cgAlertingService.triggerDailyReviewsAlerts()
    sleep(2000)

    then:
    result.result == 'OK'
    result.report[group.name]?.size() == 1
    greenMail.receivedMessages.length == 1
  }
}
