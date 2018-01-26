package org.gokb

import grails.converters.JSON
import org.springframework.security.access.annotation.Secured;
import org.codehaus.groovy.grails.commons.*
import org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib
import org.gokb.cred.*

class CreateController {

  def genericOIDService
  def classExaminationService
  def springSecurityService


  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() {
    log.debug("CreateControler::index... ${params}");
    def result=[:]
    User user = springSecurityService.currentUser

    // Create a new empty instance of the object to create
    result.newclassname=params.tmpl
    if ( params.tmpl ) {
      def newclass = grailsApplication.getArtefact("Domain",result.newclassname);
      if ( newclass ) {
        log.debug("Got new class");
        try {
          result.displayobj = newclass.newInstance()
          log.debug("Got new instance");

          if ( params.tmpl ) {
            result.displaytemplate = grailsApplication.config.globalDisplayTemplates[params.tmpl]

            /* Extras needed for the refdata */
            result.refdata_properties = classExaminationService.getRefdataPropertyNames(result.newclassname)
            result.displayobjclassname_short = result.displayobj.class.simpleName
            result.isComponent = (result.displayobj instanceof KBComponent)
          }
        }
        catch ( Exception e ) {
          log.error("Problem",e);
        }
      }
    }

    log.debug("index:: return");
    result
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def process() {
    log.debug("CreateControler::process... ${params}");

    def result=['responseText':'OK']
    

    // II: Defaulting this to true - don't like it much, but we need to be able to create a title without any
    // props being set... not ideal, but issue closing.
    boolean propertyWasSet = true

    User user = springSecurityService.currentUser

    if ( params.cls ) {

      def newclass = grailsApplication.getArtefact("Domain",params.cls)
      // def refdata_properties = classExaminationService.getRefdataPropertyNames(params.cls)

      if ( newclass ) {
        try {
          result.newobj = newclass.newInstance()
          log.debug("got newInstance...");

          params.each { p ->
            log.debug("Consider ${p.key} -> ${p.value}");
            if ( newclass.hasPersistentProperty(p.key) ) {
              // THis deffo didn't work :( if ( newclass.metaClass.hasProperty(p.key) ) {

              // Ensure that blank values actually null the value instead of trying to use an empty string.
              if (p.value == "") p.value = null

              GrailsDomainClassProperty pdef = newclass.getPersistentProperty(p.key)
              log.debug(pdef);
              if ( pdef.association ) {
                if ( pdef.isOneToOne() ) {
                  log.debug("one-to-one");
                  def related_item = genericOIDService.resolveOID(p.value);
                  result.newobj[p.key] = related_item
                  propertyWasSet = propertyWasSet || (related_item != null)
                }
                else if ( pdef.isManyToOne() ) {
                  log.debug("many-to-one");
                  def related_item = genericOIDService.resolveOID(p.value);
                  result.newobj[p.key] = related_item
                  propertyWasSet = propertyWasSet || (related_item != null)
                }
                else {
                  log.debug("unhandled association type");
                }
              }
              else {
                log.debug("Scalar property");
                result.newobj[p.key] = p.value
                propertyWasSet = propertyWasSet || (p.value != null)
              }
            }
            else {
              log.debug("Persistent class has no property named ${p.key}");
            }
          }
          log.debug("Completed setting properties");

          if ( result.newobj.hasProperty('postCreateClosure') ) {
            log.debug("Created object has a post create closure.. call it");
            result.newobj.postCreateClosure.call([user:user])
          }


          log.debug("Setting combos..");

          if (result.displayobj instanceof KBComponent) {
            // The save completed OK.. if we want to be really cool, we can now loop through the properties
            // and set any combos on the object
            boolean changed=false
            params.each { p ->
              if ( combo_properties != null && combo_properties.contains(p.key) ) {
                log.debug("Deal with a combo doodah ${p.key}:${p.value}");
                if ( ( p.value != "") && ( p.value != null ) ) {
                  def related_item = genericOIDService.resolveOID(p.value);
                  result.newobj[p.key] = related_item
                  changed = true
                  propertyWasSet = propertyWasSet || (related_item != null)
                }
              }
            }
          }

          // Add an error message here if no property was set via data sent through from the form.
          if (!propertyWasSet) {
            log.debug("No properties set");
            flash.error="Please fill in at least one piece of information to create the component."
            result.uri = g.createLink([controller: 'create', action:'index', params:[tmpl:params.cls]])
          } else {
          
            log.debug("Saving..");
            log.debug("Obj: ${result.newobj}")
            if ( result.newobj.hasErrors() ) {
              log.error("Problem saving new object")
              flash.error = "Problem saving new object!"
              
              log.error("${result.newobj.errors}")
              // render view: 'index', model: [d: result.newobj]
              result.newobj.errors.allErrors.each { e ->
                log.error(e)
              }
              
              result.uri = g.createLink([controller: 'create', action:'index', params:[tmpl:params.cls]])
            }else {
              result.newobj.save(flush:true)
              result.uri = new ApplicationTagLib().createLink([controller: 'resource', action:'show', id:"${params.cls}:${result.newobj.id}"])
            }
          }
        }
        catch ( Exception e ) {
          log.error("Problem",e);
        }
      }
    }
    log.debug("CreateController::process return");

    render result as JSON
  }
}
