package org.gokb

import groovy.json.JsonSlurper
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient

import static groovy.json.JsonOutput.*

class ESWrapperService {

  static transactional = false
  def grailsApplication
  RestHighLevelClient esClient = null

  @javax.annotation.PostConstruct
  def init() {
    log.debug("Init Elasticsearch wrapper service...")
  }


  def getSettings() {
    File settingsFile = new File(getClass().getResource(
        "${File.separator}elasticsearch${File.separator}es_settings.json").toURI())
    return new JsonSlurper().parse(settingsFile)
  }


  def getMapping() {
    File mappingFile = new File(getClass().getResource(
        "${File.separator}elasticsearch${File.separator}es_mapping.json").toURI())
    return new JsonSlurper().parse(mappingFile)
  }


  private def ensureClient() {
    if ( esClient == null ) {
      def es_cluster_name = grailsApplication.config?.gokb?.es?.cluster
      def es_host_name = grailsApplication.config?.gokb?.es?.host
      log.debug("Elasticsearch client is null, creating now... host: ${es_host_name}, cluster:${es_cluster_name}")
      log.debug("... looking for Elasticsearch on host ${es_host_name} with cluster name ${es_cluster_name}")
      esClient = new RestHighLevelClient(RestClient.builder(new HttpHost(es_host_name, 9200, "http")))
      log.debug("... Elasticsearch wrapper service init completed")
    }
    esClient
  }


  def index(index,typename,record_id, record) {
    log.debug("Indexing ... type: ${typename}, id: ${record_id}...")
    def result=null
    try {
      def future = ensureClient().prepareIndex(index,typename,record_id).setSource(record)
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
    return ensureClient()
  }


  static def close(def esClient) {
    try {
      esClient.close()
    }
    catch (Exception e) {
      log.error("Problem occurred closing Elasticsearch client", e)
    }
  }


  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Close Elasticsearch client.")
    esClient.close()
  }

}
