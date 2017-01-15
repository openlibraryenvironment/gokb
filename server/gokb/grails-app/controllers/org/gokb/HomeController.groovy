package org.gokb

import org.springframework.security.access.annotation.Secured;
import grails.util.GrailsNameUtils

import org.gokb.cred.*
import org.hibernate.SessionFactory;
import org.hibernate.transform.AliasToEntityMapResultTransformer


class HomeController {

  def grailsApplication
  def springSecurityService
  def userAlertingService
  def passwordEncoder

  SessionFactory sessionFactory

  static stats_cache = null;
  static stats_timestamp = null;

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def dashboard() {
    if ( ( stats_timestamp == null )|| ( System.currentTimeMillis() - stats_timestamp > 3600000 ) ) {
      stats_timestamp = System.currentTimeMillis()
      // Initialise
      stats_cache = [widgets:[:]];
      stats_cache = calculate();
    }
    else {
      log.debug("stats from cache...");
    }

    return stats_cache
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index () {
    if (springSecurityService.currentUser) {
      if ( grailsApplication.config.newDash ) {
        forward (action: "userdash", params: (params))
      }
      else {
        forward (action: "dashboard", params: (params))
      }
    }
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def userdash() {
    def result = [:]
    result
  }
  
  
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def calculate() {
    log.debug("Calculating stats...");

    // The defaults for these widgets.
    def result=[:].withDefault {[

        xkey:'month',
        hideHover: 'auto',
        resize: true

      ].withDefault {[]}}

    def widgets = [
      'Titles' : [
        'type'      : 'line',
        'datasets'  : [
          [
            'query'     :
            'select count(p.id) as titlesall, '+
            'sum(case when p.dateCreated > :startdate then 1 else 0 end) as titlesnew ' +
            'from TitleInstance as p where p.dateCreated < :enddate',
            'ykeys'      : ['titlesall', 'titlesnew'],
            'labels'     : ['Total Titles','New Titles'],
            'lineColors' : ['#0000FF', '#FF0000']
          ],
        ]
      ],
      'Organizations' : [
        'type'      : 'line',
        'datasets'  : [
          [
            'query'     : 'select count(p.id) as orgsnew from Org as p where p.dateCreated > :startdate and p.dateCreated < :enddate',
            'ykeys'      : 'orgsnew',
            'labels'     : 'New Organizations',
            'lineColors' : '#FF0000'
          ],[
            'query'     : 'select count(p.id) as orgsall from Org as p where p.dateCreated < :enddate',
            'ykeys'      : 'orgsall',
            'labels'     : 'Total Organizations',
            'lineColors' : '#0000FF'
          ]
        ]
      ],
      'Packages' : [
        'type'      : 'line',
        'datasets'  : [
          [
            'query'     : 'select count(p.id) as pkgsnew from Package as p where p.dateCreated > :startdate and p.dateCreated < :enddate',
            'ykeys'      : 'pkgsnew',
            'labels'     : 'New Packages',
            'lineColors' : '#FF0000'
          ],[
            'query'     : 'select count(p.id) as pkgsall from Package as p where p.dateCreated < :enddate',
            'ykeys'      : 'pkgsall',
            'labels'     : 'Total Packages',
            'lineColors' : '#0000FF'
          ]
        ]
      ],
    ]

    // The calendar for the queries.
    Calendar calendar = Calendar.getInstance()
    int start_year = calendar.get(Calendar.YEAR) - 1
    int start_month = calendar.get(Calendar.MONTH) + 1
    if ( start_month == 12 ) {
      start_month = 0
      start_year++
    }

    // For each month in the past 12 months, execute each stat query defined in the month_queries array and stuff
    // the count for that stat in the matrix (Rows = stats, cols = months)
    widgets.each {widget_name, widget_data ->

      log.debug("Processing counts for ${widget_name}");

      // Widget data.
      def wData = [:].withDefault {
        [:]
      }

      // The datasets.
      def ds = widget_data.remove("datasets")

      result."${widget_name}" << (widget_data + [
        'element' : GrailsNameUtils.getPropertyName(widget_name),
      ])

      ds.each { Map d ->

        String q = d.remove("query")

        // Clear the calendar.
        calendar.clear();
        calendar.set(Calendar.MONTH, start_month);
        calendar.set(Calendar.YEAR, start_year);

        // Merge in the singles into their plural container.
        d.each { String pName, pVal ->
          result."${widget_name}"."${pName}" += pVal
        }

        for ( int i=0; i<12; i++ ) {

          log.debug("Period ${i}");

          def period_start_date = calendar.getTime()
          calendar.add(Calendar.MONTH, 1)
          def period_end_date = calendar.getTime()

          def query_params = [:]
          if ( q.contains(':startdate') ) {
            query_params.startdate = period_start_date
          }
          if ( q.contains(':enddate') ) {
            query_params.enddate = period_end_date
          }

          // log.debug("Finding ${widget_name} from ${period_start_date} to ${period_end_date}")

          // Execute the query directly with Hibernate so we can just get a list of Maps.
          List<Map> qres = sessionFactory.getCurrentSession().createQuery(q).with {
            setProperties(query_params)
            setReadOnly(true)
            setResultTransformer (AliasToEntityMapResultTransformer.INSTANCE)
            list()
          }

          // X-axis key and val.
          String xkey = result."${widget_name}"."xkey"
          def xVal = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}"

          // Construct an entry for this xValue
          def entry = [
            "${xkey}" : "${xVal}"
          ]

          // Might be multiple Y vals per row.
          ([] + d."ykeys").each {String ykey ->
            entry."${ykey}" = qres[0]."${ykey}"
          }

          // Add to the data.
          wData."${xVal}".putAll(entry)
        }
      }

      log.debug("Completed Processing counts for ${widget_name}");

      // Add the results.
      result."${widget_name}"."data" = wData.values()
    }

    // log.debug("${result}")
    ["widgets" : result]
  }


  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def dash() {
    def result=[:]

    User user = springSecurityService.currentUser
    def active_status = RefdataCategory.lookupOrCreate('Activity.Status', 'Active')
    def needs_review_status = RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Needs Review')

    result.openActivities = Activity.findAllByOwnerAndStatus(user,active_status)
    result.recentlyClosedActivities = Activity.findAllByOwnerAndStatusNotEqual(user,active_status,[max: 10, sort: "lastUpdated", order: "desc"])
    result.recentlyViewed = History.findAllByOwner(user,[max: 20, sort: "activityDate", order: "desc"])
    result.recentReviewRequests = ReviewRequest.findAllByRaisedByAndStatus(user,needs_review_status,[max: 10, sort: "dateCreated", order: "desc"])

    result
  }

  def showRules() {
    def result=[:]
    result.rules = grailsApplication.config.validationRules
    result
  }

  def about() {
  }

  def releaseNotes() {
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def profile() {
    def result = [:]
    User user = springSecurityService.currentUser
    result.user = user
    result
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def preferences() {
    def result = [:]
    User user = springSecurityService.currentUser
    result.user = user
    result
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def changePass() {
    if ( params.newpass == params.repeatpass ) {
      User user = springSecurityService.currentUser
      if ( passwordEncoder.isPasswordValid(user.password, params.origpass, null) ) {
        user.password = params.newpass
        user.save();
        flash.message = "Password Changed!"
      }
      else {
        flash.message = "Existing password does not match: not changing"
      }
    }
    else {
      flash.message = "New password does not match repeat password: not changing"
    }
    redirect(action:'profile')
  }


  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def sendAlerts() {
    flash.message ="Alerts email sent, please check your email shortly";
    User user = springSecurityService.currentUser
    userAlertingService.sendAlertingEmail(user);
    redirect(action:'profile')
  }
}
