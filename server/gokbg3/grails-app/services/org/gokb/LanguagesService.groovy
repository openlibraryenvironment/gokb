package org.gokb

import grails.gorm.transactions.Transactional
import grails.util.Holders
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.RESTClient
import org.gokb.cred.KBComponent
import org.gokb.cred.RefdataCategory

import static groovyx.net.http.Method.GET

@Transactional
class LanguagesService{

  static Map localesToLanguages = [
      "DE" : "ger",
      "EN" : "eng",
      "FR" : "fre"
  ]

  static Map languages = [:]

  
  static void initialize(){
    String uriString = "${Holders.grailsApplication.config.gokb.languagesUrl}/api/listIso639two"
    URI microserviceUrl = new URI(uriString)
    def httpBuilder = new HTTPBuilder(microserviceUrl)
    httpBuilder.request(GET) { request ->
      response.success = { statusResp, responseData ->
        log.debug("GET ${uriString} => success")
        languages = responseData
      }
      response.failure = { statusResp, statusData ->
        log.debug("GET ${uriString} => failure => will be showing languages as shortcodes")
        return
      }
    }
    for (def entry in languages){
      RefdataCategory.lookupOrCreate(KBComponent.RD_LANGUAGE, entry.key, entry.key)
    }
  }

}
