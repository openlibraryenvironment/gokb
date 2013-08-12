package org.gokb

import grails.converters.JSON
import grails.plugins.springsecurity.Secured
import org.codehaus.groovy.grails.commons.*
import org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib

class CreateController {

  def genericOIDService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() { 
    log.debug("Create... ${params}");
    def result=[:]

    // Create a new empty instance of the object to create
    result.newclassname=params.tmpl
    if ( params.tmpl ) {
      def newclass = grailsApplication.getArtefact("Domain",result.newclassname);
      if ( newclass ) {
        try {
          result.displayobj = newclass.newInstance()
  
          if ( params.tmpl )
            result.displaytemplate = grailsApplication.config.globalDisplayTemplates[params.tmpl]
        }
        catch ( Exception e ) {
          log.error("Problem",e);
        }
      }
    }

    result
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def process() {
    def result=['responseText':'OK']
    log.debug("create::process params - ${params}");
    log.debug("create::process request - ${request}");
    if ( params.cls ) {
      def newclass = grailsApplication.getArtefact("Domain",params.cls)
      if ( newclass ) {
        try {
          result.newobj = newclass.newInstance()

          params.each { p ->
            log.debug("Consider ${p.key} -> ${p.value}");
            if ( newclass.hasPersistentProperty(p.key) ) {
              GrailsDomainClassProperty pdef = newclass.getPersistentProperty(p.key) 
              log.debug(pdef);
              if ( pdef.association ) {
                if ( pdef.isOneToOne() ) {
                  log.debug("one-to-one");
                  def related_item = genericOIDService.resolveOID(p.value);
                  result.newobj[p.key] = related_item
                }
                else if ( pdef.isManyToOne() ) {
                  log.debug("many-to-one");
                  def related_item = genericOIDService.resolveOID(p.value);
                  result.newobj[p.key] = related_item
                }
                else {
                  log.debug("unhandled association type");
                }
              }
              else {
                log.debug("Scalar property");
                result.newobj[p.key] = p.value
              }
            }
            else {
              log.debug("Persistent class has no property named ${p.key}");
            }
          }
          log.debug("Completed setting properties");

          if ( !result.newobj.save(flush:true) ) {
            log.error("Problem saving new object");
            result.newobj.errors.each { e ->
              log.error(e)
            }
            flash.message="Problem..."
            flash.error="Problem..."
            result.uri = new ApplicationTagLib().createLink([controller: 'create', action:'index', params:[tmpl:params.cls]])
            // render view: 'index', model: [d: result.newobj]
          }
          else {
            result.uri = new ApplicationTagLib().createLink([controller: 'resource', action:'show', id:"${params.cls}:${result.newobj.id}"])
          }
        }
        catch ( Exception e ) {
          log.error("Problem",e);
        }
      }
    }
    render result as JSON
  }
}
