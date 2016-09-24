package org.gokb

import org.elasticsearch.client.Client
import org.elasticsearch.node.Node
import static org.elasticsearch.node.NodeBuilder.nodeBuilder
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.groovy.*
import org.elasticsearch.common.transport.InetSocketTransportAddress
import groovy.text.SimpleTemplateEngine

class UserAlertingService {

  def mailService

  static transactional = false

  def grailsApplication

  @javax.annotation.PostConstruct
  def init() {
    log.debug("UserAlertingService::init");
  }

  def sendAlertingEmail(user) {
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
  }

  private def sendEmail(result) {

    log.debug("sendEmail....");

    def rq = User.executeQuery('select u.email from User as u where u.recAdminEmails.value=:yes',[yes:'Yes']);
    log.debug("User list: ${rq}");

    def emailTemplateFile = applicationContext.getResource("WEB-INF/mail-templates/gokbAlerts.gsp").file
    def engine = new SimpleTemplateEngine()
    def tmpl = engine.createTemplate(emailTemplateFile).make(result)
    def content = tmpl.toString()

    if ( rq.size() > 0 ) {
      mailService.sendMail {
        // to grailsApplication.config.housekeeping.recipients.toArray()
        to rq.toArray()
        from 'GlobalOpenKB@gmail.com'
        subject "${grailsApplication.config.housekeeping.subject} - ${new Date()}"
        html content
      }
    }

    log.debug("Send email");
  }


}
