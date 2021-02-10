package org.gokb

import groovy.json.JsonSlurper
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress

import static groovy.json.JsonOutput.*

class ESWrapperService {

  static transactional = false

  def grailsApplication

  TransportClient esclient = null
  File mappingFile = new File("src${File.separator}elasticsearch${File.separator}es_mapping.json")

  @javax.annotation.PostConstruct
  def init() {
    log.debug("init ES wrapper service");
  }

  def getSettings() {
    def settings = [
      number_of_shards: 1,
      analysis: [
        filter: [
          autocomplete_filter: [
            type: "edge_ngram",
            min_gram: 1,
            max_gram: 20
          ]
        ],
        analyzer: [
          autocomplete: [
            type: "custom",
            tokenizer: "standard",
            filter: ["lowercase","autocomplete_filter"]
          ]
        ]
      ]
    ]
    return settings
  }

  def getMapping() {
    def mapping = new JsonSlurper().parse(new File(mappingFile))




    return mapping
  }

  private def ensureClient() {

    if ( esclient == null ) {
      def es_cluster_name = grailsApplication.config?.gokb?.es?.cluster
      def es_host_name = grailsApplication.config?.gokb?.es?.host
      log.debug("Elasticsearch client is null, creating now... host: ${es_host_name} cluster:${es_cluster_name}")
      log.debug("Looking for Elasticsearch on host ${es_host_name} with cluster name ${es_cluster_name}")

      Settings settings = Settings.builder().put("cluster.name", es_cluster_name).build()
      esclient = new org.elasticsearch.transport.client.PreBuiltTransportClient(settings)
      esclient.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(es_host_name), 9300))
      log.debug("ES wrapper service init completed OK")
    }

    esclient
  }

  def index(index,typename,record_id, record) {
    log.debug("indexing ... ${typename},${record_id},...")
    def result=null
    try {
      def future = ensureClient().prepareIndex(index,typename,record_id).setSource(record)
      result=future.get()
    }
    catch ( Exception e ) {
      log.error("Error processing ${toJson(record)}",e)
      e.printStackTrace()
    }
    log.debug("indexing complete")
    result
  }


  def getClient() {
    return ensureClient()
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Close Elasticsearch client.")
    esclient.close()
  }

}
