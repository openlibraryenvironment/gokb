package org.gokb


import groovy.json.JsonSlurper
import org.apache.http.HttpHost
import org.opensearch.client.RestClient
import org.opensearch.client.RestHighLevelClient

import static groovy.json.JsonOutput.*

class ESWrapperService {

  static transactional = false
  def grailsApplication
  RestHighLevelClient esClient

  static Map indicesPerType = [
      "JournalInstance" : "titles",
      "DatabaseInstance" : "titles",
      "OtherInstance" : "titles",
      "BookInstance" : "titles",
      "TitleInstance" : "titles",
      "TitleInstancePackagePlatform" : "tipps",
      "Org" : "orgs",
      "Package" : "packages",
      "Platform" : "platforms"
  ]

  @javax.annotation.PostConstruct
  def init() {
    log.debug("Init Elasticsearch wrapper service...")
  }


  def getSettings() {
    File settingsFile = new File(getClass().getResource(
        "/elasticsearch/es_settings.json").toURI())
    return new JsonSlurper().parse(settingsFile)
  }


  def getMapping() {
    File mappingFile = new File(getClass().getResource(
        "/elasticsearch/es_mapping.json").toURI())
    return new JsonSlurper().parse(mappingFile)
  }


  private void newClient() {
    def es_host_name = grailsApplication.config.getProperty('gokb.es.host')
    def es_port = grailsApplication.config.getProperty('gokb.es.ports', Collection).get(0) ?: 9200

    log.debug("Elasticsearch client is null, creating now... host: ${es_host_name}")
    log.debug("... looking for Elasticsearch on host ${es_host_name}")
    esClient = new RestHighLevelClient(RestClient.builder(new HttpHost(es_host_name, es_port, "http")))
    log.debug("... Elasticsearch wrapper service init completed")
  }


  def index(index,typename,record_id, record) {
    log.debug("Indexing ... type: ${typename}, id: ${record_id}...")
    def result=null
    try {
      def future = getClient().prepareIndex(index,typename,record_id).setSource(record)
      result=future.get()
    }
    catch ( Exception e ) {
      log.error("Error processing ${toJson(record)}", e)
      e.printStackTrace()
    }
    log.debug("... indexing complete")
    result
  }


  def getClient() {
    if (!esClient) {
      newClient()
    }
    esClient
  }


  @javax.annotation.PreDestroy
  def destroy() {
    try {
      esClient?.close()
    }
    catch (Exception e) {
      log.error("Problem occurred closing Elasticsearch client", e)
    }
  }

}
