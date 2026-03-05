package org.gokb.cred

import groovy.util.logging.*
import org.hibernate.proxy.HibernateProxy


@Slf4j
class WebHookEndpoint {
  String name
  String url
  Long authmethod //legacy
  RefdataValue supplyMethod //legacy
  String principal //legacy
  String credentials //legacy
  User owner
  String epUsername
  String epPassword

  static mapping = {
    url column:'ep_url'
    authmethod column:'ep_authmethod'
    principal column:'ep_prin'
    credentials column:'ep_cred'
  }

  static constraints = {
    name(validator: { val, obj ->
      if (val && val.trim()) {
        List<WebHookEndpoint> dupes = WebHookEndpoint.findAllByNameIlike(val);

        if (dupes?.size() > 0 && dupes.any { it != obj }) {
          return ['notUnique']
        }
      } else {
        return ['notNull']
      }
    })
    url(validator: {val, obj ->
      if(val) {
        if(!val.startsWith("ftp://") && !val.startsWith("http://") && !val.startsWith("https://")){
          return ['webEndpointUrl.missingProtocol']
        }
      }
      else {
        return ['webEndpointUrl.notNull']
      }
    })
    authmethod(nullable:true, blank:true)
    principal(nullable:true, blank:true)
    credentials(nullable:true, blank:true)
    supplyMethod(nullable:true, blank:true)
    epUsername(nullable:true, blank:true)
    epPassword(nullable:true, blank:true)
  }

  static jsonMapping = [
          'ignore'       : [
                  'epPassword',
                  'epUsername',
                  'credentials',
                  'authmethod'
          ]
  ]

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

  @Override
  public boolean equals (Object o) {

    if (o != null) {
      if (o instanceof WebHookEndpoint) {
        return o.id == id
      }
    }

    return false
  }

}
