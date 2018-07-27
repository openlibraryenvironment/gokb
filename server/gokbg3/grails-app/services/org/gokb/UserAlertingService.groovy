package org.gokb

import org.apache.commons.validator.routines.EmailValidator
import groovy.text.SimpleTemplateEngine
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.gokb.cred.Folder
import org.gokb.cred.User


class UserAlertingService implements ApplicationContextAware {

  static String USER_ALERT_QRY = '''
select f, fi, work, title_in_group, ti, tipp
from Folder as f,
     KBComponentFolderEntry as fi,
     TitleInstance as ti,
     Work as work,
     TitleInstance as title_in_group,
     Combo as tipp_combo,
     TitleInstancePackagePlatform as tipp
where 
      ( ( tipp.accessStartDate between :startDate and :endDate ) OR ( tipp.accessEndDate between :startDate and :endDate ) ) AND
      ( ( tipp_combo.fromComponent = title_in_group ) and ( tipp_combo.type.value = 'TitleInstance.Tipps' ) and ( tipp_combo.toComponent = tipp ) ) AND
      ( title_in_group.work = work ) AND
      ( work = ti.work ) AND
      ( ti = fi.linkedComponent ) AND
      ( fi.folder = f ) AND
      ( ( f.owner = :user ) OR ( f.owner in ( select uom.memberOf from UserOrganisationMembership as uom where uom.party = :user ) ) )
order by f.id, ti.id, title_in_group.id
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
      Date start_date = null
      if ( user.last_alert_check) {
        start_date = user.last_alert_check
      }
      else {
        // If the user never checked, give them 5 days worth
        start_date = new Date(System.currentTimeMillis() - (5*24*60*60*1000) );
      }
      Date end_date = new Date(System.currentTimeMillis());
      sendEmail(user, start_date, end_date);
      user.last_alert_check = end_date

      if (user.validate()) {
        user.save(flush:true, failOnError:true);
      }else{
        def errors = user.errors.allErrors

        log.debug("User Object could not be validated: ${errors}")
      }
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
    Date start_date = new Date(System.currentTimeMillis() - (24*60*60*1000) );
    Date end_date = new Date(System.currentTimeMillis());
    log.debug("User list: ${rq}");
    rq.each {
      sendEmail(it, start_date, end_date);
    }
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
  }

  private def sendEmail(User user, Date startDate, Date endDate) {

    log.debug("sendEmail....");

    def result = [:]
    result.start_date = startDate;
    result.end_date = endDate;
    result.serverUrl = grailsApplication.config.serverUrl ?: 'http://localhost:8080/gokb'
    result.updates = getTippsInUserWatchList(user, startDate, endDate)

    def emailTemplateFile = applicationContext.getResource("WEB-INF/mail-templates/gokbAlerts.gsp").file
    def engine = new SimpleTemplateEngine()
    def tmpl = engine.createTemplate(emailTemplateFile).make(result)
    def content = tmpl.toString()
    EmailValidator validator = EmailValidator.getInstance();
    
    if (user.email && validator.isValid(user.email)) {

      mailService.sendMail {
        to user.email
        from "${grailsApplication.config.alerts.emailFrom ?: 'GOKb <user-alerts@gokb.org>'}"
        subject "${grailsApplication.config.alerts.subject ?: 'Your GOKb User Alerts'} - ${new Date()}"
        html content
      }

      log.debug("Sent email")
    }
    else {
      log.debug("User ${user.username} has no valid email!")
    }
  }

  private getTippsInUserWatchList(User user, Date start_date, Date end_date) {
    // Return a query - Watch List, Watch Title, Watch Work, Changed Title, Changed Tipp
    // For any tipps that are on the users watch list

    def modified_tipps = Folder.executeQuery(USER_ALERT_QRY,[user:user, startDate:start_date, endDate:end_date]);
    def result = []
    def current_folder = null;

    log.debug("Processing ${modified_tipps.size} tipp hits for user watch lists between ${start_date} and ${end_date}");
    modified_tipps.each { mt ->
      if ( current_folder?.id != mt[0].id ) {
        current_folder = [id:mt[0].id, name:mt[0].name, owner:mt[0].owner.displayName, titles:[]]
        result.add(current_folder)
      }

      current_folder.titles.add([watchlist_title:mt[4], watchlist_work:mt[2], matched_title:mt[3], tipp:mt[5]])
    }
    
    result
  }


}
