package org.gokb


import grails.testing.mixin.integration.Integration
import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.Org
import org.gokb.cred.Platform
import org.gokb.cred.Package
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.Rollback
import spock.lang.Specification

@Integration
@Rollback
class EzbCollectionServiceSpec extends Specification {

  @Autowired
  EzbCollectionService ezbCollectionService

  def setup() {
    Org.withNewSession {
      Org testOrg = Org.findByName("EzbTestProvider") ?: new Org(name: "EzbTestProvider").save(flush: true)
      Platform testPlt = Platform.findByName("EzbTestPlatform") ?: new Platform(name: "EzbTestPlatform").save(flush: true)
    }
  }

  def cleanup() {
  }

  void "test buildPackageName"() {

  }

  void "test create new package info"() {

    given:
    def item = [
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
      ezb_collection_metadata_changed_date: "2018-02-06 10:03:42"
    ]

    Org.withNewSession {
      item.ezb_collection_provider = Org.findByName("EzbTestProvider").uuid
      item.ezb_collection_platform = Platform.findByName("EzbTestPlatform").uuid
    }

    def type_results = [
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
    def result = ezbCollectionService.processPackageInfo(item, type_results)

    then:
    result.skipped == false
    result.sourceResult != false
    result.pkgCreated != null
    result.curator_id != null
    result.pkgInfo?.name == "EZB-NALIW-00492: Sage Journals Online / Humanities and Social Sciences (HSS): Nationalkonsortium"
    type_results.total == 1
    type_results.created == 1
    Package.withNewSession {
      Package new_ezb_pkg = Package.findByName("EZB-NALIW-00492: Sage Journals Online / Humanities and Social Sciences (HSS): Nationalkonsortium")
      new_ezb_pkg?.ids?.size() == 2
      new_ezb_pkg.provider == Org.findByName("EzbTestProvider")
      new_ezb_pkg.nominalPlatform == Platform.findByName("EzbTestPlatform")
    }
  }

  void "test import ezb package content"() {

    given:
    def item = [
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
      ezb_collection_metadata_changed_date: "2021-11-04 08:29:34"
    ]

    Org.withNewSession {
      item.ezb_collection_provider = Org.findByName("EzbTestProvider").uuid
      item.ezb_collection_platform = Platform.findByName("EzbTestPlatform").uuid
    }

    def type_results = [
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
    Package.withNewSession {
      Package new_ezb_pkg = Package.findByName("EZB-WISO-01791: WISO Pflege")
      new_ezb_pkg?.tipps?.size() > 0
    }
  }
}
