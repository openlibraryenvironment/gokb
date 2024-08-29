package org.gokb

import grails.gsp.PageRenderer

import org.apache.commons.validator.routines.EmailValidator
import org.gokb.cred.User
import org.springframework.context.MessageSource

class AdminAlertingService {
  def mailService
  def messageSource
  PageRenderer groovyPageRenderer

  def grailsApplication

	static final String EMAIL_LAYOUT = "/layouts/email"
  static final String REGISTRATION_ALERT_TEMPLATE = "/register/_registerAlertMail"

  def sendRegistrationAlert(User user) {
    log.debug("sendRegistrationAlert....");
    def result = [result: 'OK']
    def edit_link
    def support_address = grailsApplication.config.getProperty('gokb.support.emailTo')
    def alerts_address = grailsApplication.config.getProperty('gokb.alerts.emailFrom')
    Locale locale = new Locale(grailsApplication.config.getProperty('gokb.support.locale') ?: 'en')

    if (grailsApplication.config.getProperty('gokb.uiUrl')) {
      edit_link = grailsApplication.config.getProperty('gokb.uiUrl') + '#/user/' + user.id.toString()
    }
    else {
      edit_link = (grailsApplication.config.getProperty('grails.serverURL') ?: 'http://localhost:8080/gokb')

      edit_link += "${'/resource/show/org.gokb.cred.User:' + user.id}"
    }

    def content = renderEmail(
      REGISTRATION_ALERT_TEMPLATE, EMAIL_LAYOUT,
      [
        url   : edit_link,
        locale: locale
      ]
    )

    EmailValidator validator = EmailValidator.getInstance()

    if (alerts_address && support_address && validator.isValid(support_address)) {
      try {
        mailService.sendMail {
          to support_address
          from alerts_address
          subject messageSource.getMessage('spring.security.ui.register.support.email.subject', null, locale)
          html content
        }

        log.debug("Sent email")
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
