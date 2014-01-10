package org.gokb.cred

class WebHookEndpoint {
  String url
  Long authmethod
  String principal
  String credentials

  static mapping = {
    url column:'ep_url'
    authmethod column:'ep_authmethod'
    principal column:'ep_prin'
    credentials column:'ep_cred'
  }

  static constraints = {
    url(nullable:false, blank:false)
    authmethod(nullable:true, blank:true)
    principal(nullable:true, blank:true)
    credentials(nullable:true, blank:true)
  }

}
