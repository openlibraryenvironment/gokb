package org.gokb

import groovy.util.slurpersupport.NodeChild

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
    def candidates = []
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
          String uri = config.baseUrl[endpoint] + queryTerm + id.value +
              "&hits_per_page=10&xmloutput=${config.xmloutput[endpoint]}&xmlv=${config.xmlv[endpoint]}"
          new RESTClient(uri).request(GET, ContentType.XML) { request ->
            response.success = { resp, data ->
              log.debug("Got " + data.records.record.size() + " for " + id.namespace.value + ": " + id.value)
              if (!data.children().isEmpty()) {
                candidates = data.'**'.findAll(){ node -> node.name() == 'journal' }
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
