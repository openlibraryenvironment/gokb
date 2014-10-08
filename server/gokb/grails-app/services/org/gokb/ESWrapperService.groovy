package org.gokb

import grails.util.Holders
import org.elasticsearch.groovy.node.GNode
import org.elasticsearch.groovy.node.GNodeBuilder
import static org.elasticsearch.groovy.node.GNodeBuilder.*

class ESWrapperService {

  static transactional = false


  def gNode = null;

  @javax.annotation.PostConstruct
  def init() {
    
    log.debug("Init");

    // System.setProperty("java.net.preferIPv4Stack","true");
    // log.debug("Attempting to create a transport client...");
    // Map<String,String> m = new HashMap<String,String>();
    // m.put("cluster.name","aggr");
    // Settings s = ImmutableSettings.settingsBuilder() .put(m).build();
    // TransportClient client = new TransportClient(s);

    def clus_nm = Holders.grailsApplication.config.gokb.es.cluster ?: "gokb"

    log.debug("Using ${clus_nm} as ES cluster name...");


    def nodeBuilder = new org.elasticsearch.groovy.node.GNodeBuilder()

    log.debug("Construct node settings");

    nodeBuilder.settings {
      node {
        client = true
      }
      cluster {
        name = clus_nm
      }
      http {
        enabled = false
      }
      discovery {
        zen {
          minimum_master_nodes=1
          ping {
            unicast {
              hosts = [ "localhost" ] 
            }
          }
        }
      }
    }

    log.debug("Constructing node...");
    gNode = nodeBuilder.node()

    // log.debug("Sending record to es");
    // def future = gNode.client.index {
    //   index "courses"
    //   type "course"
    //   id "1"
    //   source {
    //     test = "value"
    //     value1 = "value1"
    //     value2 = "value2"
    //   }
    // }
    // log.debug("waiting for response...");

    // log.debug("Indexed $future.response.index/$future.response.type/$future.response.id")

    log.debug("Init completed");
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
    gNode.close()
    log.debug("Destroy completed");
  }

  def getNode() {
    log.debug("getNode()");
    gNode
  }

}
