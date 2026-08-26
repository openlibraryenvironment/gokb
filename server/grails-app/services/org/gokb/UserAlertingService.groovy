package org.gokb

import org.apache.commons.validator.routines.EmailValidator
import groovy.text.SimpleTemplateEngine
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.gokb.cred.Folder
import org.gokb.cred.User


class UserAlertingService implements ApplicationContextAware {
  def mailService
  ApplicationContext applicationContext

  static transactional = false

  def sessionFactory
  def grailsApplication
  def dateFormatService

  @javax.annotation.PostConstruct
  def init() {
    log.debug("UserAlertingService::init");
  }

  public void sendAlertingEmail(user) {
    try {
      Date start_date = null

      if ( user.last_alert_check) {
        start_date = user.last_alert_check
      }
      else {
        // If the user never checked, give them 5 days worth
        start_date = new Date(System.currentTimeMillis() - (5*24*60*60*1000) )
      }
      Date end_date = new Date(System.currentTimeMillis())
      sendEmail(user, start_date, end_date)
      user.last_alert_check = end_date

      if (user.validate()) {
        user.save(flush:true, failOnError:true)
      }else{
        def errors = user.errors.allErrors

        log.debug("User Object could not be validated: ${errors}")
      }
    }
    catch ( Exception e ) {
      log.error("Error sending user email - ${user.email}",e)
    }
    finally {
      log.debug("Send complete")
    }
  }

  public void sendAllAlerts() {
    if (grailsApplication.config.getProperty('gokb.alerts.emailFrom')) {
      def rq = User.executeQuery('select u from User as u where u.send_alert_emails.value=:yes',[yes:'Yes'])
      Date start_date = new Date(System.currentTimeMillis() - (24*60*60*1000) )
      Date end_date = new Date(System.currentTimeMillis())
      log.debug("User list: ${rq}")

      rq.each {
        sendEmail(it, start_date, end_date)
      }
    }
    else {
      log.warn("Unable to send user alerts due to missing sender address config value 'gokb.alerts.emailFrom'!")
    }
  }

  private Map sendEmail(User user, Date startDate, Date endDate) {
    log.debug("sendEmail....");

    Map result = [:]
    result.start_date = startDate;
    result.end_date = endDate;
    result.serverUrl = grailsApplication.config.getProperty('grails.serverURL') ?: 'http://localhost:8080/gokb'
    result.updates = handleUserWatchList(user, startDate, endDate)

    File emailTemplateFile = applicationContext.getResource("WEB-INF/mail-templates/gokbAlerts.gsp").file
    SimpleTemplateEngine engine = new SimpleTemplateEngine()
    def tmpl = engine.createTemplate(emailTemplateFile).make(result)
    String alerts_address = grailsApplication.config.getProperty('gokb.alerts.emailFrom')
    String content = tmpl.toString()
    EmailValidator validator = EmailValidator.getInstance()
    Locale locale = new Locale(user.preferredLocaleString ?: grailsApplication.config.getProperty('gokb.support.locale', String, 'en'))

    if (user.email && validator.isValid(user.email)) {

      mailService.sendMail {
        to user.email
        from alerts_address
        subject messageSource.getMessage('gokb.alerts.subject', [new Date()], locale)
        html content
      }

      log.debug("Sent email")
    }
    else {
      log.debug("User ${user.username} has no valid email!")
    }
  }

  private Map handleUserWatchList(User user, Date startDate, Date endDate) {
    Map result = [:]

    List changed_component_ids = ComponentWatch.executeQuery('''select cw.component.id from ComponentWatch as cw
                                                              where cw.user = :user
                                                              and cw.component.lastUpdated > :start
                                                              and cw.component.lastUpdated <= :end
                                                              order by cw.component.name''',
                                                              [user: user, start: startDate, end: endDate])

    boolean more = true

    while (more) {
      List id_batch = changed_component_ids.take(50)
      changed_component_ids = changed_component_ids.drop(id_batch.size())
      String edit_base = grailsApplication.config.getProperty('gokb.uiUrl') ? grailsApplication.config.getProperty('gokb.uiUrl') : null

      List components = KBComponent.executeQuery('''from KBComponent where id in (:batch) order by name''', [batch: id_batch])

      for (obj in components) {
        String classKey = obj.niceName
        Map cmp_info = [id: obj.id, name: obj.name, uuid: obj.uuid, lastUpdated: dateFormatService.formatTimestampOffset(obj.lastUpdated)]

        if (classKey == 'Package') {
          String edit_base = grailsApplication.config.getProperty('gokb.uiUrl') ? grailsApplication.config.getProperty('gokb.uiUrl') + 'package/' : null
          cmp_info['changed_tipps_count'] = TitleInstancePackagePlatform.executeQuery('''select count(*) from TitleInstancePackagePlatform
                                                                                          where pkg = :pkg
                                                                                          and lastUpdated > :start
                                                                                          and lastUpdated <= :end''',
                                                                                          [pkg: obj, start: startDate, end: endDate])[0]

          if (edit_base) {
            cmp_info['editLink'] = edit_base + 'package/' + obj.uuid
          }
        }
        else if (['Journal', 'Book', 'Database', 'OtherInstance'].contains(classKey)) {
          cmp_info['changed_tipps_count'] = TitleInstancePackagePlatform.executeQuery('''select count(*) from TitleInstancePackagePlatform
                                                                                          where title = :title
                                                                                          and lastUpdated > :start
                                                                                          and lastUpdated <= :end''',
                                                                                          [title: obj, start: startDate, end: endDate])[0]

          if (edit_base) {
            cmp_info['editLink'] = edit_base + 'title/' + obj.uuid
          }
        }
        else if (classKey == 'TIPP') {
          cmp_info['title_changed'] = obj.title ? (obj.title.lastUpdated > startDate && obj.title.lastUpdated <= endDate) : false

          if (edit_base) {
            cmp_info['editLink'] = edit_base + 'package-title/' + obj.uuid
          }
        }

        if (!result[classKey]) {
          result[classKey] = []
        }

        result[classKey] << cmp_info
      }

      sessionFactory.currentSession.flush()
      sessionFactory.currentSession.clear()

      if (id_batch.size() < 50) {
        more = false
      }
    }

    result
  }
}
