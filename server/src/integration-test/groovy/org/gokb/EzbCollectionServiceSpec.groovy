package org.gokb


import grails.testing.mixin.integration.Integration

import org.gokb.cred.*
import org.hibernate.SessionFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.Rollback
import org.springframework.transaction.annotation.Transactional

import spock.lang.Specification

@Integration
@Transactional
@Rollback
class EzbCollectionServiceSpec extends Specification {

  @Autowired
  SessionFactory sessionFactory

  @Autowired
  EzbCollectionService ezbCollectionService

  def setup() {
    Org.withNewSession {
      CuratoryGroup testCurator = CuratoryGroup.findByName("EzbTestCurator") ?: new CuratoryGroup(name: "EzbTestCurator").save(flush: true, failOnError: true)
      Org testProvider = Org.findByName("EzbTestProvider") ?: new Org(name: "EzbTestProvider").save(flush: true, failOnError: true)
      Platform testPlatform = Platform.findByName("EzbTestPlatform") ?: new Platform(name: "EzbTestPlatform", provider: testProvider).save(flush: true, failOnError: true)
      Identifier testId = Identifier.findByValue("EZB-TEST-12345") ?: new Identifier(value: 'EZB-TEST-12345', namespace: IdentifierNamespace.findByValue('ezb-collection-id')).save(flush: true, failOnError: true)
      Source testSrc = Source.findByName("EZB-TEST-12345: EzbTestPkg") ?: new Source(name: "EZB-TEST-12345: EzbTestPkg", url: "https://ezb.uni-regensburg.de/services/titlelist.phtml?collection_id=EZB-NALFO-01634&title_split=1").save(flush: true, failOnError: true)
      Package testPackage = Package.findByName("EzbTestPkg") ?: new Package(name: "EzbTestPkg", source: testSrc, provider: testProvider, nominalPlatform: testPlatform).save(flush: true, failOnError: true)
      testPackage.addIdentifier(testId)
      testPackage.addToCuratoryGroups(testCurator)
      testPackage.save(flush: true, failOnError: true)
    }
  }

  def cleanup() {
    TitleInstancePackagePlatform.executeQuery("select id from TitleInstancePackagePlatform").each {
      TitleInstancePackagePlatform.findById(it).expunge()
    }
    TitleInstancePlatform.executeQuery("select id from TitleInstancePlatform").each {
      TitleInstancePlatform.findById(it).expunge()
    }
    TitleInstance.executeQuery("select id from TitleInstance").each {
      TitleInstance.findById(it).expunge()
    }
    Package.executeQuery("select id from Package").each {
      Package.findById(it).expunge()
    }
    Platform.executeQuery("select id from Platform").each {
      Platform.findById(it).expunge()
    }
    Org.executeQuery("select id from Org").each {
      Org.findById(it).expunge()
    }
    Source.findByName("EZB-TEST-12345: EzbTestPkg")?.refresh()?.expunge()
  }

  void "test create new package info"() {
    given:
    Org testProvider = Org.findByName("EzbTestProvider")
    Platform testPlatform = Platform.findByName("EzbTestPlatform")

    Map item = [
      ezb_collection_id: "EZB-NALIW-00492",
      ezb_collection_shortname: "Sage_Journals_HSS_NK",
      ezb_collection_name: "Sage Journals Online / Humanities and Social Sciences (HSS)",
      ezb_collection_source: "EZB",
      ezb_collection_curatory_group: "ezb_curatory_group",
      ezb_owner: "TestManager Bib",
      ezb_collection_titlelist: "https://ezb.ur.de/services/titlelist.phtml?collection_id=EZB-NALIW-00492&title_split=1",
      ezb_package_id: "NALIW",
      ezb_package_name: "Deutsches Nationalkonsortium: Sage Journals Online Publish & Read",
      ezb_package_type: "6",
      ezb_package_type_name: "Nationalkonsortium",
      ezb_collection_anchor: "sage_hss",
      zdb_product_id: "ZDB-1-SAGH",
      national_licenses_cms_id: "",
      collection_manager_name: "TestManager",
      collection_manager_email: "testexport@ggggg.de",
      ezb_collection_released_date: "2018-02-06 10:03:42",
      ezb_collection_metadata_changed_date: "2018-02-06 10:03:42",
      ezb_collection_provider: testProvider.uuid,
      ezb_collection_platform: testPlatform.uuid
    ]

    Map type_results = [
      total: 0,
      skipped: 0,
      noProvider: 0,
      noPlatform: 0,
      noCurator: 0,
      unchanged: 0,
      updated: 0,
      created: 0,
      errors: 0,
      success: 0,
      skippedList: [],
      validationErrors: [:],
      matchingFailed: [],
      matchedOtherCg: [],
      sourceError: []
    ]

    when:
    sleep(300)
    Map result = ezbCollectionService.processPackageInfo(item, type_results)

    then:
    result.skipped == false
    result.sourceResult != false
    result.pkgCreated != null
    result.curator_id != null
    result.pkgInfo?.name == "EZB-NALIW-00492: Sage Journals Online / Humanities and Social Sciences (HSS): Nationalkonsortium"
    type_results.total == 1
    type_results.created == 1

    Package new_ezb_pkg = Package.findByName("EZB-NALIW-00492: Sage Journals Online / Humanities and Social Sciences (HSS): Nationalkonsortium")
    new_ezb_pkg?.ids?.size() == 2
    new_ezb_pkg.provider == testProvider
    new_ezb_pkg.nominalPlatform == testPlatform
  }

  void "test import ezb package content"() {

    given:
    Org testProvider = Org.findByName("EzbTestProvider")
    Platform testPlatform = Platform.findByName("EzbTestPlatform")

    Map item = [
      ezb_collection_id: "EZB-WISO-01791",
      ezb_collection_shortname: "WISO_Pflege_AP",
      ezb_collection_name: "WISO Pflege",
      ezb_collection_source: "EZB",
      ezb_collection_curatory_group: "ezb_curatory_group",
      ezb_owner: "",
      ezb_collection_titlelist: "https://ezb.ur.de/services/titlelist.phtml?collection_id=EZB-WISO-01791&title_split=1",
      ezb_package_id: "WISO",
      ezb_package_name: "WISO Aggregator-Titel",
      ezb_package_type: "4",
      ezb_package_type_name: "Aggregatorpaket",
      ezb_collection_anchor: "wisopflege",
      zdb_product_id: "",
      national_licenses_cms_id: "",
      collection_manager_name: "UB Regensburg",
      collection_manager_email: "testexport@ggggg.de",
      ezb_collection_released_date: "2021-11-04 08:29:34",
      ezb_collection_metadata_changed_date: "2021-11-04 08:29:34",
      ezb_collection_provider: testProvider.uuid,
      ezb_collection_platform: testPlatform.uuid
    ]

    Map type_results = [
      total: 0,
      skipped: 0,
      noProvider: 0,
      noPlatform: 0,
      noCurator: 0,
      unchanged: 0,
      updated: 0,
      created: 0,
      errors: 0,
      success: 0,
      skippedList: [],
      validationErrors: [:],
      matchingFailed: [],
      matchedOtherCg: [],
      sourceError: []
    ]

    when:
    ezbCollectionService.handleEzbCollectionItem(item, type_results)

    then:
    type_results.total == 1
    type_results.created == 1
  }

  void "test check existing package source URL"() {
    given:
    Package testIdPkg = Package.findByName("EzbTestPkg")

    when:
    boolean result = ezbCollectionService.hasEzbUrl(testIdPkg)

    then:
    result == true
  }

  void "test match existing package by id with other curator"() {
    given:
    Org testProvider = Org.findByName("EzbTestProvider")
    Platform testPlatform = Platform.findByName("EzbTestPlatform")

    Map item = [
      ezb_collection_id: "EZB-TEST-12345",
      ezb_collection_shortname: "EzbTestPkg",
      ezb_collection_name: "EzbTestPkg",
      ezb_collection_source: "EZB",
      ezb_collection_curatory_group: "ezb_curatory_group",
      ezb_owner: "",
      ezb_collection_titlelist: "https://ezb.ur.de/services/titlelist.phtml?collection_id=EZB-NALFO-01634&title_split=1",
      ezb_package_id: "TEST",
      ezb_package_name: "EzbTestPkg",
      ezb_package_type: "4",
      ezb_package_type_name: "Aggregatorpaket",
      ezb_collection_anchor: "ezbtest",
      zdb_product_id: "",
      national_licenses_cms_id: "",
      collection_manager_name: "GOKB",
      collection_manager_email: "testexport@ggggg.de",
      ezb_collection_released_date: "2021-11-04 08:29:34",
      ezb_collection_metadata_changed_date: "2021-11-04 08:29:34",
      ezb_collection_provider: testProvider.uuid,
      ezb_collection_platform: testPlatform.uuid
    ]

    Map type_results = [
      total: 0,
      skipped: 0,
      noProvider: 0,
      noPlatform: 0,
      noCurator: 0,
      unchanged: 0,
      updated: 0,
      created: 0,
      errors: 0,
      success: 0,
      skippedList: [],
      validationErrors: [:],
      matchingFailed: [],
      matchedOtherCg: [],
      sourceError: []
    ]

    when:
    Map result = ezbCollectionService.processPackageInfo(item, type_results)

    then:
    type_results.skipped == 0
    type_results.created == 0
  }
}
