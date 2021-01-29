package org.gokb.rest

import grails.converters.*
import grails.core.GrailsClass
import grails.gorm.transactions.*
import grails.plugin.springsecurity.annotation.Secured

import groovyx.net.http.URIBuilder

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId

import org.gokb.cred.*
import org.gokb.GOKbTextUtils
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*

@Transactional(readOnly = true)
class TippController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def ESSearchService
  def messageService
  def restMappingService
  def componentLookupService

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    def result = [:]
    def base = grailsApplication.config.serverURL + "/rest"
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }
    def es_search = params.es ? true : false

    params.componentType = "TIPP" // Tells ESSearchService what to look for

    if (es_search) {
      params.remove('es')
      def start_es = LocalDateTime.now()
      result = ESSearchService.find(params)
      log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis();}")
    }
    else {
      def start_db = LocalDateTime.now()
      result = componentLookupService.restLookup(user, TitleInstancePackagePlatform, params)
      log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")
    }

    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def show() {
    def result = [:]
    def base = grailsApplication.config.serverURL + "/rest"
    def is_curator = true
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }

    if (params.oid || params.id) {
      def obj = TitleInstancePackagePlatform.findByUuid(params.id)

      if (!obj) {
        obj = TitleInstancePackagePlatform.get(genericOIDService.oidToId(params.id))
      }

      if (obj) {
        result = restMappingService.mapObjectToJson(obj, params, user)
      }
      else {
        result.message = "Object ID could not be resolved!"
        response.setStatus(404)
        result.code = 404
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
  @Secured(value=["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='POST')
  def save() {
    def result = ['result':'OK', 'params': params]
    def reqBody = request.JSON
    def errors = [:]
    def user = User.get(springSecurityService.principal.id)
    def pkg = null

    if (reqBody?.pkg) {
      pkg = Package.get(reqBody.pkg)
    }

    if (pkg) {
      def curator = pkg?.curatoryGroups?.size() > 0 ? user.curatoryGroups?.id.intersect(obj.pkg.curatoryGroups?.id) : true

      if (curator) {
        def tipp_validation = TitleInstancePackagePlatform.validateDTO(reqBody)

        if (tipp_validation.valid) {
          def obj = TitleInstancePackagePlatform.upsertDTO(reqBody, user)

          if( obj?.validate() ) {

            errors << updateCombos(obj, reqBody)

            if (errors.size() == 0) {
              result = restMappingService.mapObjectToJson(obj, params, user)
            }
          }
          else {
            result.result = 'ERROR'
            result.message = "There have been validation errors while creating the object!"
            response.setStatus(400)
            errors = messageService.processValidationErrors(obj.errors, request.locale)
          }
        }
        else {
          result.result = 'ERROR'
          response.setStatus(400)
          result.message = "There have been validation errors!"
          errors = tipp_validation.errors
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
      response.setStatus(400)
      result.message = "Package not found or empty request body!"
    }

    if (errors) {
      result.result = 'ERROR'
      result.error = errors
    }

    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def update() {
    def result = ['result':'OK', 'params': params]
    def reqBody = request.JSON
    def errors = [:]
    def user = User.get(springSecurityService.principal.id)
    def obj = TitleInstancePackagePlatform.findByUuid(params.id)

    if (!obj) {
      obj = TitleInstancePackagePlatform.get(genericOIDService.oidToId(params.id))
    }

    if (obj?.pkg && reqBody) {
      obj.lock()

      def curator = obj.pkg.curatoryGroups?.size() > 0 ? user.curatoryGroups?.id.intersect(obj.pkg.curatoryGroups?.id) : true

      if (curator || user.isAdmin()) {
        reqBody.title = obj.title.id
        reqBody.hostPlatform = obj.hostPlatform.id
        reqBody.pkg = obj.pkg.id

        def tipp_validation = TitleInstancePackagePlatform.validateDTO(reqBody)

        if (tipp_validation.valid) {
          def jsonMap = obj.jsonMapping

          obj = restMappingService.updateObject(obj, jsonMap, reqBody)

          errors << updateCombos(obj, reqBody)
          obj = updateCoverage(obj, reqBody)

          if( obj?.validate() ) {
            if(errors.size() == 0) {
              log.debug("No errors.. saving")
              obj = obj.merge(flush:true)
              result = restMappingService.mapObjectToJson(obj, params, user)
            }
            else {
              response.setStatus(400)
              result.message = message(code:"default.update.errors.message")
            }
          }
          else {
            result.result = 'ERROR'
            response.setStatus(400)
            errors = messageService.processValidationErrors(obj.errors, request.locale)
          }
        }
        else {
          result.result = 'ERROR'
          response.setStatus(400)
          result.message = "There have been validation errors!"
          errors = tipp_validation.errors
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
      result.error = errors
    }
    render result as JSON
  }

  @Transactional
  private def updateCombos(obj, reqBody, boolean remove = true) {
    log.debug("Updating title combos ..")
    def errors = [:]

    if (reqBody.ids instanceof Collection || reqBody.identifiers instanceof Collection) {
      def id_list = reqBody.ids instanceof Collection ? reqBody.ids : reqBody.identifiers

      def id_errors = restMappingService.updateIdentifiers(obj, id_list, remove)

      if (id_errors.size() > 0) {
        errors.ids = id_errors
      }
    }

    errors
  }

  private def updateCoverage(tipp, reqBody) {
    def cov_list = reqBody.coverageStatements ?: reqBody.coverage
    def missing = tipp.coverageStatements.collect { it.id }
    def changed = false

    cov_list?.each { c ->
      def parsedStart = GOKbTextUtils.completeDateString(c.startDate)
      def parsedEnd = GOKbTextUtils.completeDateString(c.endDate, false)

      changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'startVolume', c.startVolume)
      changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'startIssue', c.startIssue)
      changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'endVolume', c.endVolume)
      changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'endIssue', c.endIssue)
      changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'embargo', c.embargo)
      changed |= com.k_int.ClassUtils.setStringIfDifferent(tipp, 'coverageNote', c.coverageNote)
      changed |= com.k_int.ClassUtils.setDateIfPresent(parsedStart,tipp,'startDate')
      changed |= com.k_int.ClassUtils.setDateIfPresent(parsedEnd,tipp,'endDate')
      changed |= com.k_int.ClassUtils.setRefdataIfPresent(c.coverageDepth, tipp, 'coverageDepth', 'TitleInstancePackagePlatform.CoverageDepth')

      def cs_match = false
      def startAsDate = (parsedStart ? Date.from( parsedStart.atZone(ZoneId.systemDefault()).toInstant()) : null)
      def endAsDate = (parsedEnd ? Date.from( parsedEnd.atZone(ZoneId.systemDefault()).toInstant()) : null)

      tipp.coverageStatements?.each { tcs ->

        if ( !cs_match && (
            (c.id && tcs.id == c.id) ||
            (tcs.startVolume && tcs.startVolume == c.startVolume) ||
            (tcs.startDate && tcs.startDate == startAsDate) ||
            (!cs_match && !tcs.startVolume && !tcs.startDate && !tcs.endVolume && !tcs.endDate))
        ) {
            changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'startIssue', c.startIssue)
            changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'startVolume', c.startVolume)
            changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'endVolume', c.endVolume)
            changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'endIssue', c.endIssue)
            changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'embargo', c.embargo)
            changed |= com.k_int.ClassUtils.setStringIfDifferent(tcs, 'coverageNote', c.coverageNote)
            changed |= com.k_int.ClassUtils.setDateIfPresent(parsedStart,tcs,'startDate')
            changed |= com.k_int.ClassUtils.setDateIfPresent(parsedEnd,tcs,'endDate')

            cs_match = true
            missing.remove(tcs.id)
        }
        else if (cs_match) {
          log.debug("Matched new coverage ${c} on multiple existing coverages!")
        }
      }

      if (!cs_match) {

        def cov_depth = null

        if (c.coverageDepth instanceof String) {
          cov_depth = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', c.coverageDepth)
        }
        else if (c.coverageDepth instanceof Integer) {
          cov_depth = RefdataValue.get(c.coverageDepth)
        }
        else if (c.coverageDepth instanceof Map) {
          if (c.coverageDepth.id) {
            cov_depth = RefdataValue.get(c.coverageDepth.id)
          }
          else {
            cov_depth = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', (c.coverageDepth.name ?: c.coverageDepth.value))
          }
        }

        if (!cov_depth) {
          cov_depth = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', "Fulltext")
        }

        tipp.addToCoverageStatements('startVolume': c.startVolume, \
          'startIssue':c.startIssue, \
          'endVolume': c.endVolume, \
          'endIssue': c.endIssue, \
          'embargo':c.embargo, \
          'coverageDepth': cov_depth, \
          'coverageNote': c.coverageNote, \
          'startDate': startAsDate, \
          'endDate': endAsDate
        )
      }
    }
    if (cov_list) {
      missing.each {
        tipp.removeFromCoverageStatements(TIPPCoverageStatement.get(it))
      }
    }

    tipp
  }

  @Secured(value=["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='DELETE')
  @Transactional
  def delete() {
    def result = ['result':'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)
    def obj = TitleInstancePackagePlatform.findByUuid(params.id) ?: TitleInstancePackagePlatform.get(genericOIDService.oidToId(params.id))

    if ( obj?.pkg && obj.isDeletable() ) {
      def curator = obj.pkg.curatoryGroups?.size() > 0 ? user.curatoryGroups?.id.intersect(obj.pkg.curatoryGroups?.id) : true

      if ( curator || user.isAdmin() ) {
        obj.deleteSoft()
      }
      else {
        result.result = 'ERROR'
        response.setStatus(403)
        result.message = "User must belong to at least one curatory group of an existing package to make changes!"
      }
    }
    else if ( !obj || !obj.pkg ) {
      result.result = 'ERROR'
      response.setStatus(404)
      result.message = "Package not found or empty request body!"
    }
    else {
      result.result = 'ERROR'
      response.setStatus(403)
      result.message = "User is not allowed to delete this component!"
    }
    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='GET')
  @Transactional
  def retire() {
    def result = ['result':'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)
    def obj = TitleInstancePackagePlatform.findByUuid(params.id) ?: TitleInstancePackagePlatform.get(genericOIDService.oidToId(params.id))

    if ( obj?.pkg && obj.isEditable() ) {
      def curator = obj.pkg.curatoryGroups?.size() > 0 ? user.curatoryGroups?.id.intersect(obj.pkg.curatoryGroups?.id) : true

      if ( curator || user.isAdmin() ) {
        obj.retire()
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
      result.message = "TIPP or connected Package not found!"
    }
    else {
      result.result = 'ERROR'
      response.setStatus(403)
      result.message = "User is not allowed to edit this component!"
    }
    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='GET')
  def getCoverage() {
    def result = [:]
    def user = User.get(springSecurityService.principal.id)
    def context = "/tipps/" + params.id + "/coverage"
    def tipp = TitleInstancePackagePlatform.get(params.id)

    if (tipp) {
      params.owner = tipp.id
      result = componentLookupService.restLookup(user, TIPPCoverageStatement, params, context)
    }
    else {
      result.result = 'ERROR'
      response.setStatus(404)
      result.message = "TIPP not found!"
    }
    render result as JSON
  }
}
