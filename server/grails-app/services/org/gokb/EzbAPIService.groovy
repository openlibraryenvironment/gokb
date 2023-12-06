package org.gokb

import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.NodeChild

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.uri.UriBuilder

class EzbAPIService {
  static transactional = false
  def endpoint = 'rzblx1'
  def grailsApplication

  BlockingHttpClient http

  static Map CONFIG = [
    baseUrl:  "http://ezb.ur.de",
    path: "/ezeit/searchres.phtml",
    queryByZdbId: "jq_type1=ZD&jq_term1=",
    queryByIssn: "jq_type1=IS&jq_term1=",
    qtype: [
      zdb: "ZD",
      issn: "IS"
    ],
    xmloutput: "1",
    xmlv: "3"
  ]

  @javax.annotation.PostConstruct
  def init() {
    log.info("Initialising rest endpoint for EZB service...")
    http = HttpClient.create(new URL(CONFIG.baseUrl)).toBlocking()
  }

  def lookup(String name, def ids) {
    def candidates = []
    ids.each { id ->
      String type_val

      if (id.namespace.value in ['eissn', 'issn']) {
        type_val = CONFIG.qtype.issn
      }
      else if (id.namespace.value == 'zdb'){
        type_val = CONFIG.qtype.zdb
      }
      if (type_val) {
        try {
          URI uri = UriBuilder.of(CONFIG.baseUrl)
            .path(CONFIG.path)
            .queryParam('version', CONFIG.version)
            .queryParam('jq_type1', type_val)
            .queryParam('xmloutput', CONFIG.xmloutput)
            .queryParam('xmlv', CONFIG.xmlv)
            .queryParam('hits_per_page', "10")
            .queryParam('jq_term1', id.value)
            .build()

          HttpRequest request = HttpRequest.GET(uri)
          HttpResponse resp = http.exchange(request, String)

          def data = new XmlSlurper().parseText(resp.body())

          if (data.ezb_alphabetical_list_searchresult?.alphabetical_order?.journals?.journal?.size() > 0) {
            candidates = data.'**'.findAll(){ node -> node.name() == 'journal' }
          }
        }
        catch ( Exception e ) {
          e.printStackTrace()
        }
      }
    }
    return candidates
  }


  static String getJourId(NodeChild journalNode){
    try{
      def jourIdAttributes = journalNode.attributes().findAll(){ attribute -> attribute.key == "jourid" }
      assert jourIdAttributes.size() < 2
      for (def jourIdAttribute in jourIdAttributes){
        return jourIdAttribute.value
      }
    }
    catch (AssertionError | Exception e){
      e.printStackTrace()
    }
    return null
  }


  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy")
  }
}
