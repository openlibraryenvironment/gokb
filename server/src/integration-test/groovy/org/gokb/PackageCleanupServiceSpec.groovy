package org.gokb

import grails.testing.mixin.integration.Integration
import org.gokb.PackageCleanupService
import org.gokb.cred.*
import org.hibernate.SessionFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.Rollback
import org.springframework.transaction.annotation.Transactional

import spock.lang.Specification

@Integration
@Transactional
@Rollback
class PackageCleanupServiceSpec extends Specification {

  @Autowired
  PackageCleanupService packageCleanupService

  def setup() {

  }

  def cleanup() {

  }

  void "test year range generation for startYear only"() {
    given:
    Package pkg = new Package(name: "PackageCleanupTest (2020) Test", contentType: RefdataCategory.lookup("Package.ContentType", "Book")).save(flush: true, failOnError: true)
    when:
    packageCleanupService.generateYearInfoFromNames()
    then:
    pkg.refresh().startYear == 2020
  }

  void "test year range generation with full start and end"() {
    given:
    Package pkg = new Package(name: "PackageCleanupTest (2020–2024) Test", contentType: RefdataCategory.lookup("Package.ContentType", "Book")).save(flush: true, failOnError: true)
    when:
    packageCleanupService.generateYearInfoFromNames()
    then:
    pkg.refresh().startYear == 2020
    pkg.refresh().endYear == 2024
  }

  void "test year range generation with full start and shortened end"() {
    given:
    Package pkg = new Package(name: "PackageCleanupTest 2020-24", contentType: RefdataCategory.lookup("Package.ContentType", "Book")).save(flush: true, failOnError: true)
    when:
    packageCleanupService.generateYearInfoFromNames()
    then:
    pkg.refresh().startYear == 2020
    pkg.refresh().endYear == 2024
  }

  void "test year range generation with end year without start preposed with '<'"() {
    given:
    Package pkg = new Package(name: "PackageCleanupTest (<1990) Test", contentType: RefdataCategory.lookup("Package.ContentType", "Book")).save(flush: true, failOnError: true)
    when:
    packageCleanupService.generateYearInfoFromNames()
    then:
    pkg.refresh().startYear == 1800
    pkg.refresh().endYear == 1989
  }

  void "test year range generation with end year without start preposed with 'before'"() {
    given:
    Package pkg = new Package(name: "PackageCleanupTest before 1990 Test", contentType: RefdataCategory.lookup("Package.ContentType", "Book")).save(flush: true, failOnError: true)
    when:
    packageCleanupService.generateYearInfoFromNames()
    then:
    pkg.refresh().startYear == 1800
    pkg.refresh().endYear == 1989
  }

  void "test year range generation with month/year combo"() {
    given:
    Package pkg = new Package(name: "PackageCleanupTest (05/2020 - 09/2024) Test", contentType: RefdataCategory.lookup("Package.ContentType", "Book")).save(flush: true, failOnError: true)
    when:
    packageCleanupService.generateYearInfoFromNames()
    then:
    pkg.refresh().startYear == 2020
    pkg.refresh().endYear == 2024
  }

  void "test year range generation with single year partial"() {
    given:
    Package pkg = new Package(name: "PackageCleanupTest 2024-1", contentType: RefdataCategory.lookup("Package.ContentType", "Book")).save(flush: true, failOnError: true)
    when:
    packageCleanupService.generateYearInfoFromNames()
    then:
    pkg.refresh().startYear == 2024
    pkg.refresh().endYear == 2024
  }
}
