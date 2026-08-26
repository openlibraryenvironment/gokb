package org.gokb.rest

import grails.converters.*
import grails.gorm.transactions.*
import grails.plugin.springsecurity.annotation.Secured

import java.time.Duration
import java.time.LocalDateTime

import org.gokb.cred.*

@Transactional(readOnly = true)
class IdentifierController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def ESSearchService
  def messageService
  def restMappingService
  def componentLookupService
  def identifierService

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    Map result = [:]
    String base = grailsApplication.config.getProperty('grails.serverURL') + "/rest"
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }
    LocalDateTime start_db = LocalDateTime.now()


    params['_embed'] = params['_embed'] ?: 'identifiedComponents'

    result = componentLookupService.restLookup(user, Identifier, params)
    log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")

    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def show() {
    Map result = [:]
    Identifier obj = null
    String base = grailsApplication.config.getProperty('grails.serverURL') + "/rest"
    boolean is_curator = true
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }

    if (params.oid || params.id) {
      obj = Identifier.findByUuid(params.id)

      if (!obj) {
        obj = Identifier.get(genericOIDService.oidToId(params.id))
      }

      if (obj) {

        params['_embed'] = params['_embed'] ?: 'identifiedComponents'

        result = restMappingService.mapObjectToJson(obj, params, user)

        // result['_currentTipps'] = obj.currentTippCount
        // result['_linkedOpenRequests'] = obj.getReviews(true,true).size()
      } else {
        result.message = "Object ID could not be resolved!"
        response.status = 404
        result.code = 404
        result.result = 'ERROR'
      }
    } else {
      result.result = 'ERROR'
      response.status = 400
      result.code = 400
      result.message = 'No object id supplied!'
    }

    render result as JSON
  }

  @Transactional
  @Secured(value = ["hasRole('ROLE_USER')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def save() {
    Map result = [:]
    Map errors = [:]
    User user = User.get(springSecurityService.principal.id)
    def reqBody = request.JSON
    log.debug("Save new Identifier: ${reqBody}")

    if ( reqBody?.value && reqBody?.namespace ) {
      IdentifierNamespace ns = null

      if (reqBody.namespace instanceof Integer) {
        ns = IdentifierNamespace.get(reqBody.namespace)
      }
      else if (reqBody.namespace instanceof String) {
        ns = IdentifierNamespace.findByValueIlike(reqBody.namespace)
      }

      if (ns) {
        Identifier obj = null

        try {
          obj = componentLookupService.lookupOrCreateCanonicalIdentifier(ns.value, reqBody.value, false)
        }
        catch (grails.validation.ValidationException ve) {
          log.debug("Identifier ${reqBody} has failed validation!")
          result.message = "Identifier has failed validation!"
          errors = messageService.processValidationErrors(ve.errors, request.locale)
        }
        catch (Exception e) {
          result.message = "Unable to create Identifier: ${e.cause}"
          response.status = 500
        }

        log.debug("After Identifier lookup: ${obj}")

        if (!obj) {
          log.debug("Could not create identifier!")
          result.message = "Unable to create identifier ${reqBody}"
          errors = [
            value: [
              [
                message: messageService.resolveCode('identifier.validation.generic', null, request.locale),
                baddata: reqBody.value,
                messageCode: 'identifier.validation.generic'
              ]
            ]
          ]
          response.status = 400
        }
        else if ( obj.hasErrors() ) {
          errors = messageService.processValidationErrors(obj.errors, request.locale)
          result.message = "Identifier failed validation!"
        }
        else {
          if (reqBody.component) {
            KBComponent comp = null

            if ( reqBody.component instanceof Integer ) {
              comp = KBComponent.get(reqBody.component)
            }
            else if ( reqBody.component instanceof String ) {
              comp = KBComponent.findByUuid(reqBody.component)
            }

            if (comp) {
              if ( comp?.isEditable() ) {
                comp.ids.add(obj)
                comp.save(flush:true)

                params['_embed'] = params['_embed'] ?: 'identifiedComponents'
                response.status = 201

                result = restMappingService.mapObjectToJson(obj, params, user)
                log.debug("Got mapped ID with component! ${result}")
              }
              else {
                result.message = "Access to object was denied!"
                response.status = 403
                result.code = 403
                result.result = 'ERROR'
              }
            }
            else {
              result.message = "Component could not be resolved!"
              result.badData = [component: reqBody.component]
              response.status = 400
              result.code = 400
              result.result = 'ERROR'
            }
          }
          else {
            result = restMappingService.mapObjectToJson(obj, params, user)
            response.setStatus(201)
            log.debug("Got mapped ID without component! ${result}")
          }
        }
      } else {
        result.message = "Namespace could not be resolved!"
        result.badData = [namespace: reqBody.namespace]
        response.status = 400
        result.code = 400
        result.result = 'ERROR'
      }
    } else {
      errors = [badData: reqBody, message: "Unable to save identifier!"]
    }

    if (errors) {
      result.result = 'ERROR'
      if (response.status == 200) {
        response.status = 400
      }
      result.error = errors
    }

    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def namespace() {
    Map result = identifierService.fetchNamespaces(params)

    render result as JSON
  }
}
