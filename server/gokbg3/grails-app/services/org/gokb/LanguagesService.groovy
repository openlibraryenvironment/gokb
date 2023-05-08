package org.gokb

import grails.gorm.transactions.Transactional
import grails.util.Holders

import groovy.json.JsonSlurper

import io.micronaut.http.*
import io.micronaut.http.client.*

import org.gokb.cred.KBComponent
import org.gokb.cred.RefdataCategory

@Transactional
class LanguagesService{

  static Map languages = [:]

  /**
   * Fills the RefdataCategory given by {@link org.gokb.cred.KBComponent.RD_LANGUAGE} with a list of all language codes
   * provided in the ISO-639-2 map specified by the referenced languages microservice. See
   * https://github.com/hbz/languages-microservice#get-the-whole-iso-639-2-list for details.
   */
  static void initialize(){
    if (Holders.grailsApplication.config.getProperty('gokb.languagesUrl')) {
      try {
        def client = HttpClient.create(Holders.grailsApplication.config.getProperty('gokb.languagesUrl').toURL()).toBlocking()
        def response = client.exchange("/api/listIso639two", Map)
      
        languages = response.body()
      }
      catch (Exception e) {
        e.printStackTrace()
      }
    }
    else {
      File languageFile = getClass().getResource("languages.json")?.getFile()

      if (languageFile)  {
        languages = new JsonSlurper().parse(languageFile)
      }
    }


    for (def entry in languages){
      RefdataCategory.lookupOrCreate(KBComponent.RD_LANGUAGE, entry.key, entry.key)
    }
  }


  Map getLanguages(){
    languages
  }

}
