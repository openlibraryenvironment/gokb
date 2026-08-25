package org.gokb

import grails.gsp.PageRenderer

import java.time.*

import org.apache.commons.validator.routines.EmailValidator
import org.gokb.cred.*
import org.springframework.context.MessageSource

class CuratoryGroupAlertingService {
  def mailService
  def messageSource
  PageRenderer groovyPageRenderer

  def grailsApplication
  def sessionFactory

	static final String EMAIL_LAYOUT = "/layouts/email"
  static final String JOB_CANCELLATION_ALERT_TEMPLATE = "/group/_cancelledJobAlert"
  static final Map DAILY_EMAIL_TYPES = [
    reviews: [
      template: "/group/_dailyJobReviewsAlerts"
    ],
    jobs: [
      template: "/group/_dailyCancelledJobsAlerts"
    ],
    externalRequests: [
      template: "/group/_dailyEditorialRequestAlerts"
    ]
  ]

  public Map sendJobFailureAlert(JobResult jr) {
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

  public Map triggerDailyJobsAlert(groupId, jobs) {
    Map result = [result: 'OK']
    CuratoryGroup obj = CuratoryGroup.get(groupId)

    if (obj) {
      Locale locale = new Locale(obj.preferredLocaleString ?: (grailsApplication.config.getProperty('gokb.support.locale') ?: 'en'))
      String edit_base = grailsApplication.config.getProperty('gokb.uiUrl') ? grailsApplication.config.getProperty('gokb.uiUrl') + 'package/' : null
      List jobs_table = jobs.collect { job -> [
                                      packageName: job.linkedItemName,
                                      packageId: job.linkedItemId,
                                      editLink: edit_base ? edit_base + "${job.linkedItemId}" : null,
                                      messageCode: job.messageCode
                                    ] }

      result = sendDailyAlertsForGroup(obj, locale, 'jobs', jobs_table)
    }
    else {
      log.error("Unable to resolve group from ID ${groupId}!")
      result.result = 'ERROR'
      result.message = "Unable to resolve group from ID ${groupId}!"
    }

    result
  }

  public Map triggerDailyReviewsAlerts() {
    Map result = [result: 'OK', report: [:]]
    String edit_base = grailsApplication.config.getProperty('gokb.uiUrl') ? grailsApplication.config.getProperty('gokb.uiUrl') + 'package/' : null
    Date lastDayDate = Date.from(LocalDateTime.now().minusHours(24).atZone(ZoneId.systemDefault()).toInstant())
    RefdataValue rr_open = RefdataCategory.lookup('ReviewRequest.Status', 'Open')
    RefdataValue combo_tipp = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
    CuratoryGroup zdb_admin = grailsApplication.config.getProperty("gokb.zdbAugment.rrCurators") ? CuratoryGroup.findByNameIlike(grailsApplication.config.getProperty("gokb.zdbAugment.rrCurators")) : null
    CuratoryGroup ezb_admin = grailsApplication.config.getProperty("gokb.ezbAugment.rrCurators") ? CuratoryGroup.findByNameIlike(grailsApplication.config.getProperty("gokb.ezbAugment.rrCurators")) : null
    def session = sessionFactory.currentSession

    List<JobResult> completed_jobs = JobResult.executeQuery('''select groupId, linkedItemId from JobResult
                                                    where linkedItemId is not null
                                                    and groupId is not null
                                                    and startTime > :lastDay''',
                                                [lastDay: lastDayDate])

    Map groups_list = [:]

    completed_jobs.each { jr ->
      if (!groups_list[jr[0]]) {
        groups_list[jr[0]] = []
      }

      groups_list[jr[0]] << jr[1]
    }

    groups_list.each { groupId, packageIdList ->
      CuratoryGroup cg = CuratoryGroup.get(groupId)

      if (cg) {
        log.debug("triggerDailyReviewsAlerts :: Processing group ${cg?.name} (ID ${groupId}) ..")
        Locale locale = new Locale(cg.preferredLocaleString ?: (grailsApplication.config.getProperty('gokb.support.locale') ?: 'en'))
        List table_items = []

        if (cg.newReviewsAlerts) {
          packageIdList.each { pid ->
            log.debug("triggerDailyReviewsAlerts :: Processing package ${pid}) ..")

            Package pkg = Package.get(pid)

            int num_new_reviews = ReviewRequest.executeQuery('''select count(rr.id) from ReviewRequest as rr
                                                                where status = :open
                                                                and dateCreated > :lastDay
                                                                and exists (
                                                                  select 1 from TitleInstancePackagePlatform as t
                                                                  where t.id = rr.componentToReview.id
                                                                  t.pkg = :pkg
                                                                )''',
                                                                [lastDay: lastDayDate, open: rr_open, ctype: combo_tipp, pkg: pkg])[0]

            if (num_new_reviews > 0) {
              log.debug("Got ${num_new_reviews} new reviews!")

              table_items << [
                packageName: pkg.name,
                packageId: pid,
                editLink: edit_base ? edit_base + "${pid}" : null,
                reviewsTotal: num_new_reviews
              ]
            }
          }

          if (table_items.size() > 0) {
            result.report[cg.name] = sendDailyAlertsForGroup(cg, locale, 'reviews', table_items)
          }
        }

        session.flush()
        session.clear()
      }
      else {
        log.warn("Skipping review alerts for missing groupID ${groupId}!")
      }
    }

    if (zdb_admin && zdb_admin.newReviewsAlerts) {
      result.report['zdb_editorial_reviews'] = processExternalEditorialReviews(zdb_admin)
    }

    if (ezb_admin && ezb_admin.newReviewsAlerts) {
      result.report['ezb_editorial_reviews'] = processExternalEditorialReviews(ezb_admin)
    }

    result
  }

  public Map processExternalEditorialReviews(group) {
    Map result = [result: 'OK']
    RefdataValue rr_open = RefdataCategory.lookup('ReviewRequest.Status', 'Open')
    Locale locale = new Locale(group.preferredLocaleString ?: (grailsApplication.config.getProperty('gokb.support.locale') ?: 'en'))
    Date lastDayDate = Date.from(LocalDateTime.now().minusHours(24).atZone(ZoneId.systemDefault()).toInstant())
    String edit_base = grailsApplication.config.getProperty('gokb.uiUrl') ? grailsApplication.config.getProperty('gokb.uiUrl') + 'review/' : null
    RefdataValue type_ext = RefdataCategory.lookup('ReviewRequest.StdDesc', 'External Editorial Request')

    def new_requests = ReviewRequest.executeQuery('''from ReviewRequest as rr
                                                     where status = :open
                                                     and stdDesc = :type
                                                     and exists (
                                                       select 1 from TitleInstance
                                                       where id = rr.componentToReview.id
                                                     )
                                                     and exists (
                                                       select 1 from AllocatedReviewGroup
                                                       where group = :grp
                                                       and review = rr
                                                     )
                                                     and dateCreated > :lastDay
                                                     ''',
                                                     [lastDay: lastDayDate, open: rr_open, type: type_ext, grp: group])

    if (new_requests.size() > 0) {
      List table_items = new_requests.collect { nr ->
        [
          editLink: edit_base ? edit_base + "${nr.id}" : null
        ]
      }

      result = sendDailyAlertsForGroup(group, locale, 'externalRequest', table_items)
    }
    else {
      result.result = 'SKIPPED_NO_REVIEWS'
    }

    result
  }

  private Map sendDailyAlertsForGroup(group, locale, type, items) {
    Map result = [result: 'OK']
    String support_address = grailsApplication.config.getProperty('gokb.support.emailTo')
    String alerts_address = grailsApplication.config.getProperty('gokb.alerts.emailFrom')

    Map template_params = [
      supportAddress: support_address,
      locale: locale,
      items: items,
      groupName: group.name
    ]

    def content = renderEmail(DAILY_EMAIL_TYPES[type].template, EMAIL_LAYOUT, template_params)

    EmailValidator validator = EmailValidator.getInstance()

    if (alerts_address && group.email && validator.isValid(group.email)) {
      try {
        mailService.sendMail {
          to group.email
          from alerts_address
          subject messageSource.getMessage('curatoryGroup.alert.daily.' + type + '.subject', null, locale)
          html content
        }

        log.debug("Daily ${type} alert mail sent for group ${group.name} (ID ${group.id})!")
      }
      catch (Exception e) {
        result.result = 'ERROR'
        log.error("Unable to send daily ${type} alert for group ${group.name} (ID ${group.id})!", e)
      }
    }
    else if (!alerts_address){
      log.debug("No support email entered!")
      result.result = 'SKIPPED_NO_CONFIG'
    }
    else {
      log.error("Email ${group.email} of group ${group.name} (ID ${group.id}) is not valid!")
      result.result = 'ERROR'
      result.message = "Unable to send mail to '${group.email}'!"
    }

    result
  }

	private String renderEmail(String viewPath, String layoutPath, Map model) {
		String content = groovyPageRenderer.render(view: viewPath, model: model)
		return groovyPageRenderer.render(view: layoutPath, model: model << [content: content])
	}
}
