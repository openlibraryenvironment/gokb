package org.gokb

import com.icegreen.greenmail.util.GreenMailUtil
import grails.plugin.greenmail.GreenMail
import grails.plugins.mail.MailService
import grails.testing.mixin.integration.Integration
import javax.mail.internet.MimeMessage
import org.gokb.CleanupService
import org.gokb.CuratoryGroupAlertingService
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

  Package pkg

  void setup () {
    CuratoryGroup group = CuratoryGroup.findByName('GroupAlertsGroup') ?: new CuratoryGroup(name: 'GroupAlertsGroup', email: 'test@gokb.org').save(flush: true)

    Map source_info = [
      name: "GroupAlertingPackageTest",
      url: 'http://test-url.com/test'
    ]

    Source pkg_source = Source.findByName("GroupAlertingPackageTest") ?: new Source(source_info).save(flush: true)

    if (!Package.findByName("GroupAlertingPackageTest")) {
      pkg = new Package(name: "GroupAlertingPackageTest", source: pkg_source).save(flush: true)
      pkg.curatoryGroups.add(group)
      pkg.save(flush: true)
    }
  }

  void cleanup() {
    greenMail.deleteAllMessages()
    Package.findByName("GroupAlertingPackageTest")?.expunge()
    Source.findByName("GroupAlertingPackageTest")?.expunge()
    CuratoryGroup.findByName('GroupAlertsGroup')?.expunge()
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
}
