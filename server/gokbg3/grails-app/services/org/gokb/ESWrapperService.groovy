package org.gokb

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.ElasticsearchTransport
import co.elastic.clients.transport.rest_client.RestClientTransport
import groovy.json.JsonSlurper
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient

import static groovy.json.JsonOutput.*

class ESWrapperService {

  static transactional = false
  def grailsApplication
  ElasticsearchClient esClient


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


  private def newClient() {
    def es_cluster_name = grailsApplication.config?.gokb?.es?.cluster
    def es_host_name = grailsApplication.config?.gokb?.es?.host
    def es_port = grailsApplication.config?.gokb?.es?.port ?: 9200
    log.debug("Elasticsearch client is null, creating now... host: ${es_host_name}, cluster:${es_cluster_name}")
    log.debug("... looking for Elasticsearch on host ${es_host_name} with cluster name ${es_cluster_name}")
    RestClient restClient = RestClient.builder(new HttpHost(es_host_name, es_port)).build()
    ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper())
    esClient = new ElasticsearchClient(transport)
    log.debug("... Elasticsearch wrapper service init completed")
    esClient
  }


  def index(index,typename,record_id, record) {
    log.debug("Indexing ... type: ${typename}, id: ${record_id}...")
    def result=null
    try {
      def future = newClient().prepareIndex(index,typename,record_id).setSource(record)
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
    return newClient()
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
