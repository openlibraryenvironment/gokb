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
  def endpoint = 'zdb'
  def target_service = null
  def grailsApplication

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
    baseUrl: [
      kxp: "http://sru.k10plus.de/k10plus",
      zdb: "http://services.dnb.de/sru/zdb"
    ]
  ]

  @javax.annotation.PostConstruct
  def init() {
    log.debug("Initialising rest endpoint for ZDB service...");
    // endpoint = checkKxpAccess() ? 'kxp' : 'zdb'
  }

  def checkKxpAccess () {
    boolean result = true

    def testUrl = "https://sru.k10plus.de/k10plus"
    def testClient = new RESTClient(testUrl)

    try {
      testClient.request(GET, ContentType.XML) { request ->
        uri.query = [
          version: config.version.kxp,
          operation: "searchRetrieve",
          recordSchema: config.recordSchema.kxp,
          maximumRecords: "10",
          query: "pica.zdb=2936849-2"
        ]
        response.success = { resp, data ->
          if (data?.diagnostics.isEmpty()) {
            log.debug("KXP access established ..")
          }
          else {
            log.debug("KXP access denied ..")
            result = false
          }
        }
        response.failure = { resp, data ->
          log.debug("KXP returned error status ${resp.status}")
          result = false
        }
      }
    }
    catch (Exception e) {
      log.debug("Exception trying to lookup KXP access..", e)
      result = false
    }
    result
  }

  def lookup(String name, def ids) {
    def candidate_ids = [direct: [], parallel: []]

    ids.each { id ->
      if (id.namespace.value == 'eissn' || id.namespace.value == 'issn') {
        try {
          new RESTClient(config.baseUrl[endpoint]).request(GET, ContentType.XML) { request ->
            uri.query = [
              version: config.version[endpoint],
              operation: "searchRetrieve",
              recordSchema: config.recordSchema[endpoint],
              maximumRecords: "10",
              query: config.issTerm[endpoint] + id.value + config.onlineOnly[endpoint]
            ]

            response.success = { resp, data ->
              log.debug("Got " + data.records.size() + " for " + id.namespace.value + ": " + id.value)

              if (!data.records.children().isEmpty()) {
                data.records.findAll { rec ->
                  def zdb_info = null

                  if (endpoint == 'kxp') {
                    zdb_info = getKxpInfo(rec)
                  }
                  else {
                    zdb_info = getZdbInfo(rec)
                  }

                  if (zdb_info) {
                    log.debug("Found ID candidate ${zdb_info.id}")
                    if (id.namespace.value == 'eissn' && !candidate_ids.direct.find { it.id == zdb_info.id }) {
                      candidate_ids.direct.add(zdb_info)
                    }
                    else if (!candidate_ids.parallel.find { it.id == zdb_info.id }) {
                      candidate_ids.parallel.add(zdb_info)
                    }
                  }
                }
              }
            }
          }
        }
        catch ( Exception e ) {
          e.printStackTrace();
        }
      }
    }

    if (candidate_ids.direct.size() > 0) {
      return candidate_ids.direct
    }
    else {
      return candidate_ids.parallel
    }
  }

  def getKxpInfo(record, isOnline) {
    def result = [:]
    def rec = record.record.recordData.record

    result.id = rec.'*'.find { it.@tag == '006Z' }.subfield[0].text()

    rec.'*'.findAll { it.@tag == '039D' }.each { lf ->
      def validLink = false
      def idVal = null

      lf.'*'.each { subfield ->
        if (subfield.@code == 'R') {
          validLink = subfield.text().startsWith('O')
        }
        if (subfield.@code == '7') {
          idVal = subfield.text().substring(5, subfield.text().length())
        }
      }

      if (validLink) {
        result.id = idVal
      }
    }

    result
  }

  def getZdbInfo(record) {
    def result = [:]
    def rec = record.record.recordData.record

    result.id = rec.global.'*'.find { it.@id == '006Z' }[0].text()
    result.title = rec.global.'*'.find { it.@id == '021A' }[0].text()
    result.subtitle = rec.global.'*'.find { it.@id == '021C' }.'*'.find {it.@id == 'a'}.text() ?: null

    def fromDate = rec.global.'*'.find { it.@id == '011@'}.'*'.find {it.@id == 'a'}
    def toDate = rec.global.'*'.find { it.@id == '011@'}.'*'.find {it.@id == 'b'}

    if (fromDate)
      result.publishedFrom = fromDate.text()

    if (toDate)
      result.publishedTo = toDate.text()

    def pubName = rec.global.'*'.find { it.@id == '033A' }.'*'.find {it.@id == 'n'}

    if (pubName) {
      result.publisher = pubName.text()
    }

    def otherPubs = []

    rec.global.'*'.findAll { it.@id == '033B'}.each { otherpub ->
      otherpub.'*'.each { subfield ->
        if (subfield.@id == 'n') {
          otherPubs.add(subfield.text())
        }
      }
    }

    if (otherPubs.size() > 0) {
      result.publisher_history = otherPubs
    }

    result.direct = []
    result.parallel = []

    rec.global.'*'.findAll { it.@id == '005A' }.each { eissn ->

      eissn.'*'.each { subfield ->
        if (subfield.@id == '0') {
          result.direct.add(subfield.text())
        }
      }
    }

    rec.global.'*'.findAll { it.@id == '039D' }.each { lf ->
      def validLink = false
      def idVal = null

      lf.'*'.each { subfield ->
        if (subfield.@id == 'g') {
          validLink = subfield.text().startsWith('A')
        }
        if (subfield.@id == 'I') {
          idVal = subfield.text()
        }
      }

      if (validLink) {
        result.parallel.add(idVal)
      }
    }

    result.history = []

    rec.global.'*'.findAll { it.@id == '039E' }.each { lf ->
      def item = [:]

      lf.'*'.each { subfield ->
        if (subfield.@id == 'b') {
          item.prev = subfield.text().startsWith('f')
        }
        if (subfield.@id == 'I') {
          item.issn = subfield.text()
        }
        if (subfield.@id == '0') {
          item.zdbId = subfield.text()
        }
        if (subfield.@id == 'Y') {
          item.name = subfield.text()
        }
        if (subfield.@id == 'H') {
          def val = subfield.text()
          if (val && !val.contains('[')) {
            item.publishedFrom = val.contains('-') ? val.split('-')[0] : val

            if (val.contains('-') && val.split('-')[1]?.length() > 0) {
              item.publishedTo = val.split('-')[1]
            }
          }
        }
      }

      if (item) {
        result.history.add(item)
      }
    }

    result
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
  }
}
