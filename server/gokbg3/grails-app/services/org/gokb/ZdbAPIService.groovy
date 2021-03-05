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
  def useHydra = false

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
          if (!useHydra) {
            new RESTClient(config.baseUrl[endpoint]).request(GET, ContentType.XML) { request ->
              uri.query = [
                version: config.version[endpoint],
                operation: "searchRetrieve",
                recordSchema: config.recordSchema[endpoint],
                maximumRecords: "10",
                query: config.issTerm[endpoint] + id.value + (id.namespace.value == 'eissn' ? config.onlineOnly[endpoint] : config.printOnly[endpoint])
              ]

              response.success = { resp, data ->
                log.debug("Got " + data.records.size() + " for " + id.namespace.value + ": " + id.value)

                if (!data.records.children().isEmpty()) {
                  data.records.findAll { rec ->
                    def zdb_info = null

                    if (endpoint == 'kxp') {
                      zdb_info = getKxpInfo(rec, (id.namespace.value == 'eissn' ? true : false))
                    }
                    else {
                      zdb_info = getZdbInfo(rec, (id.namespace.value == 'eissn' ? true : false))
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
          else {
            new RESTClient("https://www.zeitschriftendatenbank.de/api/hydra").request(GET, ContentType.JSON) { request ->
              // uri.path='/'
              uri.query = [
                q: "iss=" + id.value
              ]

              response.success = { resp, data ->
                data.member?.each { rec ->
                  if ((id.namespace.value == 'eissn' && rec.data['002@'][0][0][0].startsWith('O'))  || (id.namespace.value == 'issn' && rec.data['002@'][0][0][0].startsWith('A'))) {
                    def zdb_info = getZdbInfo(rec, (id.namespace.value == 'eissn' ? true : false), true)

                    if (zdb_info) {
                      log.debug("Found ID candidate ${zdb_info.id}")
                      if (id.namespace.value == 'eissn' && !candidate_ids.direct.contains(zdb_info.id)) {
                        candidate_ids.direct.add(zdb_info.id)
                      }
                      else if (!candidate_ids.parallel.contains(zdb_info.id)) {
                        candidate_ids.parallel.add(zdb_info.id)
                      }
                    }
                  }
                }
              }
              response.failure = { resp ->
                log.error("Error - ${resp}");
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

    if (isOnline) {
      result.id = rec.'*'.find { it.@tag == '006Z' }.subfield[0].text()
    }
    else {
      rec.'*'.findAll { it.@tag == '039D' }.each { lf ->
        def validLink = false
        def idVal = null

        lf.'*'.each { sf ->
          if (sf.@code == 'R') {
            validLink = sf.text().startsWith('O')
          }
          if (sf.@code == '7') {
            idVal = sf.text().substring(5, sf.text().length())
          }
        }

        if (validLink) {
          result.id = idVal
        }
      }
    }

    result
  }

  def getZdbInfo(record, isOnline, boolean useHydra = false) {
    def result = [:]

    if (!useHydra) {
      def rec = record.record.recordData.record

      if (isOnline) {
        result.id = rec.global.'*'.find { it.@id == '006Z' }[0].text()

        def fromDate = rec.global.'*'.find { it.@id == '011@'}.'*'.find {it.@id == 'a'}
        def toDate = rec.global.'*'.find { it.@id == '011@'}.'*'.find {it.@id == 'b'}

        if (fromDate)
          result.publishedFrom = fromDate.text()

        if (toDate)
          result.publishedTo = toDate.text()
      }
      else {
        rec.global.'*'.findAll { it.@id == '039D' }.each { lf ->
          def validLink = false
          def idVal = null

          lf.'*'.each { sf ->
            if (sf.@id == 'g') {
              validLink = sf.text().startsWith('O')
            }
            if (sf.@id == '0') {
              idVal = sf.text()
            }
          }

          if (validLink) {
            result.id = idVal
          }
        }
      }
    }
    else {
      if (isOnline) {
        result.id = record.data['006Z'][0][0][0]

        if (record.data['011@'][0]['a']) {
          result.publishedFrom = record.data['011@'][0]['a'][0] ?: null
        }

        if (record.data['011@'][0]['b']) {
          result.publishedTo = record.data['011@'][0]['b'][0] ?: null
        }
      }
      else {
        record.data['039D'].each { lf ->
          def validLink = false
          def idVal = null

          lf.each { sf ->
            if (sf instanceof List) {
              idVal = sf[0]
            }
            else if (sf['g'] && sf['g'].startsWith('O')) {
              validLink = true
            }
          }

          if (validLink) {
            result.id = idVal
          }
        }
      }

    }

    result
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
  }
}
