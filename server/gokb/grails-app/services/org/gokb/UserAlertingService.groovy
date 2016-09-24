package org.gokb

import org.elasticsearch.client.Client
import org.elasticsearch.node.Node
import static org.elasticsearch.node.NodeBuilder.nodeBuilder
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.groovy.*
import org.elasticsearch.common.transport.InetSocketTransportAddress

class UserAlertingService {

  static transactional = false

  def grailsApplication

  @javax.annotation.PostConstruct
  def init() {
    log.debug("UserAlertingService::init");
  }

  def sendAlertingEmail(user) {
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
  }

}
