package org.gokb

import groovy.json.JsonSlurper
import grails.gorm.transactions.Transactional
import grails.util.Holders
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.RESTClient
import org.gokb.cred.KBComponent
import org.gokb.cred.RefdataCategory

import static groovyx.net.http.Method.GET

@Transactional
class LanguagesService{

  static Map languages = [:]

  /**
   * Fills the RefdataCategory given by {@link org.gokb.cred.KBComponent.RD_LANGUAGE} with a list of all language codes
   * provided in the ISO-639-2 map specified by the referenced languages microservice. See
   * https://github.com/hbz/languages-microservice#get-the-whole-iso-639-2-list for details.
   */
  static void initialize(){

    if (Holders.grailsApplication.config.gokb.languagesUrl) {
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
    }
    else {
      File languageFile = new File(getClass().getResource(
          "${File.separator}languages.json").toURI())

      languages = new JsonSlurper().parse(languageFile)
    }


    for (def entry in languages){
      RefdataCategory.lookupOrCreate(KBComponent.RD_LANGUAGE, entry.key, entry.key)
    }
  }

}
