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
class PackageController {

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
    def minResponse = ['id','label']

    params.componentType = params.componentType ?: "Package" // Tells ESSearchService what to look for

    def start_es = LocalDateTime.now()
    def es_result = ESSearchService.find(params)
    log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis();}")

    def start_db = LocalDateTime.now()
    def db_result = componentLookupService.restLookup(Package, params)
    log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")

    result = db_result

    result.data?.each { pkg ->
      pkg['_links'] << ['tipps': ['href': (base + "/packages/${pkg.uuid}/tipps")]]
    }

    render result as JSON
  }

  @Transactional
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def show() {
    def result = [:]
    def obj = null
    def base = grailsApplication.config.serverURL + "/rest"
    def is_curator = true
    User user = User.get(springSecurityService.principal.id)

    if (params.oid || params.id) {
      obj = Package.findByUuid(params.id)

      if (!obj) {
        obj = genericOIDService.resolveOID(params.id)
      }

      if (!obj) {
        obj = Package.get(params.id)
      }

      if (obj?.isReadable()) {
        result = restMappingService.mapObjectToJson(obj, params)

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

      if ( obj.curatoryGroups && obj.curatoryGroups?.size() > 0 ) {
        is_curator = user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)
      }

      if (is_curator) {
        result._links.update = ['href': base + obj.restPath + "/${obj.uuid}"]
        result._links.delete = ['href': base + obj.restPath + "/${obj.uuid}"]
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

  @Secured(value=["hasRole('ROLE_USER')", 'IS_AUTHENTICATED_FULLY'], httpMethod='POST')
  def save() {
    def result = ['result':'OK', 'params': params]
    def reqBody = request.JSON
    def errors = []
    def user = User.get(springSecurityService.principal.id)

    if (reqBody) {
      Package pkg = Package.upsertDTO(reqBody, user)

      if (!pkg) {
        errors = [badData: reqBody, message:"Unable to save package!"]
      }
      else if (pkg?.errors) {
        errors = messsageService.processValidationErrors(pkg.errors, request.locale)
      }
      else {
        if (reqBody.identifiers) {
          pkg
        }
      }
    }
    else {
      errors = [badData: reqBody, message:"Unable to save package!"]
    }

    if (errors) {
      result.result = 'ERROR'
      result.errors = errors
    }

    result
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='PUT')
  @Transactional
  def update() {
    def result = ['result':'OK', 'params': params]
    def reqBody = request.JSON
    def errors = []
    def user = User.get(springSecurityService.principal.id)
    def editable = true
    def pkg = Package.findByUuid(params.id) ?: genericOIDService.resolveOID(params.id)

    if (pkg && reqBody) {
      if ( !user.hasRole('ROLE_ADMIN') && pkg.curatoryGroups && pkg.curatoryGroups.size() > 0 ) {
        def cur = user.curatoryGroups?.id.intersect(pkg.curatoryGroups?.id)

        if (!cur) {
          editable = false
        }
      }
      if (editable) {

        def jsonMap = pkg.jsonMapping

        jsonMap.ignore = [
          'lastProject',
          'bucketHash',
          'shortcode',
          'normname',
          'people',
          'status',
          'lastSeen',
          'additionalProperties',
          'updateBenchmark',
          'systemComponent',
          'provenance',
          'insertBenchmark',
          'componentHash',
          'prices',
          'subjects',
          'reference',
          'duplicateOf',
          'componentDiscriminator',
          'incomingCombos',
          'outgoingCombos'
        ]

        restMappingService.updateObject(pkg, jsonMap, reqBody)

        if (reqBody.identifiers) {
          restMappingService.updateIdentifiers(pkg, reqBody.identifiers)
        }

        if (reqBody.variantNames) {
          restMappingService.updateVariants(pkg, reqBody.variantNames)
        }

        if ( reqBody.status ) {
          if ( reqBody.status.value != 'Deleted' || pkg.isDeletable() ) {
            pkg.status = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
          }
        }

        if (reqBody.provider) {
          pkg.provider = Org.get(reqBody.provider.id)
        }

        if (reqBody.nominalPlatform) {
          pkg.nominalPlatform = Platform.get(reqBody.nominalPlatform.id)
        }

        if( pkg.validate() ) {
          if(errors.size() == 0) {
            pkg.save(flush:true)
          }
        }
        else {
          result.result = 'ERROR'
          response.setStatus(422)
          errors.addAll(messsageService.processValidationErrors(pkg.errors, request.locale))
        }
      }
      else {
        result.result = 'ERROR'
        response.setStatus(403)
        result.message = "User must belong to at least one curatory group of an existing package to make changes!"
      }
    }
    else {
      result.result = 'ERROR'
      response.setStatus(404)
      result.message = "Package not found or empty request body!"
    }

    if(errors.size() > 0) {
      result.errors = errors
    }
    render result as JSON
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def tipps() {
    def result = [:]
    log.debug("tipps :: ${params}")
    def pkgId = Package.findByUuid(params.id)?.id ?: null

    if (!pkgId) {
      try {
        pkgId = Package.get(genericOIDService.oidToId(params.id))?.id ?: null
      }
      catch (Exception e) {
      }
    }

    if (pkgId) {
      def context = "/packages/" + pkgId + "/tipps"
      def base = grailsApplication.config.serverURL + "/rest"
      def es_search = params.es ? true : false

      params.remove('id')
      params.remove('uuid')
      params.remove('es')

      def esParams = new HashMap(params)
      esParams.remove('componentType')
      esParams.componentType = "TIPP" // Tells ESSearchService what to look for
      esParams.tippPackage = pkgId

      log.debug("New ES params: ${esParams}")

      params.pkg = pkgId

      if (es_search) {
        def start_es = LocalDateTime.now()
        result = ESSearchService.find(esParams, context)
        log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis();}")
      }
      else {
        def start_db = LocalDateTime.now()
        result = componentLookupService.restLookup(TitleInstancePackagePlatform, params)
        log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")
      }
    }
    else {
      result.result = 'ERROR'
      result.message = 'Package id could not be resolved!'
      response.setStatus(404)
    }

    render result as JSON
  }
}
