package org.gokb.cred

import groovy.util.logging.*


@Slf4j
class WebHookEndpoint {
  String name
  String url
  Long authmethod //legacy
  RefdataValue supplyMethod //legacy
  // RefdataValue transferMethod //rausnehmen - nur in Source
  String principal //legacy
  String credentials //legacy
  User owner
  String ba_username
  String ba_password

  static mapping = {
    url column:'ep_url'
    authmethod column:'ep_authmethod'
    principal column:'ep_prin'
    credentials column:'ep_cred'
  }

  static constraints = {
    name(nullable:false, blank:false)
    url(nullable:false, blank:false)
    authmethod(nullable:true, blank:true)
    principal(nullable:true, blank:true)
    credentials(nullable:true, blank:true)
    supplyMethod(nullable:true, blank:true)
    ba_username(nullable:true, blank:true)
    ba_password(nullable:true, blank:true)
  }

  static def refdataFind(params) {

    log.debug("refdataFind(${params})");

    def result = [];
    def ql = null;
    def qp = [qry: "%${params.q}%" ]

    def query = "select e from WebHookEndpoint as e where lower(e.url) like :qry"

    if ( params.filter1 ) {
      query += " and e.owner.id = :owner"
      qp.put('owner', Long.parseLong(params.filter1))
    }

    ql = WebHookEndpoint.executeQuery(query,qp,params)

    if ( ql ) {
      ql.each { t ->
        result.add([id:"${t.class.name}:${t.id}",text:"${t.name} (${t.url})"])
      }
    }

    result
  }

}
