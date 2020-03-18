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

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() {
    def result = [:]
    def base = grailsApplication.config.serverURL + "/rest"
    User user = User.get(springSecurityService.principal.id)
    def start_db = LocalDateTime.now()

    result = componentLookupService.restLookup(user, Identifier, params)
    log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")

    render result as JSON
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def show() {
    def result = [:]
    def obj = null
    def base = grailsApplication.config.serverURL + "/rest"
    def is_curator = true
    User user = User.get(springSecurityService.principal.id)

    if (params.oid || params.id) {
      obj = Identifier.findByUuid(params.id)

      if (!obj) {
        obj = genericOIDService.resolveOID(params.id)
      }

      if (!obj && params.long('id')) {
        obj = Identifier.get(params.long('id'))
      }

      if (obj?.isReadable()) {
        result = restMappingService.mapObjectToJson(obj, params, user)

        // result['_currentTipps'] = obj.currentTippCount
        // result['_linkedOpenRequests'] = obj.getReviews(true,true).size()
      }
      else if (!obj) {
        result.message = "Object ID could not be resolved!"
        response.setStatus(404)
        result.code = 404
        result.result = 'ERROR'
      }
      else {
        result.message = "Access to object was denied!"
        response.setStatus(403)
        result.code = 403
        result.result = 'ERROR'
      }
    }
    else {
      result.result = 'ERROR'
      response.setStatus(400)
      result.code = 400
      result.message = 'No object id supplied!'
    }

    render result as JSON
  }

  @Transactional
  @Secured(value=["hasRole('ROLE_USER')", 'IS_AUTHENTICATED_FULLY'], httpMethod='POST')
  def save() {
    def result = [:]
    def reqBody = request.JSON
    def errors = []
    def user = User.get(springSecurityService.principal.id)

    if ( reqBody?.value && reqBody?.namespace && reqBody?.component ) {
			def ns = null

			if (reqBody.namespace instanceof Long) {
				ns = IdentifierNamespace.get(reqBody.namespace)
			}
			else if (reqBody.namespace instanceof String) {
				ns = IdentifierNamespace.findByValueIlike(reqBody.namespace)
			}

			if (ns) {
        Identifier obj = null
        try {
				  obj = Identifier.lookupOrCreateCanonicalIdentifier(namespace, reqBody.value, false)
        }
        catch (grails.validation.ValidationException ve) {
          errors = [badData: reqBody, message: message(code:'identifier.value.IllegalIDForm')]
        }

				if (!obj) {
					errors = [badData: reqBody, message: message(code: 'identifier.create.error')]
				}
				else if (obj?.errors) {
					errors = messsageService.processValidationErrors(obj.errors, request.locale)
				}
        else {
          KBComponent comp = null

          if ( reqBody.component instanceof Long ) {
            comp = KBComponent.get(reqBody.component)
          }
          else if ( reqBody.component instanceof String ) {
            comp = KBComponent.findByUuid(reqBody.component)
          }

          if (comp) {
            if ( comp?.isEditable() ) {
              comp.ids.add(obj)
              comp.save()

              result = restMappingService.mapObjectToJson(obj, [:], user)
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
            response.setStatus(404)
            result.code = 404
            result.result = 'ERROR'
          }
        }
			}
			else {
        result.message = "Namespace could not be resolved!"
        response.setStatus(404)
        result.code = 404
        result.result = 'ERROR'
			}
    }
    else {
      errors = [badData: reqBody, message:"Unable to save identifier!"]
    }

    if (errors) {
      result.result = 'ERROR'
      result.error = errors
    }

    result
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='DELETE')
  @Transactional
  def delete() {
    def result = ['result':'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)
    def obj = Identifier.findByUuid(params.id) ?: genericOIDService.resolveOID(params.id)
    def curator = obj.respondsTo('curatoryGroups') ? user.curatoryGroups?.id.intersect(pkg.curatoryGroups?.id) : true

    if ( obj && obj.isDeletable() ) {
      if ( curator || user.isAdmin() ) {
        obj.deleteSoft()
      }
      else {
        result.result = 'ERROR'
        response.setStatus(403)
        result.message = "User must belong to at least one curatory group of an existing package to make changes!"
      }
    }
    else if (!obj) {
      result.result = 'ERROR'
      response.setStatus(404)
      result.message = "Package not found or empty request body!"
    }
    else {
      result.result = 'ERROR'
      response.setStatus(403)
      result.message = "User is not allowed to delete this component!"
    }
    result
  }
}
