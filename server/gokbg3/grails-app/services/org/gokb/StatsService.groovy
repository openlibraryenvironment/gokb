package org.gokb

import grails.gorm.transactions.Transactional

@Transactional
class StatsService {

  def statsCache = [:]

  // These queries are for summing month on month activity totals - EG # of titles added in 01/2014
  static def month_queries = [
      [ 'titlesCreated', 'select count(p.id) from TitleInstance as p where p.dateCreated > ? and p.dateCreated < ?', 'titleAdditionData' ],
      [ 'packagesCreated', 'select count(p.id) from Package as p where p.dateCreated > ? and p.dateCreated < ?', 'packageAdditionData' ],
      [ 'orgsCreated', 'select count(p.id) from Org as p where p.dateCreated > ? and p.dateCreated < ?', 'orgAdditionData' ],
  ]

  // These queries are for totals over time
  static def cumulative_total_queries = [
      [ 'titlesCreated', 'select count(p.id) from TitleInstance as p where p.dateCreated < ?', 'totalTitlesAdditionData' ],
      [ 'packagesCreated', 'select count(p.id) from Package as p where p.dateCreated < ?', 'totalPackagesAdditionData' ],
      [ 'orgsCreated', 'select count(p.id) from Org as p where p.dateCreated < ?', 'totalOrgsAdditionData' ],
  ]


  def getStats() {
    if ( statsCache == null ) {
      statsCache = generateStats();
    }
    statsCache
  }

  def generateStats() {

    def resuls = [:]

    Calendar calendar = Calendar.getInstance();
    int year = calendar.get(Calendar.YEAR) - 1;

    int month = calendar.get(Calendar.MONTH) + 1;  // Zero based, Jan = 0 - Add one so we get the full year and not the start of this month last year
                                                   // IE we want now to be a partial month....
    if ( month == 12 ) month = 0;

    result.colHeads1 = [['string', 'Year-Month'], ['number', 'Total']]

    // Set up arrays for each stat defined in the month_queries array
    month_queries.each {
      result[it[2]] = []
    }

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
        log.debug("Finding (MT) ${mc[0]} from ${period_start_date} to ${period_end_date} (${year}-${month})");
        result[mc[2]].add(["${year}-${month}",KBComponent.executeQuery(mc[1],[period_start_date, period_end_date])[0]])
      }

      cumulative_total_queries.each { ct ->
        log.debug("Finding (CT) ${ct[0]} from ${period_start_date} to ${period_end_date} (${year}-${month})");
        result[ct[2]].add(["${year}-${month}",KBComponent.executeQuery(ct[1],[period_end_date])[0]])
      }

      // cumulative_total_queries.each { ct ->
      //   result[mc[2]].add(["${year}-${month}",KBComponent.executeQuery(mc[1],[period_end_date])[0]])
      // }

      if ( month == 12 ) {
        year++
        month=0
      }
      else {
        month++
      }
    }

    log.debug(result)

    result
  }
}
