package org.gokb.rest

import grails.converters.*
import grails.core.GrailsClass
import grails.gorm.transactions.*
import grails.plugin.springsecurity.annotation.Secured

import groovyx.net.http.URIBuilder

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.gokb.cred.*
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*

@Transactional(readOnly = true)
class IdentifierController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def ESSearchService
  def messageService
  def restMappingService
  def componentLookupService
  def targetTypeMap = [:]

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    def result = [:]
    def base = grailsApplication.config.serverURL + "/rest"
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }
    def start_db = LocalDateTime.now()


    params['_embed'] = params['_embed'] ?: 'identifiedComponents'

    result = componentLookupService.restLookup(user, Identifier, params)
    log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")

    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def show() {
    def result = [:]
    def obj = null
    def base = grailsApplication.config.serverURL + "/rest"
    def is_curator = true
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
        response.setStatus(404)
        result.code = 404
        result.result = 'ERROR'
      }
    } else {
      result.result = 'ERROR'
      response.setStatus(400)
      result.code = 400
      result.message = 'No object id supplied!'
    }

    render result as JSON
  }

  @Transactional
  @Secured(value = ["hasRole('ROLE_USER')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def save() {
    def result = [:]
    def reqBody = request.JSON
    def errors = [:]
    def user = User.get(springSecurityService.principal.id)
    log.debug("Save new Identifier: ${reqBody}")

    if ( reqBody?.value && reqBody?.namespace ) {
      def ns = null

      if (reqBody.namespace instanceof Integer) {
        ns = IdentifierNamespace.get(reqBody.namespace)
      }
      else if (reqBody.namespace instanceof String) {
        ns = IdentifierNamespace.findByValueIlike(reqBody.namespace)
      }

      if (ns) {
        Identifier obj = null

        try {
          obj = componentLookupService.lookupOrCreateCanonicalIdentifier(ns.value, reqBody.value,false)
        }
        catch (grails.validation.ValidationException ve) {
          log.debug("Identifier ${reqBody} has failed validation!")
          result.message = "Identifier has failed validation!"
          errors = messageService.processValidationErrors(ve.errors, request.locale)
        }
        catch (Exception e) {
          result.message = "Unable to create Identifier: ${e.cause}"
          response.setStatus(500)
        }

        log.debug("After Identifier lookup: ${obj}")

        if (!obj) {
          log.debug("Could not create identifier!")
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
                response.setStatus(201)

                result = restMappingService.mapObjectToJson(obj, params, user)
                log.debug("Got mapped ID with component! ${result}")
              }
              else {
                result.message = "Access to object was denied!"
                response.setStatus(403)
                result.code = 403
                result.result = 'ERROR'
              }
            }
            else {
              result.message = "Component could not be resolved!"
              result.badData = [component: reqBody.component]
              response.setStatus(400)
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
        response.setStatus(400)
        result.code = 400
        result.result = 'ERROR'
      }
    } else {
      errors = [badData: reqBody, message: "Unable to save identifier!"]
    }

    if (errors) {
      result.result = 'ERROR'
      if (response.status == 200) {
        response.setStatus(400)
      }
      result.error = errors
    }

    render result as JSON
  }

  @Secured(value = ["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'DELETE')
  @Transactional
  def delete() {
    def result = ['result': 'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)
    def obj = Identifier.findByUuid(params.id) ?: genericOIDService.resolveOID(params.id)
    def curator = obj.respondsTo('curatoryGroups') ? user.curatoryGroups?.id.intersect(pkg.curatoryGroups?.id) : true

    if (obj && obj.isDeletable()) {
      if (curator || user.isAdmin()) {
        obj.deleteSoft()
      } else {
        result.result = 'ERROR'
        response.setStatus(403)
        result.message = "User must belong to at least one curatory group of an existing package to make changes!"
      }
    } else if (!obj) {
      result.result = 'ERROR'
      response.setStatus(400)
      result.message = "Package not found or empty request body!"
    } else {
      result.result = 'ERROR'
      response.setStatus(403)
      result.message = "User is not allowed to delete this component!"
    }
    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def namespace() {
    if (targetTypeMap.size() == 0) {
      fillTargetMap()
    }
    def result = [_links: [:]]
    def data = []
    params << [_exclude:"_links"]
    def base = grailsApplication.config.serverURL + "/rest"
    List<IdentifierNamespace> nss = []
    if (params.targetType != null) {
      nss = IdentifierNamespace.findAllByTargetType(targetTypeMap[params.targetType])
      if (params.targetType in ['Book', 'Journal', 'Database', 'Other']) {
        IdentifierNamespace.findAllByTargetType(targetTypeMap['Title'])
          .each { ns -> nss << ns }
      } else if (params.targetType == 'Title') {
        IdentifierNamespace.findAllByTargetTypeInList([targetTypeMap['Book'], targetTypeMap['Journal'], targetTypeMap['Database'], targetTypeMap['Other']])
          .each { ns -> nss << ns }
      }
    } else {
      nss = IdentifierNamespace.all
    }

    if (params.q?.trim()) {
      nss = nss.collect { it.name.startsWith(params.q.trim()) }
    }

    nss.each { ns ->
      data << [
        name:ns.name,
        value:ns.value,
        id: ns.id,
        pattern: ns.pattern,
        family: ns.family
      ]
    }
    result.data=data
    result['_links']['self'] = ['href': base + "/identifier-namespaces"]
    render result as JSON
  }

  private void fillTargetMap() {
    RefdataValue.findAllByOwner(RefdataCategory.findByLabel('IdentifierNamespace.TargetType'))
      .each { refVal ->
        targetTypeMap.put((refVal.value), refVal)
      }
  }
}
