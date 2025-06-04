package org.gokb

import grails.gorm.transactions.Transactional

import groovy.json.JsonSlurper

import io.micronaut.http.client.*

import org.gokb.cred.KBComponent
import org.gokb.cred.RefdataCategory

@Transactional
class LanguagesService{

  def grailsApplication

  static Map languages = [:]

  @Transactional
  public void initialize(){
    File languageFile = new File(getClass().getResource("${File.separator}languages${File.separator}languages.json").toURI())
    languages = new JsonSlurper().parse(languageFile)

    for (def entry in languages){
      RefdataCategory.lookupOrCreate(KBComponent.RD_LANGUAGE, entry.key, entry.key)
    }
  }

  Map getLanguages(){
    if (!languages) {
      initialize()
    }

    languages
  }

}
