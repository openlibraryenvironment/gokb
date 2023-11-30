package org.gokb

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest

import org.gokb.cred.IdentifierNamespace

import spock.lang.Specification
import spock.lang.Shared

class ValidationServiceSpec extends Specification implements DataTest, ServiceUnitTest<ValidationService> {

  @Shared IdentifierNamespace issn
  @Shared IdentifierNamespace isbn
  @Shared IdentifierNamespace zdb
  @Shared IdentifierNamespace isil
  @Shared IdentifierNamespace doi

  def setup() {
    mockDomain IdentifierNamespace

    issn = new IdentifierNamespace(value: "issn")
    isbn = new IdentifierNamespace(value: "isbn")
    zdb = new IdentifierNamespace(value: "zdb")
    isil = new IdentifierNamespace(value: "isil", pattern: "^(?=[0-9A-Z-]{4,16}\$)[A-Z]{1,4}-[A-Z0-9]{1,11}(-[A-Z0-9]+)?\$")
    doi = new IdentifierNamespace(value: "doi")
  }

  void "test checkAccessType with exact match"() {
    expect:
      service.checkAccessType("F") == 'F'
  }

  void "test checkAccessType with previously interpreted match"() {
    expect:
      service.checkAccessType("Free") == 'P'
  }

  void "test checkAccessType with invalid value"() {
    expect:
      service.checkAccessType("Test") == 'P'
  }

  void "test checkTitleString with exact match"() {
    expect:
      service.checkTitleString("Test Title") == "Test Title"
  }

  void "test checkTitleString with corrected match"() {
    expect:
      service.checkTitleString(" Test  @Sanitize : Title ") == "Test Sanitize: Title"
  }

  void "test checkTitleString with empty trim"() {
    expect:
      service.checkTitleString(" ") == null
  }

  void "test issn validation with valid value"() {
    expect:
      service.checkIdForNamespace("0020-0255", issn) == "0020-0255"
  }

  void "test issn validation with invalid value"() {
    expect:
      service.checkIdForNamespace("123-2332", issn) == null
  }

  void "test isbn validation with valid value"() {
    expect:
      service.checkIdForNamespace("978-3-16-148410-0", isbn) == "978-3-16-148410-0"
  }

  void "test isbn validation with invalid value"() {
    expect:
      service.checkIdForNamespace("978-3-16-148410-2", isbn) == null
  }

  void "test isbn validation with valid isbn10"() {
    expect:
      service.checkIdForNamespace("0-7817-8338-0", isbn) == "9780781783385"
  }

  void "test zdb validation with valid value"() {
    expect:
      service.checkIdForNamespace("1483109-0", zdb) == "1483109-0"
  }

  void "test zdb validation with invalid value"() {
    expect:
      service.checkIdForNamespace("1483109-4", zdb) == null
  }

  void "test custom id pattern validation with valid value"() {
    expect:
      service.checkIdForNamespace("ZDB-1-ESWX", isil) == "ZDB-1-ESWX"
  }

  void "test custom id pattern validation with invalid value"() {
    expect:
      service.checkIdForNamespace("TestIsil", isil) == null
  }

  void "test id validation without check"() {
    expect:
      service.checkIdForNamespace("TestDoi", doi) == "TestDoi"
  }

  void "test checkDates with valid form yyyy"() {
    expect:
      service.checkDate("2000") == "2000"
  }

  void "test checkDates with valid form yyyy-mm"() {
    expect:
      service.checkDate("2020-10") == "2020-10"
  }

  void "test checkDates with valid form yyyy-mm-dd"() {
    expect:
      service.checkDate("2020-10-31") == "2020-10-31"
  }

  void "test checkDates with valid form yyyy-mm-dd but wrong day value"() {
    expect:
      service.checkDate("2020-12-40") == null
  }

  void "test checkDates with invalid form"() {
    expect:
      service.checkDate("10 Aug 2020") == null
  }

  void "test reject long value in first_author column"() {
    given:
      String long_string = ""

      for (int i; i <= 260; i++) {
        long_string += "a"
      }

    expect:
      !service.hasValidLength(long_string, "first_author")
  }
}
