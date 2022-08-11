package org.gokb

import groovy.util.logging.Slf4j
import com.k_int.ConcurrencyManagerService.Job
import static groovyx.net.http.Method.*
import groovyx.net.http.RESTClient

@Slf4j
class EzbCollectionService {

  def componentLookupService
  def concurrencyManagerService
  def packageService

  static ArrayList activeTypes = [
    "collections_from_national_license_package",
    "collections_from_alliance_license_package",
    "collections_from_consortia_package",
    "collections_from_national_consortia_package"
  ]

  def startUpdate(Job job) {
    if (!job) {
      job = concurrencyManagerService.startOrQueue { ljob ->
        fetchUpdatedLists(ljob)
      }
    } else {
      fetchUpdatedLists(job)
    }
  }

  def fetchUpdatedLists (job) {
    if (grailsApplication.config.gokb.ezbOpenCollections?.enabled && grailsApplication.config.gokb.ezbOpenCollections?.url) {
      def baseUrl = grailsApplication.config.gokb.ezbOpenCollections.url
      Map allCollections

      def client = new RESTClient(baseUrl)

      client.request(GET, ContentType.JSON) {
        response.success = { resp, data ->
          data.collections?.each { type, items ->
            if (type in activeTypes) {
              allCollections[type] = items
            }
          }
        }
        response.failure = { resp, data ->
          log.error("Got status ${resp.status} .. ${data}")
        }
      }

      allCollections.each { type, item ->
        def pkgName = "${item.ezb_collection_id}: ${item.ezb_collection_name}"
        CuratoryGroup curator = CuratoryGroup.findByName(item.ezb_collection_curatory_group != '' ? item.ezb_collection_curatory_group : grailsApplication.config.gokb.ezbAugment.rrCurators)
        Identifier collection_id = componentLookupService.lookupOrCreateCanonicalIdentifier('ezb-collection-id', item.ezb_collection_id)

        if (item.ezb_package_type_name != 'Konsortialpaket') {
          pkgName += ": ${item.ezb_package_type_name}"
        }
        else {
          pkgName += ": ${item.ezb_owner}"
        }

        Package obj = Package.findAllByNameILike(pkgName)

        if (!obj) {
          def candidates = Package.executeQuery('''from Package as p
                                                  where exists (select 1 from Combo where fromComponent = p and toComponent = :cg)
                                                  and exists (select 1 from Combo where fromCOmponent = p and toComponent = :cid)''', [cg: curator, cid: collection_id])
        }
      }
    }
    else {
      log.debug("No API base for open EZB collections configured.")
    }
  }
}