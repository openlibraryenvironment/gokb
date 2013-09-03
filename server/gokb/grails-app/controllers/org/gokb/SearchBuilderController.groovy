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

  def getClassProperties() {
    log.debug("getClassProperties(${params})");
    def result=[
      [ title:'Prop1', isLazy:true, key: 'XX11' ],
      [ title:'Prop2', isLazy:true, key: 'XX12' ],
      [ title:'Prop3', isLazy:true, key: 'XX13' ],
    ]

    render result as JSON
  }
}
