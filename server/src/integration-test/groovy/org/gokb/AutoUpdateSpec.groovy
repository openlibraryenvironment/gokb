package org.gokb

import com.k_int.ConcurrencyManagerService
import grails.core.GrailsApplication
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import org.gokb.cred.RefdataCategory
import org.gokb.cred.Source
import org.gokb.cred.WebHookEndpoint
import spock.lang.Specification

import java.time.LocalDate
import java.time.ZoneId


@Integration
@Rollback
class AutoUpdateSpec extends Specification{

  GrailsApplication grailsApplication
  ConcurrencyManagerService concurrencyManagerService
  PackageSourceUpdateService packageSourceUpdateService

  def setup() {

    LocalDate now = LocalDate.now()
    Date lastFound = Date.from(LocalDate.parse("2026-04-12").atStartOfDay(ZoneId.systemDefault()).toInstant())
    Date lastFoundBeforeAllTimeIntervals = Date.from(LocalDate.parse("2024-01-05").atStartOfDay(ZoneId.systemDefault()).toInstant())
    //lastFound = null
    def freqMonthly = RefdataCategory.lookup('Source.Frequency', 'Monthly').save(flush: true)
    def freqWeekly = RefdataCategory.lookup('Source.Frequency', 'Weekly').save(flush: true)
    def freqQuarterly = RefdataCategory.lookup('Source.Frequency', 'Quarterly').save(flush: true)
    def freqYearly = RefdataCategory.lookup('Source.Frequency', 'Yearly').save(flush: true)

    String twoWeeksBefore = LocalDate.now().minusWeeks(2).toString()
    String urlTwoWeeksBefore = "https://www.abc.de/kbart-" + twoWeeksBefore + ".txt"

    Source.findByName("source1") ?: new Source(name: "source1", url: "https://www.abc.de/kbart.txt", dateLastFoundUpdateFile: lastFound, frequency: freqMonthly).save(flush: true)
    Source.findByName("source2") ?: new Source(name: "source2", url: "https://www.abc.de/kbart-2025-12-24.txt", dateLastFoundUpdateFile: null, frequency: freqMonthly).save(flush: true)
    Source.findByName("source3") ?: new Source(name: "source3", url: urlTwoWeeksBefore, dateLastFoundUpdateFile: lastFound, frequency: freqMonthly).save(flush: true)
    Source.findByName("source4") ?: new Source(name: "source4", url: "https://www.abc.de/kbart-2026-02-03.txt", dateLastFoundUpdateFile: lastFoundBeforeAllTimeIntervals, frequency: freqMonthly).save(flush: true)
    Source.findByName("source5") ?: new Source(name: "source5", url: "https://www.abc.de/kbart-{YYYY-MM-DD}.txt", dateLastFoundUpdateFile: null, frequency: freqYearly).save(flush: true)
    Source.findByName("source6") ?: new Source(name: "source6", url: "https://www.abc.de/kbart-{YYYY-MM-DD}.txt", dateLastFoundUpdateFile: null, frequency: freqQuarterly).save(flush: true)

  }

  def cleanup() {
    Source.findByName("source1")?.expunge()
    Source.findByName("source2")?.expunge()
    Source.findByName("source3")?.expunge()
    Source.findByName("source4")?.expunge()
    Source.findByName("source5")?.expunge()
    Source.findByName("source6")?.expunge()
  }


  void "Test AutoUpdate :: URL without date"() {

    Source source = Source.findByName("source1")
    String givenUrl = source.url

    List res = packageSourceUpdateService.findUrlsToCall(givenUrl, source, false)

    expect:
    res.size() == 1
    res.get(0).toString().equals(givenUrl)

  }

  void "Test AutoUpdate :: Fixed Date out of interval, no lastFoundFile"() {

    Source source = Source.findByName("source2")
    String givenUrl = source.url

    List res = packageSourceUpdateService.findUrlsToCall(givenUrl, source, false)
    Set<URL> uniques = new HashSet<URL>(res)

    expect:
    res.size() > 0
    res.get(0).toString().equals(givenUrl)

    //check that there are no dupes in res
    uniques.size() == res.size()

  }

  void "Test AutoUpdate :: Fixed Date in interval, no lastFoundFile"() {

    Source source = Source.findByName("source3")
    String givenUrl = source.url

    List res = packageSourceUpdateService.findUrlsToCall(givenUrl, source, false)

    expect:
    res.size() > 0
    res.get(0).toString().equals(givenUrl)

  }

  void "Test AutoUpdate :: Fixed Date out of interval, lastFoundFile out of interval"() {

    Source source = Source.findByName("source4")
    String givenUrl = source.url

    List res = packageSourceUpdateService.findUrlsToCall(givenUrl, source, false)

    expect:
    res.size() > 0
    res.get(0).toString().equals(givenUrl)

  }

  void "Test AutoUpdate :: check all dates of a year"() {

    Source source = Source.findByName("source5")
    String givenUrl = source.url

    LocalDate now = LocalDate.now()
    LocalDate oneYearBefore = now.minusYears(1)
    List<URL> urls = new ArrayList<URL>()

    while(oneYearBefore.isBefore(now)){
      String toAdd = "https://www.abc.de/kbart-" + oneYearBefore.toString() + ".txt"
      urls.add(new URL(toAdd))
      oneYearBefore = oneYearBefore.plusDays(1)
    }
    boolean contains = true

    List res = packageSourceUpdateService.findUrlsToCall(givenUrl, source, false)

    expect:
    res.size() - urls.size() <= 1

    res.containsAll(urls)

  }


  void "Test AutoUpdate :: check all dates of a Quarter year"() {

    Source source = Source.findByName("source6")
    String givenUrl = source.url

    LocalDate now = LocalDate.now()
    LocalDate oneQuarterBefore = now.minusMonths(3)
    List<URL> urls = new ArrayList<URL>()

    while(oneQuarterBefore.isBefore(now)){
      String toAdd = "https://www.abc.de/kbart-" + oneQuarterBefore.toString() + ".txt"
      urls.add(new URL(toAdd))
      oneQuarterBefore = oneQuarterBefore.plusDays(1)
    }
    boolean contains = true

    List res = packageSourceUpdateService.findUrlsToCall(givenUrl, source, false)

    expect:
    res.size() - urls.size() <= 1

    res.containsAll(urls)

  }



}
