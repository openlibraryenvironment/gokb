package org.gokb

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification
import spock.lang.Shared

class ValidationServiceSpec extends Specification implements ServiceUnitTest<ValidationService> {

   @Shared IdentifierNamespace issn
   @Shared IdentifierNamespace isbn
   @Shared IdentifierNamespace zdb
   @Shared IdentifierNamespace pattern

  def setupSpec() {
    mockDomain IdentifierNamespace
  }

  def setup() {
    issn = new IdentifierNamespace(value: "issn")
    isbn = new IdentifierNamespace(value: "isbn")
    zdb = new IdentifierNamespace(value: "zdb")
    isil = new IdentifierNamespace(value: "isil", pattern: "^(?=[0-9A-Z-]{4,16}\$)[A-Z]{1,4}-[A-Z0-9]{1,11}(-[A-Z0-9]+)?\$")
  }

  void "check access type"() {
    expect:
      service.checkAccessType("Paid") == 'P'
      service.checkAccessType("p") == 'P'
      service.checkAccessType("Free") == 'F'
      service.checkAccessType("Frei") == null
      service.checkAccessType("Test") == null
      service.checkAccessType(null) == null
  }

  void "check title strings"() {
    expect:
      service.checkTitleString("Test Title") == "Test Title"
      service.checkTitleString("Sanitize : Title") == "Sanitize: Title"
      service.checkTitleString(" Sanitize Title ") == "Sanitize Title"
      service.checkTitleString(" ") == null
      service.checkTitleString(null) === null
      service.checkTitleString("The @title") === 'The title'
  }

  void "check issn validation"() {
    expect:
      service.checkIdForNamespace("123-2332", issn) == null
      service.checkIdForNamespace("0020-0255", issn) == "0020-0255"
  }

  void "check isbn validation"() {
    expect:
      service.checkIdForNamespace("978-3-16-148410-0", isbn) == "978-3-16-148410-0"
      service.checkIdForNamespace("978-3-16-148410-2", isbn) == null
  }

  void "check zdb validation"() {
    expect:
      service.checkIdForNamespace("1483109-0", zdb) == "1483109-0"
      service.checkIdForNamespace("1483109-4", zdb) == null
  }

  void "check id pattern validation"() {
    expect:
      service.checkIdForNamespace("TestIsil", isil) == null
      service.checkIdForNamespace("ZDB-1-ESWX", isil) == "ZDB-1-ESWX"
  }

  void "check dates"() {
    expect:
      service.checkDate("2000") == "2000"
      service.checkDate("2020-10") == "2020-10"
      service.checkDate("2020-10-31") == "2020-10-31"
      service.checkDate("2000 AD") == null
      service.checkDate("2020-12-40") == null
      service.checkDate("01.01.2020") == null
      service.checkDate("10 Aug 2020") == null
  }
}
