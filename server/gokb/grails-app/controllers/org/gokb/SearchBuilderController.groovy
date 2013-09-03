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
    ]

    if ( ( params.qbeClassName != null ) && ( params.qbeClassName.length() > 0 ) ) {
      def dc = grailsApplication.getArtefact('Domain',params.qbeClassName)
      if ( dc != null ) {
        dc.getPersistentProperties().each { p -> 
          log.debug("${p.name} (assoc=${p.isAssociation()}) (oneToMany=${p.isOneToMany()}) (ManyToOne=${p.isManyToOne()}) (OneToOne=${p.isOneToOne()})");
          if ( p.isAssociation() ) {
            if ( p.isManyToOne() || p.isOneToOne() ) {
              result.add([ title: "${p.name} (${p.getReferencedDomainClass().fullName})", 
                           isLazy:true, 
                           key: params.key?:''+"."+p.name,
                           qbeClassName:p.getReferencedDomainClass().fullName ])
            }
            else {
              result.add([ title: "${p.name} (${p.getReferencedDomainClass().fullName})", 
                           isLazy:true, 
                           key: params.key?:''+"."+p.name,
                           qbeClassName:p.getReferencedDomainClass().fullName ])
            }
          }
          else {
            result.add([ title:p.name, isLazy:false, key: params.key?:''+"."+p.name ])
          }
        }
      }
    }

    render result as JSON
  }
}
