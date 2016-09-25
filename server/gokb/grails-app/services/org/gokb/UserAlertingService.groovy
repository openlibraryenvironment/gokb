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
import org.gokb.cred.Folder


class UserAlertingService implements ApplicationContextAware {


  static String USER_ALERT_QRY = '''
select f, fi, work
from Folder as f,
     KBComponentFolderEntry as fi,
     TitleInstance as ti join ti.work as work,
     TitleInstance as title_in_group
where 
      ( title_in_group.work = work ) AND
      ( fi.folder = f ) AND
      ( ti = fi.linkedComponent ) AND
      ( ( f.owner = :user ) OR ( f.owner in ( select uom.memberOf from UserOrganisationMembership as uom where uom.party = :user ) ) )
'''


  def mailService
  ApplicationContext applicationContext

  static transactional = false

  def grailsApplication

  @javax.annotation.PostConstruct
  def init() {
    log.debug("UserAlertingService::init");
  }

  def sendAlertingEmail(user) {
    try {
      sendEmail(user);
    }
    catch ( Exception e ) {
      log.error("Error sending user email - ${user.email}",e)
    }
    finally {
      log.debug("Send complete");
    }
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
    result.updates = getTippsInUserWatchList(user)

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

    log.debug("Sent email");
  }

  private getTippsInUserWatchList(user) {
    // Return a query - Watch List, Watch Title, Watch Work, Changed Title, Changed Tipp
    // For any tipps that are on the users watch list
    def result = Folder.executeQuery(USER_ALERT_QRY,[user:user]);

    log.debug("Processing ${result.size} tipp hits for user watch lists");
    result
  }


}
