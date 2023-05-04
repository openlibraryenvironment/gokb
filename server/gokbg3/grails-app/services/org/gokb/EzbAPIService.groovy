package org.gokb

import groovy.util.slurpersupport.NodeChild
import groovy.util.XmlSlurper

import io.micronaut.http.*
import io.micronaut.http.client.*
import io.micronaut.http.uri.UriBuilder

class EzbAPIService {
  static transactional = false
  static BlockingHttpClient client
  def grailsApplication
  static String baseUrl = "http://rzblx1.uni-regensburg.de"

  @javax.annotation.PostConstruct
  def init() {
    log.debug("Initialising rest endpoint for EZB service...")
  }

  def lookup(String name, def ids) {
    def candidates = []

    if (!client) {
      client = HttpClient.create(baseUrl.toURL()).toBlocking()
    }

    for (id in ids) {
      if (id.namespace.value in ['eissn', 'issn', 'zdb']) {
        try {
          def queryPars = [
            bib: 'HBZ',
            hits_per_page: '10',
            xmloutput: '1',
            xmlv: '3',
            jq_type1: id.namespace.value == 'zdb' ? 'ZD' : 'IS',
            jq_term: id.value
          ]
          def uriBuilder = UriBuilder.of(baseUrl)
            .path("/ezeit/searchres.phtml")

          queryPars.each { k, v ->
            uriBuilder.queryParam(k, v)
          }

          URI final_uri = uriBuilder.build()

          HttpResponse resp = client.exchange(HttpRequest.GET(final_uri), String)
          def data = new XmlSlurper().parseText(resp.body())

          if (!data.children().isEmpty()) {
            candidates = data.'**'.findAll() { node -> node.name() == 'journal' }
          }
        }
        catch ( io.micronaut.http.client.exceptions.HttpClientResponseException e ) {
          log.error('Error fetching EZB record!', e)
          return e.status.code
        }
        catch ( Exception e ) {
          log.error('Error fetching EZB record!', e)
          return 500
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
