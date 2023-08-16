package org.gokb

import groovy.xml.XmlSlurper

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.uri.UriBuilder

class ZdbAPIService {

  static transactional = false
  def grailsApplication

  static BlockingHttpClient http

  static Map CONFIG = [
    version: "1.1",
    recordSchema: "PicaPlus-xml",
    issTerm: "dnb.iss=",
    zdbTerm: "dnb.zdbid=",
    onlineOnly: " and dnb.frm=O",
    prefix:  "",
    baseUrl: "http://services.dnb.de",
    path: "/sru/zdb"
  ]

  @javax.annotation.PostConstruct
  def init() {
    log.info("Initialising rest endpoint for ZDB service...")
    http = HttpClient.create(new URL(CONFIG.baseUrl)).toBlocking()
  }

  def lookup(String name, def ids) {
    def candidate_ids = [direct: [], parallel: [], matched: []]

    for (id in ids) {
      if (id.namespace.value == 'eissn' || id.namespace.value == 'issn' || id.namespace.value == 'zdb') {
        try {
          URI uri = UriBuilder.of(CONFIG.baseUrl)
            .path(CONFIG.path)
            .queryParam('version', CONFIG.version)
            .queryParam('operation', 'searchRetrieve')
            .queryParam('recordSchema', CONFIG.recordSchema)
            .queryParam('maximumRecords', "10")
            .queryParam('query', (id.namespace.value == 'zdb' ? CONFIG.zdbTerm : CONFIG.issTerm) + id.value + CONFIG.onlineOnly)
            .build()

          HttpResponse resp = http.exchange(HttpRequest.GET(uri), String)

          if (resp.status == HttpStatus.OK) {
            def data = new XmlSlurper().parseText(resp.body())

            log.debug("Got " + (data.records?.record?.size() ?: 0) + " for " + id.namespace.value + ": " + id.value)

            if (data.records?.record?.size() > 0) {
              data.records.record.each { rec ->
                def zdb_info = getZdbInfo(rec)

                if (zdb_info) {
                  log.debug("Found ID candidate ${zdb_info.id}")
                  if (id.namespace.value == 'eissn' && zdb_info.direct.contains(id.value) && !candidate_ids.direct.find { it.id == zdb_info.id }) {
                    candidate_ids.direct.add(zdb_info)
                  }
                  else if (!candidate_ids.parallel.find { it.id == zdb_info.id } && zdb_info.parallel.contains(id.value)) {
                    candidate_ids.parallel.add(zdb_info)
                  }
                  else if (id.namespace.value == 'zdb' && zdb_info.id == id.value) {
                    candidate_ids.matched.add(zdb_info)
                  }
                  else {
                    log.debug("Skipping duplicate/unused ID result! (${id.value} -> $zdb_info)")
                  }
                }
                else {
                  log.error("Unable to parse record ${rec}")
                }
              }
            }
            else {
              log.debug("No records")
            }
          }
        }
        catch (HttpClientResponseException e) {
          log.error("Error fetching ZDB record for '$id' (status $e.status.code)!", e)
          break
        }
        catch ( Exception e ) {
          log.error("Error fetching ZDB record for '$id'!", e)
          break
        }
      }
      else {
        log.debug("Skipping value for namespace ${id.namespace.value}!")
      }
    }
    if (candidate_ids.matched.size() > 0) {
      return candidate_ids.matched
    }
    else if (candidate_ids.direct.size() > 0) {
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
    result.mainTitle = rec.global.'*'.find { it.@id == '021A' }.'*'.find {it.@id == 'a'}.text().trim()
    result.subtitle = rec.global.'*'.find { it.@id == '021C' }.'*'.find {it.@id == 'r'}.text()?.trim() ?: null

    if (!result.subtitle) {
      result.subtitle = rec.global.'*'.find { it.@id == '021C' }.'*'.find {it.@id == 'a'}.text()?.trim() ?: null
    }

    result.displayTitle = rec.global.'*'.find { it.@id == '025@' }.'*'.find {it.@id == 'a'}.text()?.trim() ?: null

    result.title = GOKbTextUtils.cleanTitleString(result.mainTitle)

    if (result.subtitle) {
      result.subtitle = GOKbTextUtils.cleanTitleString(result.subtitle)
      result.title = result.title  + '. ' + result.subtitle
    }
    else if (result.displayTitle) {
      result.title = GOKbTextUtils.cleanTitleString(result.displayTitle)
    }

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
        if (subfield.@id == '0' && subfield.text().indexOf('-') == subfield.text().length() - 2) {
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
    log.debug("Destroy");
  }
}
