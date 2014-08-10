package org.gokb

import org.gokb.cred.*;

class WelcomeController {

  def index() { 

    def result=[:]

    def month_queries = [
      [ 'titlesCreated', 'select count(p.id) from TitleInstance as p where p.dateCreated > ? and p.dateCreated < ?', 'titleAdditionData' ],
      [ 'packagesCreated', 'select count(p.id) from Package as p where p.dateCreated > ? and p.dateCreated < ?', 'packageAdditionData' ],
    ]

    Calendar calendar = Calendar.getInstance();
    int year = calendar.get(Calendar.YEAR) - 1;
    int month = calendar.get(Calendar.MONTH);

    result.colHeads1 = [['string', 'Year-Month'], ['number', 'Count']]
    result.titleAdditionData=[]
    result.packageAdditionData=[]

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
