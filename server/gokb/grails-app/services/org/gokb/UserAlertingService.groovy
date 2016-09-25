package org.gokb

import org.elasticsearch.client.Client
import org.elasticsearch.node.Node
import static org.elasticsearch.node.NodeBuilder.nodeBuilder
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.groovy.*
import org.elasticsearch.common.transport.InetSocketTransportAddress
import groovy.text.SimpleTemplateEngine
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware


class UserAlertingService implements ApplicationContextAware {

  def mailService
  ApplicationContext applicationContext

  static transactional = false

  def grailsApplication

  @javax.annotation.PostConstruct
  def init() {
    log.debug("UserAlertingService::init");
  }

  def sendAlertingEmail(user) {
    sendEmail(user);
  }

  def sendAllAlerts() {
    def rq = User.executeQuery('select u from User as u where u.send_alert_emails.value=:yes',[yes:'Yes']);
    log.debug("User list: ${rq}");
    rq.each {
      sendEmail(it);
    }
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
  }

  private def sendEmail(user) {

    log.debug("sendEmail....");

    def result = [:]
    result.msg = 'hello';

    def emailTemplateFile = applicationContext.getResource("WEB-INF/mail-templates/gokbAlerts.gsp").file
    def engine = new SimpleTemplateEngine()
    def tmpl = engine.createTemplate(emailTemplateFile).make(result)
    def content = tmpl.toString()

    mailService.sendMail {
      to user.email
      from 'GlobalOpenKB@gmail.com'
      subject "${grailsApplication.config.alerts.subject} - ${new Date()}"
      html content
    }

    log.debug("Send email");
  }


}
