package org.gokb

import groovy.json.JsonBuilder

import java.net.InetAddress;

import org.elasticsearch.client.Client
import org.elasticsearch.node.Node
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress

import static groovy.json.JsonOutput.*

class ESWrapperService {

  static transactional = false

  def grailsApplication

  TransportClient esclient = null;

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
    def mapping = [
      component: [
        dynamic_templates: [],
        properties: [
          name: [
            type: "text",
            copy_to: "suggest",
            fields: [
              name: [type: "text"],
              altname: [type: "text"]
            ]
          ],
          identifiers: [
            type: "nested",
            properties: [
              namespace: [type: "keyword"],
              namespaceName: [type: "keyword"],
              value: [type: "keyword"]
            ]
          ],
          source : [
            type : "nested",
            properties: [
              frequency: [type: "keyword"],
              url: [type: "keyword"]
            ]
          ],
          sortname: [
            type: "keyword"
          ],
          componentType: [
            type: "keyword"
          ],
          lastUpdatedDisplay: [
            type: "date",
            format: "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd'T'HH:mm:ssZ||epoch_millis"
          ],
          uuid: [
            type: "keyword"
          ],
          status: [
            type: "keyword"
          ],
          suggest: [
            type: "text",
            analyzer: "autocomplete",
            search_analyzer: "standard"
          ]
        ]
      ]
    ]

    def dynamic = [
      provider: [
        match: "provider*",
        match_mapping_type: "string",
        mapping: [type: "keyword"]
      ],
      dateFirstInPrint: [
        match: "dateFirstInPrint",
        mapping: [
          type: "date",
          format: "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd'T'HH:mm:ssZ||epoch_millis"
        ]
      ],
      dateFirstOnline: [
        match: "dateFirstOnline",
        mapping: [
          type: "date",
          format: "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd'T'HH:mm:ssZ||epoch_millis"
        ]
      ],
      cpname: [
        match: "cpname",
        match_mapping_type: "string",
        mapping: [type: "keyword"]
      ],
      publisher: [
        match: "publisher*",
        match_mapping_type: "string",
        mapping: [type: "keyword"]
      ],
      listStatus: [
        match: "listStatus",
        match_mapping_type: "string",
        mapping: [type: "keyword"]
      ],
      package: [
        match: "tippPackage*",
        match_mapping_type: "string",
        mapping: [type: "keyword"]
      ],
      title: [
        match: "tippTitle*",
        match_mapping_type: "string",
        mapping: [type: "keyword"]
      ],
      hostPlatform: [
        match: "hostPlatform*",
        match_mapping_type: "string",
        mapping: [type: "keyword"]
      ],
      roles: [
        match: "roles",
        match_mapping_type: "string",
        mapping: [type: "keyword"]
      ],
      curGroups: [
        match: "curatoryGroups",
        match_mapping_type: "string",
        mapping: [type: "keyword"]
      ],
      nominalPlatform: [
        match: "nominalPlatform*",
        match_mapping_type: "string",
        mapping: [type: "keyword"]
      ],
      otherUuids: [
        match: "*Uuid",
        match_mapping_type: "string",
        mapping: [type: "keyword"]
      ],
      scope: [
        match: "scope",
        match_mapping_type: "string",
        mapping: [type: "keyword"]
      ],
      contentType: [
        match: "contentType",
        match_mapping_type: "string",
        mapping: [type: "keyword"]
      ],
      titleType: [
        match: "titleType",
        match_mapping_type: "string",
        mapping: [type: "keyword"]
      ]
    ]

    dynamic.each { k, v ->
      def mapObj = [:]
      mapObj[k] = v

      mapping.component.dynamic_templates << mapObj
    }

    return mapping
  }

  private def ensureClient() {

    if ( esclient == null ) {

      def es_cluster_name = grailsApplication.config?.gokb?.es?.cluster ?: 'elasticsearch'
      def es_host_name = grailsApplication.config?.eshost ?: 'localhost'

      log.debug("esclient is null, creating now... host: ${grailsApplication.config?.eshost} cluster:${es_cluster_name}");

      log.debug("Looking for es on host ${es_host_name} with cluster name ${es_cluster_name}");

      Settings settings = Settings.builder().put("cluster.name", es_cluster_name).build();
      esclient = new org.elasticsearch.transport.client.PreBuiltTransportClient(settings);
      esclient.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(es_host_name), 9300));

      log.debug("ES wrapper service init completed OK");
    }

    esclient
  }

  def index(index,typename,record_id,record) {
    log.debug("index... ${typename},${record_id},...");
    def result=null;
    try {
      def future = ensureClient().prepareIndex(index,typename,record_id).setSource(record)
      result=future.get()
    }
    catch ( Exception e ) {
      log.error("Error processing ${toJson(record)}",e);
      e.printStackTrace()
    }
    log.debug("Index complete");
    result
  }


  def getClient() {
    return ensureClient()
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
    esclient.close()
  }

}
