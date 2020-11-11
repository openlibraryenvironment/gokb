package org.gokb

import grails.converters.JSON
import org.springframework.security.access.annotation.Secured;
import org.gokb.cred.*
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*
import grails.core.GrailsClass
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime

class CreateController {

  def genericOIDService
  def classExaminationService
  def springSecurityService
  def displayTemplateService
  def messageSource

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
            result.displaytemplate = displayTemplateService.getTemplateInfo(params.tmpl)

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
    log.debug("CreateController::process... ${params}");

    def result=['responseText':'OK']


    // II: Defaulting this to true - don't like it much, but we need to be able to create a title without any
    // props being set... not ideal, but issue closing.
    boolean propertyWasSet = true

    User user = springSecurityService.currentUser

    if ( params.cls ) {

      GrailsClass newclass = grailsApplication.getArtefact("Domain",params.cls)
      PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(params.cls)
      // def refdata_properties = classExaminationService.getRefdataPropertyNames(params.cls)
      log.debug("Got entity ${pent} for ${newclass.name}")

      if ( newclass ) {
        try {
          result.newobj = newclass.newInstance()
          log.debug("got newInstance...");

          params.each { p ->
            log.debug("Consider ${p.key} -> ${p.value}");
            if ( pent.getPropertyByName(p.key) ) {
              // THis deffo didn't work :( if ( newclass.metaClass.hasProperty(p.key) ) {

              // Ensure that blank values actually null the value instead of trying to use an empty string.
              if (p.value == "") p.value = null

              PersistentProperty pprop = pent.getPropertyByName(p.key)

              if ( pprop instanceof Association ) {
                if ( pprop instanceof OneToOne) {
                  log.debug("one-to-one");
                  def related_item = null

                  related_item = genericOIDService.resolveOID(p.value);

                  if (!related_item && pprop.getType().name == 'org.gokb.cred.RefdataValue') {
                    def rdc = classExaminationService.deriveCategoryForProperty(params.cls, p.key)
                    related_item = RefdataCategory.lookup(rdc, p.value)
                  }

                  result.newobj[p.key] = related_item
                  propertyWasSet = propertyWasSet || (related_item != null)
                }
                else if ( pprop instanceof ManyToOne ) {
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
                if ( pprop.getType().name == 'java.lang.String' ) {
                  result.newobj[p.key] = p.value?.trim() ?: null
                }
                else if ( pprop.getType().name == 'java.util.Date' ) {
                  def sdf = new java.text.SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss z", Locale.ENGLISH);
                  def incoming = p.value.substring(0,31) + ":" + p.value.substring(31, 33)
                  Instant instant = sdf.parse(incoming).toInstant()
                  LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.of("GMT"))

                  result.newobj[p.key] = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant())
                }
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

          // Add an error message here if no property was set via data sent through from the form.
          if (!propertyWasSet) {
            log.debug("No properties set");
            flash.error="Please fill in at least one piece of information to create the component."
            result.uri = g.createLink([controller: 'create', action:'index', params:[tmpl:params.cls]])
          } else {

            log.debug("Saving..");
            if ( !result.newobj.validate() ) {
              flash.error = []

              result.newobj.errors.allErrors.each { eo ->

                String[] messageArgs = eo.getArguments()
                def errorMessage = null

                log.debug("Found error with args: ${messageArgs}")

                eo.getCodes().each { ec ->

                  if (!errorMessage) {
                    // log.debug("testing code -> ${ec}")

                    def msg = messageSource.resolveCode(ec, request.locale)?.format(messageArgs)

                    if(msg && msg != ec) {
                      errorMessage = msg
                    }

                    if(!errorMessage) {
                      // log.debug("Could not resolve message")
                    }else{
                      log.debug("found message: ${msg}")
                    }
                  }
                }

                if (errorMessage) {
                  flash.error.add(errorMessage)
                }else{
                  log.debug("No message found for ${eo.getCodes()}")
                }
              }

              if ( flash.error.size() == 0 ) {
                flash.error.add("There has been an error creating the component. Please try again.")
              }

              result.errors = flash.error

              result.uri = createLink([controller: 'create', action:'index', params:[tmpl:params.cls]])
            } else {
              result.newobj.save(flush:true)

              log.debug("Setting combos..");

              if (result.newobj instanceof KBComponent) {
                // The save completed OK.. if we want to be really cool, we can now loop through the properties
                // and set any combos on the object
                boolean changed=false
                params.each { p ->
                  def combo_properties = result.newobj.getComboTypeValue(p.key)

                  if ( combo_properties != null ) {
                    log.debug("Deal with a combo doodah ${p.key}:${p.value}");
                    if ( ( p.value != "") && ( p.value != null ) ) {
                      def related_item = genericOIDService.resolveOID(p.value);
                      result.newobj[p.key] = related_item
                      changed = true
                    }
                  }
                  result.newobj.save(flush:true)
                }
              }

              result.uri = createLink([controller: 'resource', action:'show', id:"${params.cls}:${result.newobj.id}"])
            }
          }
        }
        catch ( Exception e ) {
          log.error("Problem",e);
          flash.error = "Could not create component!"
        }
      }
    }
    log.debug("CreateController::process return ${result}");

    render result as JSON
  }
}
