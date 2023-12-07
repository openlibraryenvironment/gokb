package org.gokb


import grails.testing.mixin.integration.Integration
import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.Org
import org.gokb.cred.Platform
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

  void "test fetch new package"() {

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
    def result = ezbCollectionService.processCollectionEntry(item, type_results)

    then:
    result.skipped == false
    result.sourceResult != false
    result.pkgCreated != null
    result.curator_id != null
    result.pkgInfo?.name == "EZB-NALIW-00492: Sage Journals Online / Humanities and Social Sciences (HSS): Nationalkonsortium"
    type_results.total == 1
    type_results.created == 1
  }
}
