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
    LocalDate lastFound = LocalDate.parse("2026-04-12")
    LocalDate lastFoundBeforeAllTimeIntervals = LocalDate.parse("2024-01-05")
    LocalDate lastFoundThreeWeeksBefore = now.minusWeeks(3)
    //lastFound = null
    def freqMonthly = RefdataCategory.lookup('Source.Frequency', 'Monthly').save(flush: true)
    def freqWeekly = RefdataCategory.lookup('Source.Frequency', 'Weekly').save(flush: true)
    def freqQuarterly = RefdataCategory.lookup('Source.Frequency', 'Quarterly').save(flush: true)
    def freqYearly = RefdataCategory.lookup('Source.Frequency', 'Yearly').save(flush: true)

    String twoWeeksBefore = LocalDate.now().minusWeeks(2).toString()
    String urlTwoWeeksBefore = "https://www.abc.de/kbart-" + twoWeeksBefore + ".txt"

    Source.findByName("source1") ?: new Source(name: "source1", url: "https://www.abc.de/kbart.txt", lastImportFileDate: lastFound, frequency: freqMonthly).save(flush: true)
    Source.findByName("source2") ?: new Source(name: "source2", url: "https://www.abc.de/kbart-2025-12-24.txt", lastImportFileDate: null, frequency: freqMonthly).save(flush: true)
    Source.findByName("source3") ?: new Source(name: "source3", url: urlTwoWeeksBefore, lastImportFileDate: lastFound, frequency: freqMonthly).save(flush: true)
    Source.findByName("source4") ?: new Source(name: "source4", url: "https://www.abc.de/kbart-2026-02-03.txt", lastImportFileDate: lastFoundBeforeAllTimeIntervals, frequency: freqMonthly).save(flush: true)
    Source.findByName("source5") ?: new Source(name: "source5", url: "https://www.abc.de/kbart-{YYYY-MM-DD}.txt", lastImportFileDate: null, frequency: freqYearly).save(flush: true)
    Source.findByName("source6") ?: new Source(name: "source6", url: "https://www.abc.de/kbart-{YYYY-MM-DD}.txt", lastImportFileDate: null, frequency: freqQuarterly).save(flush: true)
    Source.findByName("source7") ?: new Source(name: "source7", url: "https://www.abc.de/kbart-{YYYY-MM-DD}.txt", lastImportFileDate: lastFoundBeforeAllTimeIntervals, frequency: freqWeekly).save(flush: true)
    Source.findByName("source8") ?: new Source(name: "source8", url: "https://www.abc.de/kbart-{YYYY-MM-DD}.txt", lastImportFileDate: lastFoundThreeWeeksBefore, frequency: freqMonthly).save(flush: true)
    Source.findByName("source9") ?: new Source(name: "source9", url: "https://www.abc.de/kbart-{YYYY-MM-DD}.txt", lastImportFileDate: null, frequency: null).save(flush: true)
    Source.findByName("source10") ?: new Source(name: "source10", url: "https://www.abc.de/kbart-2026-04-01.txt", lastImportFileDate: null, frequency: null).save(flush: true)

  }

  def cleanup() {
    Source.findByName("source1")?.expunge()
    Source.findByName("source2")?.expunge()
    Source.findByName("source3")?.expunge()
    Source.findByName("source4")?.expunge()
    Source.findByName("source5")?.expunge()
    Source.findByName("source6")?.expunge()
    Source.findByName("source7")?.expunge()
    Source.findByName("source8")?.expunge()
    Source.findByName("source9")?.expunge()
    Source.findByName("source10")?.expunge()
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
    Set<URL> uniques = new HashSet<URL>(res)

    expect:
    res.size() > 0
    res.get(0).toString().equals(givenUrl)

    //check that there are no dupes in res - fixed date URL is called first
    uniques.size() == res.size()

  }

  void "Test AutoUpdate :: Fixed Date out of interval, lastFoundFile out of interval"() {

    Source source = Source.findByName("source4")
    String givenUrl = source.url

    List res = packageSourceUpdateService.findUrlsToCall(givenUrl, source, false)
    Set<URL> uniques = new HashSet<URL>(res)

    expect:
    res.size() > 0
    res.get(0).toString().equals(givenUrl)

    //check that there are no dupes in res - fixed date URL is called first
    uniques.size() == res.size()

  }

  void "Test AutoUpdate :: check all dates of a year"() {

    given: "no found file, no fix date in URL"
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

    List res = packageSourceUpdateService.findUrlsToCall(givenUrl, source, false)

    expect:
    res.size() - urls.size() <= 1

    res.containsAll(urls)

  }


  void "Test AutoUpdate :: check all dates of a Quarter year"() {

    given: "no found file, no fix date in URL"
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

    List res = packageSourceUpdateService.findUrlsToCall(givenUrl, source, false)

    expect:
    res.size() - urls.size() <= 1
    packageSourceUpdateService.extractDateFromUrl(res.get(0).toString()) == now
    res.containsAll(urls)

  }

  void "Test AutoUpdate :: Date Mask, lastFoundFile out of interval"() {

    given: "weekly Update"
    Source source = Source.findByName("source7")
    LocalDate oneWeekBefore = LocalDate.now().minusWeeks(1)
    LocalDate today = LocalDate.now()

    List res = packageSourceUpdateService.findUrlsToCall(source.url, source, false)
    Set<URL> uniques = new HashSet<URL>(res)
    // edge cases
    URL lowerBorder = new URL("https://www.abc.de/kbart-" + oneWeekBefore.toString() + ".txt")
    URL upperBorder = new URL("https://www.abc.de/kbart-" + today.toString() + ".txt")

    expect:
    res.size() > 0

    uniques.size() == res.size()
    res.contains(lowerBorder)
    res.contains(upperBorder)

  }

  void "Test AutoUpdate :: Date Mask, lastFoundFile inside interval"() {

    given: "Monthly Update, lastFoundFile 3 weeks before"
    Source source = Source.findByName("source8")
    LocalDate today = LocalDate.now()
    LocalDate oneMonthBefore = today.minusMonths(1)
    LocalDate threeWeeksBefore = today.minusWeeks(3)

    List res = packageSourceUpdateService.findUrlsToCall(source.url, source, false)
    Set<URL> uniques = new HashSet<URL>(res)

    // edge cases
    URL lowerIntervalBorder = new URL("https://www.abc.de/kbart-" + oneMonthBefore.toString() + ".txt")
    URL lowerBorder = new URL("https://www.abc.de/kbart-" + threeWeeksBefore.toString() + ".txt")
    URL upperBorder = new URL("https://www.abc.de/kbart-" + today.toString() + ".txt")

    expect:
    res.size() > 0
    // case february
    res.size() <= 31 - 7

    uniques.size() == res.size()
    !res.contains(lowerIntervalBorder)
    res.contains(lowerBorder)
    res.contains(upperBorder)

  }

  void "Test AutoUpdate :: Date Mask, no frequency"() {

    Source source = Source.findByName("source9")
    LocalDate today = LocalDate.now()

    List res = packageSourceUpdateService.findUrlsToCall(source.url, source, false)

    expect:
    res.size() == 1
    res.get(0).toString().equals("https://www.abc.de/kbart-" + today.toString() + ".txt")

  }

  void "Test AutoUpdate :: Fix Date, no frequency"() {

    Source source = Source.findByName("source10")
    LocalDate fixDate = packageSourceUpdateService.extractDateFromUrl(source.url)

    List res = packageSourceUpdateService.findUrlsToCall(source.url, source, false)

    expect:
    res.size() == 1
    res.get(0).toString().equals("https://www.abc.de/kbart-" + fixDate.toString() + ".txt")

  }


}
