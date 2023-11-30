package org.gokb

import com.k_int.ConcurrencyManagerService

import gokbg3.DateFormatService

import grails.testing.mixin.integration.Integration
import grails.testing.services.ServiceUnitTest

import java.time.LocalDateTime

import org.gokb.*
import org.gokb.TitleLookupService
import org.gokb.cred.BookInstance
import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.JournalInstance
import org.gokb.cred.Org
import org.gokb.cred.RefdataCategory
import org.gokb.cred.ReviewRequest
import org.hibernate.SessionFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.Rollback
import org.springframework.transaction.annotation.Transactional

import spock.lang.Specification

@Integration
@Transactional
@Rollback
class TitleLookupServiceSpec extends Specification {

  @Autowired
  TitleLookupService titleLookupService

  JournalInstance journal
  BookInstance book

  def setup() {
    IdentifierNamespace eissn_ns = IdentifierNamespace.findByValue('eissn')
    IdentifierNamespace issn_ns = IdentifierNamespace.findByValue('issn')
    IdentifierNamespace zdb_ns = IdentifierNamespace.findByValue('zdb')
    IdentifierNamespace pisbn_ns = IdentifierNamespace.findByValue('pisbn')

    journal = JournalInstance.findByName("Lookup serviceTest Title!") ?: new JournalInstance(name: "Lookup serviceTest Title!").save(flush: true)
    Identifier issn = Identifier.findByNamespaceAndValue(issn_ns, "2210-8440") ?: new Identifier(value: "2210-8440", namespace: issn_ns).save(flush: true)
    Identifier eissn = Identifier.findByNamespaceAndValue(issn_ns, "1532-2033") ?: new Identifier(value: "1532-2033", namespace: eissn_ns).save(flush: true)
    Identifier zdbId = Identifier.findByNamespaceAndValue(issn_ns, "2594355-8") ?: new Identifier(value: "2594355-8", namespace: zdb_ns).save(flush: true)

    journal.ids.addAll([issn, eissn, zdbId])
    journal.save(flush: true)

    book = BookInstance.findByName("Lookup serviceTest Book!") ?: new BookInstance(name: "Lookup serviceTest Book!").save(flush: true)
    Identifier pisbn = Identifier.findByNamespaceAndNormname(pisbn_ns, "9783631733158") ?: new Identifier(value: "9783631733158", namespace: pisbn_ns).save(flush: true)
    book.ids << pisbn
    book.save(flush: true)
  }

  def cleanup() {
    JournalInstance.findByName("Lookup serviceTest Title!")?.expunge()
    BookInstance.findByName("Lookup serviceTest Book!")?.expunge()
  }

  void "Test find to match title via class one ids"() {
    given:
    def ids = [
      [value: "2210-8440", type: "issn"],
      [value: "1532-2033", type: "eissn"],
      [value: "2594355-8", type: "zdb"]
    ]

    when:
    def result = titleLookupService.find("Lookup serviceTest Title!", null, ids, 'org.gokb.cred.JournalInstance')

    then:
    result.to_create == false
    result.matches.size() == 1
    result.matches[0].object.name == "Lookup serviceTest Title!"
    result.matches[0].conflicts.size() == 0
  }

  void "Test find to match title via class one ids with conflicts"() {
    given:
    def ids = [
      [value: "0890-6955", type: "issn"],
      [value: "1532-2033", type: "eissn"],
      [value: "2594355-8", type: "zdb"]
    ]

    when:
    def result = titleLookupService.find("Lookup serviceTest Title!", null, ids, 'org.gokb.cred.JournalInstance')

    then:
    result.to_create == false
    result.matches.size() == 1
    result.matches[0].object.name == "Lookup serviceTest Title!"
    result.matches[0].conflicts.size() == 1
  }

  void "Test find to fail title matching due to conflicting name & id"() {
    given:
    def ids = [
      [value: "0890-6955", type: "issn"],
      [value: "1532-2033", type: "eissn"],
      [value: "2594355-8", type: "zdb"]
    ]

    when:
    def result = titleLookupService.find("Lookup serviceTest Other!", null, ids, 'org.gokb.cred.JournalInstance')

    then:
    result.to_create == true
    result.matches.size() == 1
    result.matches[0].object.name == "Lookup serviceTest Title!"
    result.matches[0].conflicts.size() == 2
  }

  void "Test find to match title via secondary id"() {
    given:
    def ids = [
      [value: "9783631733158", type: "pisbn"]
    ]

    when:
    def result = titleLookupService.find("Lookup serviceTest Other!", null, ids, 'org.gokb.cred.BookInstance')

    then:
    result.to_create == false
    result.matches.size() == 1
    result.matches[0].object.name == "Lookup serviceTest Book!"
    result.matches[0].conflicts.size() == 0
  }
}
