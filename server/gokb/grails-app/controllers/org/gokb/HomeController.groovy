package org.gokb

import grails.plugins.springsecurity.Secured
import org.gokb.cred.*


class HomeController {

  def grailsApplication
  def springSecurityService
  
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() { 
    redirect(controller:'search',action:'index',params:[qbe:'g:components'])
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
  
  def dashboard() {
    
    def result=[:]

    def month_queries = [
      [ 'titlesCreated', 'select count(p.id) from TitleInstance as p where p.dateCreated > ? and p.dateCreated < ?', 'titleAdditionData' ],
      [ 'packagesCreated', 'select count(p.id) from Package as p where p.dateCreated > ? and p.dateCreated < ?', 'packageAdditionData' ],
      [ 'orgsCreated', 'select count(p.id) from Org as p where p.dateCreated > ? and p.dateCreated < ?', 'orgAdditionData' ],
    ]

    Calendar calendar = Calendar.getInstance();
    int year = calendar.get(Calendar.YEAR) - 1;
    int month = calendar.get(Calendar.MONTH);

    result.colHeads1 = [['string', 'Year-Month'], ['number', 'Count']]
    result.titleAdditionData=[]
    result.packageAdditionData=[]
    result.orgAdditionData=[]

    // For each month in the past 12 months, execute each stat query defined in the month_queries array and stuff
    // the count for that stat in the matrix (Rows = stats, cols = months)
    for ( int i=0; i<12; i++ ) {

      calendar.clear();
      calendar.set(Calendar.MONTH, month);
      calendar.set(Calendar.YEAR, year);
      def period_start_date =  calendar.getTime();

      calendar.clear();
      calendar.set(Calendar.MONTH, month+1);
      calendar.set(Calendar.YEAR, year);
      def period_end_date = calendar.getTime();

      def month_data = []
      month_data.add("${year}-${month}");

      month_queries.each { mc ->
        log.debug("Finding ${mc[0]} from ${period_start_date} to ${period_end_date}");
        result[mc[2]].add(["${year}-${month}",KBComponent.executeQuery(mc[1],[period_start_date, period_end_date])[0]])
      }

      if ( month == 12 ) {
        year++
        month=1
      }
      else {
        month++
      }
    }

    log.debug(result)

    result
    
  }
}
