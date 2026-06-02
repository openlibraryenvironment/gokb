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
    //lastFound = null
    def freq = RefdataCategory.lookup('Source.Frequency', 'Monthly').save(flush: true)
    Source.findByName("source1") ?: new Source(name: "source1", url: "https://www.abc.de/kbart-{YYYY-MM-DD}.txt", dateLastFoundUpdateFile: lastFound, frequency: freq).save(flush: true)

  }

  def cleanup() {
    Source.findByName("source1")?.expunge()
  }


  void "test1"() {

    Source source = Source.findByName("source1")
    String givenUrl = source.url

    List res = packageSourceUpdateService.findUrlsToCall(givenUrl, source)

    expect:
    res.size() > 0

  }


}
