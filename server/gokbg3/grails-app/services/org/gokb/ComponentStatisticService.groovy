package org.gokb

import org.gokb.cred.*

import com.k_int.ClassUtils
import grails.gorm.transactions.Transactional

@Transactional
class ComponentStatisticService {

  def executorService

  public static components = ["TitleInstance", "Org", "Package"]
  public static boolean running = false;

  def synchronized updateCompStats(int months = 12, int offset = 0, boolean force_update = false) {
    log.debug("updateCompStats");

    if ( running == false ) {
      running = true;
      ensureStats(months, offset, force_update)
      log.debug("updateCompStats returning");
      return new Date();
    }
    else {
      log.debug("Stats Update already running");
    }
  }

  def ensureStats(int months = 12, int offset = 0, boolean force_update = false) {

    log.debug("Ensuring stats for ${months} months with offset ${offset}.")
    Calendar calendar = Calendar.getInstance()
    RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')

    months = ( months > 3 ? months : 3 )
    offset = ( offset > 0 ? offset : 0 )

    int to_substract = months + offset -1

    calendar.add(Calendar.MONTH, -to_substract)

    int start_year = calendar.get(Calendar.YEAR)
    int start_month = calendar.get(Calendar.MONTH)

    components.each { c ->

      log.debug("ensureStats - ${c}")
      calendar.clear();
      calendar.set(Calendar.MONTH, start_month);
      calendar.set(Calendar.YEAR, start_year);

      def new_stats_created = 0
      def stats_updated = 0

      for ( int i; i <= to_substract; i++ ) {

        def period_start_date = calendar.getTime()
        def period_month = calendar.get(Calendar.MONTH)
        def period_year = calendar.get(Calendar.YEAR)
        calendar.add(Calendar.MONTH, 1)
        def period_end_date = calendar.getTime()

        def month_year_string = "${period_month}-${period_year}"

        def existing_stats = ComponentStatistic.executeQuery("from ComponentStatistic where componentType = ? and month = ? and year = ?", [c, period_month, period_year], [readOnly: true])

        if ( !existing_stats || existing_stats.size() == 0 || ( offset == 0 && i == to_substract ) || force_update ) {

          def query_params = [enddate: period_end_date,
                              forbiddenStatus : RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED)]
          def fetch_all = "select count(o.id) from ${c} as o where dateCreated < :enddate and status != :forbiddenStatus"
          def stats_total_count = KBComponent.executeQuery(fetch_all.toString(), query_params, [readOnly: true])[0]

          query_params.startdate = period_start_date
          def fetch_new = "select count(o.id) from ${c} as o where dateCreated > :startdate and dateCreated < :enddate and status != :forbiddenStatus"
          def stats_new_count = KBComponent.executeQuery(fetch_new.toString(), query_params, [readOnly: true])[0]

          if (existing_stats && existing_stats.size() > 0 && ( ( offset == 0 && i == to_substract ) || force_update ) ) {
            if( existing_stats.size() == 1 ) {
              existing_stats[0].numTotal = stats_total_count
              existing_stats[0].numNew = stats_new_count
              existing_stats[0].save(failOnError: true, flush:true)

              stats_updated++
            }
            else{
              log.error("Statline ${month_year_string} for componentType ${c} has ${existing_stats.size()} entries!")
            }
          }
          else {
            def new_statsline = new ComponentStatistic(componentType: c, month: period_month, year: period_year, numTotal: stats_total_count, numNew: stats_new_count).save(failOnError: true, flush:true)

            new_stats_created++
          }
        }
      }
      log.debug("Finished stats generation for ${c}")
      log.debug("${c} - Created ${new_stats_created} new stat lines, updated ${stats_updated}")
    }
    running = false
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
  }
}
