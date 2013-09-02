package org.gokb

import grails.converters.JSON


class SearchBuilderController {

  def index() { 
  }

  def getClasses() {
    // grailsApplication.getArtefacts("Domain")*.clazz

    def result=[
      values:[
      ]
    ]

    grailsApplication.getArtefacts("Domain").each { c ->
      result.values.add([id:c.fullName,text:c.fullName])
    }

    render result as JSON
  }
}
