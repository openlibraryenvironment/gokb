package org.gokb

import groovy.util.slurpersupport.GPathResult
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*
import groovyx.net.http.*
import org.apache.http.entity.mime.*
import org.apache.http.entity.mime.content.*
import java.nio.charset.Charset
import org.apache.http.*
import org.apache.http.protocol.*

class ZdbAPIService {

  static transactional = false
  def endpoint
  def target_service
  def grailsApplication
  def kxpAccess = true

  def config = [
    version: [
      kxp: "1.2",
      zdb: "1.1"
    ],
    recordSchema: [
      kxp: "picaxml",
      zdb: "PicaPlus-xml"
    ],
    issTerm: [
      kxp: "pica.iss=",
      zdb: "dnb.iss="
    ],
    onlineOnly: [
      kxp: " and pica.bbg=O*",
      zdb: " and dnb.frm=O"
    ],
    prefix: [
      kxp: "zs:",
      zdb: ""
    ],
  ]

  @javax.annotation.PostConstruct
  def init() {
    log.debug("Initialising rest endpoint for ZDB service...");
    kxpAccess = checkAccess()
    endpoint = kxpAccess ? "http://sru.k10plus.de/k10plus" : "http://http://services.dnb.de/sru/zdb"
    target_service = new RESTClient(endpoint)
  }

  def checkAccess () {
    boolean result = true
    def testUrl = "http://sru.k10plus.de/k10plus"
    def testClient = new RESTClient(testUrl)

    try {
      target_service.request(GET, ContentType.XML) { request ->
        uri.query = [
          version: config.kxp.version,
          operation: "searchRetrieve",
          recordSchema: config.kxp.recordSchema,
          maximumRecords: "10",
          query: "pica.zdb=2936849-2"
        ]

        response.success = { resp, data ->
          if (data.'zs:searchRetrieveResponse'.'zs:diagnostics') {
            result = false
          }
        }

        response.failure = { resp, data ->
          result = false
        }
      }
    }
    catch (Exception e) {
      log.debug("No KXP access..")
      result = false
    }

    result
  }

  def lookup(String name, def ids) {
    def activeConfig = kxpAccess ? "kxp" : "zdb"
    def prefix = config[activeConfig]['prefix']
    def candidate_ids = []

    ids.each { id ->
      if (id.namespace.value == 'issn' || id.namespace.value == 'eissn') {
        def qryString = config[activeConfig].issTerm + id.value + config[activeConfig].onlineOnly

        try {
          target_service.request(GET, ContentType.XML) { request ->
            // uri.path='/'
            uri.query = [
              version: config[activeConfig].version,
              operation: "searchRetrieve",
              recordSchema: config[activeConfig].recordSchema,
              maximumRecords: "10",
              query: qryString
            ]

            response.success = { resp, data ->
              Integer num = data[prefix + "searchRetrieveResponse"][prefix + "numberOfRecords"].toInteger()

              if (num > 0) {
                def records = data[prefix + "searchRetrieveResponse"][prefix + "records"]

                records.each { rec ->
                  def zdb_info = kxpAccess ? getKxpInfo(rec) : getZdbInfo(rec)

                  if (zdb_info && !candidate_ids.contains(zdb_info.id)) {
                    candidate_ids.add(zdb_info.id)
                  }
                }
              }
              else {
                log.debug("No ZDB candidate found for ${id}")
              }
            }
            response.failure = { resp ->
              log.error("Error - ${resp}");
            }
          }
        }
        catch ( Exception e ) {
          e.printStackTrace();
        }
      }
    }

    candidate_ids
  }

  def getKxpInfo(record) {
    def result = [:]
    def rec = record['zs:recordData']['record']

    result.id = rec.'*'.find { it.@tag == '006Z' }['subfield'][0].text()

    result
  }

  def getZdbInfo(record) {
    def result = [:]
    def rec = record['recordData']['ppxml:record']['ppxml:global']

    result.id = rec.'*'.find { it.@id == '006Z' }['ppxml:subf'][0].text()

    result
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
  }
}
