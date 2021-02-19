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
  def target_service = null
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
    printOnly: [
      kxp: " and pica.bbg=A*",
      zdb: " and dnb.frm=A"
    ],
    prefix: [
      kxp: "zs:",
      zdb: ""
    ],
  ]

  @javax.annotation.PostConstruct
  def init() {
    log.debug("Initialising rest endpoint for ZDB service...");
    // kxpAccess = checkKxpAccess()
    target_service = new RESTClient("https://www.zeitschriftendatenbank.de/api/hydra").setHeaders(['User-Agent': 'gokb'])
  }

  def checkKxpAccess () {
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
    def candidate_ids = []

    ids.each { id ->
      if (id.namespace.value == 'eissn' || id.namespace.value == 'issn') {
        try {
          new RESTClient("https://www.zeitschriftendatenbank.de/api/hydra").request(GET, ContentType.JSON) { request ->
            // uri.path='/'
            uri.query = [
              q: "iss=" + id.value
            ]

            response.success = { resp, data ->
              data.member?.each { rec ->
                log.debug("Checking record ${rec}")

                if ((id.namespace.value == 'eissn' && rec.data['002@'][0][0][0].startsWith('O'))  || (id.namespace.value == 'issn' && rec.data['002@'][0][0][0].startsWith('A'))) {
                  def zdb_info = getZdbInfo(rec, (id.namespace.value == 'eissn' ? true : false))

                  if (zdb_info && !candidate_ids.contains(zdb_info.id)) {
                    log.debug("Found ID candidate ${zdb_info.id}")
                    candidate_ids.add(zdb_info.id)
                  }
                  else {
                    log.debug("Not adding ${zdb_info}")
                  }
                }
                else {
                  log.debug("Skipping parallel title")
                }
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

  def getKxpInfo(record, isOnline) {
    def result = [:]
    def rec = record['zs:recordData']['record']

    if (isOnline) {
      result.id = rec.'*'.find { it.@tag == '006Z' }['subfield'][0].text()
    }
    else {
      result.id = rec.'*'.find { it.@tag == '006Z' }['subfield'][0].text()
    }

    result
  }

  def getZdbInfo(record, isOnline) {
    def result = [:]

    if (isOnline) {
      result.id = record.data['006Z'][0][0]
    }
    else {
      def online_id = null

      record.data['039D']?.each { field ->
        field.each { pos ->
          if (pos instanceof List) {
            online_id = pos[0]
          }
        }
      }

      if (online_id) {
        result.id = online_id
      }
    }

    result
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
  }
}
