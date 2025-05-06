package org.gokb

import grails.gsp.PageRenderer

import org.apache.commons.validator.routines.EmailValidator
import org.gokb.cred.*
import org.springframework.context.MessageSource

class CuratoryGroupAlertingService {
  def mailService
  def messageSource
  PageRenderer groovyPageRenderer

  def grailsApplication

	static final String EMAIL_LAYOUT = "/layouts/email"
  static final String JOB_CANCELLATION_ALERT_TEMPLATE = "/group/_cancelledJobAlert"
  static final String DAILY_GROUP_ALERT_TEMPLATE = "/group/_dailyAlerts"

  def sendJobFailureAlert(JobResult jr) {
    log.debug("sendJobFailureAlert...");
    def result = [result: 'OK']
    def edit_link
    def support_address = grailsApplication.config.getProperty('gokb.support.emailTo')
    def alerts_address = grailsApplication.config.getProperty('gokb.alerts.emailFrom')

    Package.withNewSession {
      Package pkg = Package.get(jr.linkedItemId)

      def groups = pkg.curatoryGroups

      groups.each { cg ->
          Locale locale = new Locale(it.preferredLocaleString ?: (grailsApplication.config.getProperty('gokb.support.locale') ?: 'en'))

          if (grailsApplication.config.getProperty('gokb.uiUrl')) {
            edit_link = grailsApplication.config.getProperty('gokb.uiUrl') + "package/${pkg.uuid}"
          }
          else {
            edit_link = (grailsApplication.config.getProperty('grails.serverURL') ?: 'http://localhost:8080/gokb')

            edit_link += "/resource/show/${pkg.uuid}"
          }

          def content = renderEmail(
            JOB_CANCELLATION_ALERT_TEMPLATE, EMAIL_LAYOUT,
            [
              url: edit_link,
              contact: support_address,
              locale: locale,
              startTime: jr.startTime,
              endTime: jr.endTime,
              errorCode: jr.resultJson?.messageCode ?: 'kbart.errors.url.unknown'
            ]
          )

        EmailValidator validator = EmailValidator.getInstance()

        if (alerts_address && cg.email && validator.isValid(cg.email)) {
          try {
            mailService.sendMail {
              to cg.email
              from alerts_address
              subject messageSource.getMessage('curatoryGroup.alert.cancelledImport.subject', null, locale)
              html content
            }

            log.debug("Mail sent!")
          }
          catch (Exception e) {
            result.result = 'ERROR'
            log.error("Unable to send registration alert!", e)
          }
        }
        else if (!support_address){
          log.debug("No support email entered!")
          result.result = 'SKIPPED'
        }
        else {
          log.error("Config value at (gokb.support.emailTo) is not a valid address!")
          result.result = 'ERROR'
        }
      }
    }

    result
  }

  def triggerDailyJobsAlert(groupId, jobs) {
    def result = [result: 'OK']

    CuratoryGroup.withNewSession {
      def obj = CuratoryGroup.get(groupId)

      if (obj) {
        Locale locale = new Locale(obj.preferredLocaleString ?: (grailsApplication.config.getProperty('gokb.support.locale') ?: 'en'))
        String edit_base = grailsApplication.config.getProperty('gokb.uiUrl') ? grailsApplication.config.getProperty('gokb.uiUrl') + 'package/' : null
        def jobs_table = jobs.collect { [
                                        packageName: it.linkedItemName,
                                        packageId: it.linkedItemId,
                                        editLink: edit_base ? edit_base + "${it.linkedItemId}" : null,
                                        messageCode: it.messageCode
                                      ] }

        result = sendDailyAlertsForGroup(obj.email, locale, 'jobs', jobs_table)
      }
      else {
        result.result = 'ERROR'
        result.message = 'Unable to resolve group from ID ${groupId}!'
      }
    }

    result
  }

  def triggerDailyReviewsAlert(groupId) {
    // TODO
  }

  def sendDailyAlertsForGroup(cg_address, locale, type, items) {
    def result = [result: 'OK']
    def support_address = grailsApplication.config.getProperty('gokb.support.emailTo')
    def alerts_address = grailsApplication.config.getProperty('gokb.alerts.emailFrom')

    def template_params = [
      supportAddress: support_address,
      locale: locale
    ]

    if (type == 'jobs') {
      template_params.jobs = items
    }
    else if (type == 'reviews') {
      template_params.reviews = items
    }

    def content = renderEmail(DAILY_GROUP_ALERT_TEMPLATE, EMAIL_LAYOUT, template_params)

    EmailValidator validator = EmailValidator.getInstance()

    if (alerts_address && cg_address && validator.isValid(cg_address)) {
      try {
        mailService.sendMail {
          to cg_address
          from alerts_address
          subject messageSource.getMessage('curatoryGroup.alert.daily.' + type + '.subject', null, locale)
          html content
        }

        log.debug("Mail sent!")
      }
      catch (Exception e) {
        result.result = 'ERROR'
        log.error("Unable to send registration alert!", e)
      }
    }
    else if (!support_address){
      log.debug("No support email entered!")
      result.result = 'SKIPPED'
    }
    else {
      log.error("Config value at (gokb.support.emailTo) is not a valid address!")
      result.result = 'ERROR'
    }

    result
  }

	private String renderEmail(String viewPath, String layoutPath, Map model) {
		String content = groovyPageRenderer.render(view: viewPath, model: model)
		return groovyPageRenderer.render(view: layoutPath, model: model << [content: content])
	}
}
