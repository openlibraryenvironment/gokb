package org.gokb

import grails.gsp.PageRenderer

import org.apache.commons.validator.routines.EmailValidator
import org.gokb.cred.JobResult
import org.gokb.cred.Package
import org.springframework.context.MessageSource

class CuratoryGroupAlertingService {
  def mailService
  def messageSource
  PageRenderer groovyPageRenderer

  def grailsApplication

	static final String EMAIL_LAYOUT = "/layouts/email"
  static final String JOB_CANCELLATION_ALERT_TEMPLATE = "/group/_cancelledJobAlert"

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
              errorCode: jr.resultJson?.messageCode ?: 'kbart.error'
            ]
          )

        EmailValidator validator = EmailValidator.getInstance()

        if (alerts_address && cg.email && validator.isValid(cg.email)) {
          try {
            mailService.sendMail {
              to cg.email
              from alerts_address
              subject messageSource.getMessage('spring.security.ui.register.support.email.subject', null, locale)
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

	private String renderEmail(String viewPath, String layoutPath, Map model) {
		String content = groovyPageRenderer.render(view: viewPath, model: model)
		return groovyPageRenderer.render(view: layoutPath, model: model << [content: content])
	}
}
