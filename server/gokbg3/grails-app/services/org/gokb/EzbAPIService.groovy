package org.gokb

import static groovyx.net.http.Method.*
import groovyx.net.http.*

class EzbAPIService {
  static transactional = false
  def endpoint = 'rzblx1'
  def grailsApplication

  def config = [
    baseUrl: [
      rzblx1: "http://rzblx1.uni-regensburg.de/ezeit/searchres.phtml?bibid=HBZ&"
    ],
    queryByZdbId: [
      rzblx1: "jq_type1=ZD&jq_term1="
    ],
    queryByIssn: [
      rzblx1: "jq_type1=IS&jq_term1="
    ],
    xmloutput: [
      rzblx1: "1"
    ],
    xmlv: [
      rzblx1: "3"
    ]
  ]

  @javax.annotation.PostConstruct
  def init() {
    log.debug("Initialising rest endpoint for EZB service...")
  }

  def lookup(String name, def ids) {
    def candidate_ids = [direct: [], parallel: []]

    ids.each { id ->
      String queryTerm
      if (id.namespace.value in ['eissn', 'issn']) {
        queryTerm = config.queryByIssn[endpoint]
      }
      else if (id.namespace.value == 'zdb'){
        queryTerm = config.queryByZdbId[endpoint]
      }
      if (queryTerm) {
        try {
          new RESTClient(config.baseUrl[endpoint] + queryTerm + id.value).request(GET, ContentType.XML) { request ->
            uri.query = [
              hits_per_page: "10",
              xmloutput: config.xmloutput[endpoint],
              xmlv: config.xmlv[endpoint]
            ]

            response.success = { resp, data ->
              log.debug("Got " + data.records.record.size() + " for " + id.namespace.value + ": " + id.value)

              if (!data.records.children().isEmpty()) {
                data.records.record.findAll { rec ->
                  def zdb_info = getZdbInfo(rec)
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
            response.failure = { resp ->
              log.debug("Got no results for " + id.namespace.value + " : " + id.value)
            }
          }
        }
        catch ( Exception e ) {
          e.printStackTrace()
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

  def getZdbInfo(record) {
    def result = [:]
    def rec = record.recordData.record

    result.id = rec.global.'*'.find { it.@id == '006Z' }[0].text()
    result.title = rec.global.'*'.find { it.@id == '021A' }.'*'.find {it.@id == 'a'}.text()
    result.subtitle = rec.global.'*'.find { it.@id == '021C' }.'*'.find {it.@id == 'a'}.text() ?: null

    def fromDate = rec.global.'*'.find { it.@id == '011@'}.'*'.find {it.@id == 'a'}
    def toDate = rec.global.'*'.find { it.@id == '011@'}.'*'.find {it.@id == 'b'}

    if (fromDate){
      result.publishedFrom = fromDate.text()
    }

    if (toDate){
      result.publishedTo = toDate.text()
    }

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
            item.publishedFrom = val.contains('-') ? val.split('-')[0].trim() : val

            if (val.contains('-')) {
              def pubToDate = val.split('-').size() == 2 ? val.split('-')[1].trim() : null

              if (pubToDate) {
                item.publishedTo = val.split('-')[1]
              }
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
    log.debug("Destroy")
  }
}
