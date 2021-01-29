package org.gokb.rest

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService
import com.k_int.ConcurrencyManagerService.Job

import grails.converters.*
import grails.core.GrailsClass
import grails.gorm.transactions.*
import grails.plugin.springsecurity.annotation.Secured

import groovyx.net.http.URIBuilder

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import org.apache.commons.lang.RandomStringUtils
import org.gokb.GOKbTextUtils
import org.gokb.cred.*
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*
import org.springframework.web.servlet.support.RequestContextUtils

@Transactional(readOnly = true)
class PackageController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def ESSearchService
  def messageService
  def restMappingService
  def packageService
  def classExaminationService
  def componentLookupService
  def componentUpdateService
  def concurrencyManagerService
  def sessionFactory
  def reviewRequestService
  def titleLookupService
  def titleHistoryService

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    def result = [:]
    def base = grailsApplication.config.serverURL + "/rest"
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }
    def es_search = params.es ? true : false

    params.componentType = "Package" // Tells ESSearchService what to look for

    if (es_search) {
      params.remove('es')
      def start_es = LocalDateTime.now()
      result = ESSearchService.find(params)
      log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis();}")
    }
    else {
      def start_db = LocalDateTime.now()
      result = componentLookupService.restLookup(user, Package, params)
      log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")
    }

    result.data?.each { obj ->
      obj['_links'] << ['tipps': ['href': (base + "/packages/${obj.uuid}/tipps")]]
    }

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
      obj = Package.findByUuid(params.id)

      if (!obj) {
        obj = Package.get(genericOIDService.oidToId(params.id))
      }

      if (obj) {
        result = restMappingService.mapObjectToJson(obj, params, user)

        result['_tippCount'] = obj.currentTippCount
        // result['_linkedOpenRequests'] = obj.getReviews(true,true).size()
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
    def generateToken = params.generateToken ? params.boolean('generateToken') : (reqBody.generateToken ? true : false)
    def request_locale = RequestContextUtils.getLocale(request)
    UpdateToken update_token = null
    def errors = [:]
    def user = User.get(springSecurityService.principal.id)

    if (reqBody) {
      log.debug("Save package ${reqBody}")
      def pkg_validation = packageService.validateDTO(reqBody, request_locale)
      def obj = null

      if (pkg_validation.valid) {
        def lookup_result = packageService.restLookup(reqBody)

        if (lookup_result.to_create) {
          def normname = Package.generateNormname(reqBody.name)
          obj = new Package(name: reqBody.name, normname: normname).save(flush:true)
          log.debug("New Object ${obj}")
        }
        else {
          lookup_result.matches.each { id, errs ->
            errs.each { e ->
              if (!errors[e.field])
                errors[e.field] = []

              errors[e.field] << [message: e.message, baddata: e.value, matches: id]
            }
          }
        }

        if (lookup_result.to_create && !obj) {
          log.debug("Could not upsert object!")
          errors.object = [[baddata: reqBody, message:"Unable to save object!"]]
        }
        else if (obj?.hasErrors()) {
          log.debug("Object has errors!")
          errors << messageService.processValidationErrors(obj.errors, request_locale)
        }
        else if (obj) {
          def jsonMap = obj.jsonMapping

          jsonMap.immutable = [
            'userListVerifier',
            'listVerifiedDate',
            'listStatus'
          ]

          log.debug("Updating ${obj}")
          obj = restMappingService.updateObject(obj, jsonMap, reqBody)

          if( obj.validate() ) {
            if (errors.size() == 0) {
              log.debug("No errors.. saving")
              obj.save()

              if (reqBody.variantNames) {
                obj = restMappingService.updateVariantNames(obj, reqBody.variantNames)
              }

              if (generateToken) {
                String charset = (('a'..'z') + ('0'..'9')).join()
                def updateToken = RandomStringUtils.random(255, charset.toCharArray())
                update_token = new UpdateToken(pkg: obj, updateUser: user, value: updateToken).save(flush:true)
              }

              errors << updateCombos(obj, reqBody)

              if (errors.size() == 0) {
                log.debug("No errors: ${errors}")
                obj.save(flush:true)
                response.status = 201
                result = restMappingService.mapObjectToJson(obj, params, user)

                if (update_token) {
                  result.updateToken = update_token.value
                }
              }
              else {
                result.result = 'ERROR'
                log.debug("There were errors setting combo props!")
                obj.discard()
                result.error = errors
              }
            }
            else {
              result.result = 'ERROR'
              obj.discard()
              result.message = message(code:"default.create.errors.message")
              response.setStatus(400)
            }
          }
          else {
            result.result = 'ERROR'
            obj.discard()
            response.setStatus(400)
            errors << messageService.processValidationErrors(obj.errors, request_locale)
          }
        }
      }
      else {
        errors << pkg_validation.errors
      }
    }
    else {
      response.setStatus(400)
      errors.object = [[baddata: reqBody, message:"Unable to save package!"]]
    }

    if (errors.size() > 0) {
      result.result = 'ERROR'
      result.errors = errors

      if (response.status == 200) {
        response.setStatus(400)
      }
    }

    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_CONTRIBUTOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def update() {
    def result = ['result':'OK', 'params': params]
    def reqBody = request.JSON
    def errors = [:]
    def remove = (request.method == 'PUT')
    def generateToken = params.generateToken ? params.boolean('generateToken') : (reqBody.generateToken ? true : false)
    UpdateToken update_token = null
    def request_locale = RequestContextUtils.getLocale(request)
    def user = User.get(springSecurityService.principal.id)
    def editable = true
    def obj = Package.findByUuid(params.id)

    if (!obj) {
      obj = Package.get(genericOIDService.oidToId(params.id))
    }

    if (obj && reqBody) {
      if ( !user.hasRole('ROLE_ADMIN') && obj.curatoryGroups && obj.curatoryGroups.size() > 0 ) {
        def cur = user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)

        if (!cur) {
          editable = false
        }
      }
      if (editable) {

        def jsonMap = obj.jsonMapping

        jsonMap.ignore = [
          'lastProject',
        ]

        jsonMap.immutable = [
          'userListVerifier',
          'listVerifiedDate',
          'listStatus'
        ]

        obj = restMappingService.updateObject(obj, jsonMap, reqBody)

        if (reqBody.variantNames) {
          obj = restMappingService.updateVariantNames(obj, reqBody.variantNames, remove)
        }

        errors << updateCombos(obj, reqBody, remove)

        if( obj.validate() ) {
          if (generateToken) {
            String charset = (('a'..'z') + ('0'..'9')).join()
            def updateToken = RandomStringUtils.random(255, charset.toCharArray())

            if (obj.updateToken) {
              def currentToken = obj.updateToken
              obj.updateToken = null
              currentToken.delete(flush:true)
            }

            update_token = new UpdateToken(pkg: obj, updateUser: user, value: updateToken).save(flush:true)
          }

          if(errors.size() == 0) {
            log.debug("No errors.. saving")
            obj = obj.merge(flush:true)
            result = restMappingService.mapObjectToJson(obj, params, user)

            if (update_token) {
              result.updateToken = update_token.value
            }
          }
          else {
            response.setStatus(400)
            result.message = message(code:"default.update.errors.message")
          }
        }
        else {
          result.result = 'ERROR'
          response.setStatus(400)
          errors << messageService.processValidationErrors(obj.errors, request_locale)
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

  private def updateCombos(obj, reqBody, boolean remove = true) {
    log.debug("Updating package combos ..")
    def errors = [:]

    if (reqBody.ids instanceof Collection || reqBody.identifiers instanceof Collection) {
      def id_list = reqBody.ids instanceof Collection ? reqBody.ids : reqBody.identifiers

      def id_errors = restMappingService.updateIdentifiers(obj, id_list, remove)

      if (id_errors.size() > 0) {
        errors.ids = id_errors
      }
    }

    if (reqBody.curatoryGroups) {
      def cg_errors = restMappingService.updateCuratoryGroups(obj, reqBody.curatoryGroups, remove)

      if (cg_errors.size() > 0) {
        errors['curatoryGroups'] = cg_errors
      }
    }

    if (reqBody.listStatus) {
      def new_val = null

      if (reqBody.listStatus instanceof String) {
        new_val = RefdataCategory.lookup('Package.ListStatus', reqBody.listStatus)
      }
      else if (reqBody.listStatus instanceof Integer) {
        def rdv = RefdataValue.get(reqBody.listStatus)

        if (rdv.owner == RefdataCategory.findByLabel('Package.ListStatus')) {
          new_val = rdv
        }

        if (new_val && new_val != obj.listStatus) {
          obj.listStatus = new_val
        }

        if (new_val && new_val.value == 'Checked') {
          obj.listVerifiedDate = new Date()
        }
      }
    }

    if (reqBody.provider instanceof Integer) {
      def prov = null

      try {
        prov = Org.get(reqBody.provider)
      }
      catch (Exception e) {
      }

      if (prov) {
        if (!obj.hasErrors() && errors.size() == 0 && prov != obj.provider) {
          def current_combo = Combo.findByFromComponentAndToComponent(obj, prov)

          if (current_combo) {
            current_combo.delete(flush:true)
          }

          def combo_type = RefdataCategory.lookup('Combo.Type', 'Package.Provider')
          def new_combo = new Combo(fromComponent: obj, toComponent: prov, type: combo_type).save(flush:true)

          obj.refresh()
        }
      }
      else {
        errors.provider = [[message: "Could not find provider Org with id ${reqBody.provider}!", baddata: reqBody.provider]]
      }
    } else if (reqBody.provider == null) {
      obj.provider = null
    }

    if (reqBody.nominalPlatform != null || reqBody.platform != null) {
      def plt_id = reqBody.nominalPlatform ?: reqBody.platform
      def plt = null

      try {
        plt = Platform.get(plt_id)
      }
      catch (Exception e) {
      }

      if (plt) {
        if (!obj.hasErrors() && errors.size() == 0 && plt != obj.nominalPlatform) {
          def current_combo = Combo.findByFromComponentAndToComponent(obj, plt)

          if (current_combo) {
            current_combo.delete(flush:true)
          }

          def combo_type = RefdataCategory.lookup('Combo.Type', 'Package.NominalPlatform')
          def new_combo = new Combo(fromComponent: obj, toComponent: plt, type: combo_type).save(flush:true)

          obj.refresh()
        }
      }
      else {
        errors.nominalPlatform = [[message: "Could not find platform with id ${reqBody.nominalPlatform}!", baddata: plt_id]]
      }
    } else if (reqBody.nominalPlatform == null || reqBody.platform == null) {
      obj.nominalPlatform = null
    }

    if (reqBody.tipps) {
      reqBody.tipps.each { tipp_dto ->
        tipp_dto.pkg = obj.id
        def ti_errors = []

        if (tipp_dto.title && tipp_dto.title instanceof Map) {
          if (!tipp_dto.id) {
            try {
              def ti = TitleInstance.upsertDTO(tipp_dto.title)

              if (ti) {
                tipp_dto.title = ti.id
              }
            }
            catch (grails.validation.ValidationException ve) {
              log.error("ValidationException attempting to cross reference title",ve);
              valid_ti = false
              def validation_errors = [
                message: "Title ${tipp_dto.title?.name} failed validation!",
                baddata: tipp_dto.title,
                errors: messageService.processValidationErrors(ve.errors)
              ]
              ti_errors.add(validation_errors)
            }
            catch (org.gokb.exceptions.MultipleComponentsMatchedException mcme) {
              log.debug("Handling MultipleComponentsMatchedException")
              valid_ti = false
              ti_errors.add([baddata: tipp_dto.title, 'message': "Unable to uniquely match title ${tipp_dto.title?.name}, check duplicates for titles ${mcme.matched_ids}!", conflicts: mcme.matched_ids])
            }
          }
        }

        def tipp_validation = TitleInstancePackagePlatform.validateDTO(tipp_dto)

        if (ti_errors?.size > 0 || !tipp_validation.valid) {
          if (!errors.tipps) {
            errors.tipps = []
          }

          if (ti_errors?.size > 0) {
            errors.tipps << ti_errors
          }

          if(!tipp_validation.valid) {
            errors.tipps << tipp_validation.errors
          }
        }
        else {
          def upserted_tipp = TitleInstancePackagePlatform.upsertDTO(tipp_dto)

          if (upserted_tipp) {
            if (errors.size() == 0) {
              upserted_tipp = upserted_tipp?.save(flush: true)
            }
          }
          else {
            if (!errors.tipps) {
              errors.tipps = []
            }

            error.tipps << [[message: "Unable to reference TIPP!", baddata: tipp_dto]]
          }
        }
      }
    }
    errors
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def delete() {
    def result = ['result':'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)
    def obj = Package.findByUuid(params.id)

    if (!obj) {
      obj = Package.get(genericOIDService.oidToId(params.id))
    }

    if ( obj && obj.isDeletable() ) {
      def curator = user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)

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
    render result as JSON
  }

  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def retire() {
    def result = ['result':'OK', 'params': params]
    def user = User.get(springSecurityService.principal.id)
    def obj = Package.findByUuid(params.id)

    if (!obj) {
      obj = Package.get(genericOIDService.oidToId(params.id))
    }

    if ( obj && obj.isEditable() ) {
      def curator = user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)

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
      result.message = "Package not found or empty request body!"
    }
    else {
      result.result = 'ERROR'
      response.setStatus(403)
      result.message = "User is not allowed to edit this component!"
    }
    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def tipps() {
    def result = [:]
    User user = null

    if (springSecurityService.isLoggedIn()) {
      user = User.get(springSecurityService.principal?.id)
    }
    log.debug("tipps :: ${params}")
    def obj = Package.findByUuid(params.id)

    if (!obj) {
      obj = Package.get(genericOIDService.oidToId(params.id))
    }

    log.debug("TIPPs for Package: ${obj}")

    if (obj) {
      def context = "/packages/" + params.id + "/tipps"
      def base = grailsApplication.config.serverURL + "/rest"
      def es_search = params.es ? true : false

      params.remove('id')
      params.remove('uuid')
      params.remove('es')
      params.pkg = obj.id

      def esParams = new HashMap(params)
      esParams.remove('componentType')
      esParams.componentType = "TIPP" // Tells ESSearchService what to look for

      log.debug("New ES params: ${esParams}")
      log.debug("New DB params: ${params}")

      if (es_search) {
        def start_es = LocalDateTime.now()
        result = ESSearchService.find(esParams, context)
        log.debug("ES duration: ${Duration.between(start_es, LocalDateTime.now()).toMillis();}")
      }
      else {
        def start_db = LocalDateTime.now()
        result = componentLookupService.restLookup(user, TitleInstancePackagePlatform, params, context)
        log.debug("DB duration: ${Duration.between(start_db, LocalDateTime.now()).toMillis();}")
      }
    }
    else {
      result.result = 'ERROR'
      result.message = "Package id ${params.id} could not be resolved!"
      response.setStatus(404)
    }

    render result as JSON
  }

  @Transactional
  @Secured(value=["hasRole('ROLE_EDITOR')", 'IS_AUTHENTICATED_FULLY'], httpMethod='POST')
  def addTipps() {
    def result = [:]
    def errors = []
    def user = User.get(springSecurityService.principal.id)
    def context = "/packages/" + params.id + "/tipps"
    log.debug("addTipps :: ${params}")
    def obj = Package.findByUuid(params.id)
    def reqBody = request.JSON

    if (!obj) {
      obj = Package.get(genericOIDService.oidToId(params.id))
    }

    if (obj && reqBody) {

      def curator = user.curatoryGroups?.id.intersect(obj.curatoryGroups?.id)

      params.pkg = params.id

      if ( curator || user.isAdmin() ) {
        if (reqBody instanceof List) {
          def idx = 0

          reqBody.each { tipp ->
            def tipp_validation = TitleInstancePackagePlatform.validateDTO(tipp)

            if (tipp_validation.valid) {
              def tipp_obj = TitleInstancePackagePlatform.upsertDTO(tipp)

              if (!tipp_obj) {
                errors.add(['code': 400, 'message': "TIPP could not be created!", baddata: tipp, idx: idx])
              }
            } else {
              errors.add(['code': 400, 'message': "TIPP information is not valid!", baddata: tipp, idx: idx, errors:tipp_validation.errors])
            }
            idx++
          }

          if (errors.size() == 0) {
            result = componentLookupService.restLookup(user, TitleInstancePackagePlatform, params, context)
          }
          else {
            result.result = 'ERROR'
            response.setStatus(400)
            result.errors = errors
            result.message = "There have been errors creating TIPPs!"
          }
        } else {
          result.result = 'ERROR'
          response.setStatus(400)
          result.message = "Missing expected array of TIPPs!"
        }
      }
      else {
        result.result = 'ERROR'
        response.setStatus(403)
        result.message = "User must belong to at least one curatory group of an existing package to make changes!"
      }
    }
    else if (!reqBody) {
      result.result = 'ERROR'
      response.setStatus(400)
      result.message = "Missing JSON payload!"
    }
    else {
      result.result = 'ERROR'
      response.setStatus(400)
      result.message = "Missing ID for connected package!"
    }

    render result as JSON
  }

  @Transactional
  @Secured(value=["IS_AUTHENTICATED_ANONYMOUSLY"])
  def updateTipps() {
    def result = [ 'result' : 'OK' ]
    def async = params.async ? params.boolean('async') : true
    def update = request.method == 'PATCH' || (params.addOnly ? params.boolean('addOnly') : false)
    def request_locale = RequestContextUtils.getLocale(request)
    def force = params.force ? params.boolean('force') : false
    def rjson = request.JSON
    UpdateToken updateToken = null
    User request_user = null
    def obj = params.id ? (Package.findByUuid(params.id) ?: Package.get(params.id)) :null
    def fullsync = false

    log.debug("updateTipps (${request_locale})")

    if (obj) {
      if (springSecurityService.isLoggedIn()) {
        request_user = User.get(springSecurityService.principal.id)
      }
      else if (params.updateToken?.trim()) {
        updateToken = UpdateToken.findByValue(params.updateToken)

        if (updateToken) {
          request_user = updateToken.updateUser

          if (rjson.packageHeader) {
            rjson.packageHeader.uuid = updateToken.pkg.uuid
          }
        }
        else {
          log.error("Unable to reference update token!")
          result.message = "Unable to reference update token!"
          response.setStatus(400)
          result.result = "ERROR"
        }
      }
      else {
        response.setStatus(401)
      }

      if (params.fullsync == "true" && request_user?.adminStatus) {
        fullsync = true
      }

      if ( request_user ) {
        Job background_job = concurrencyManagerService.createJob { Job job ->
          def json = rjson
          def job_result = [:]
          def ctr = 0
          def errors = []

          Package.withNewSession { session ->
            def user = User.get(request_user.id)
            def the_pkg = Package.get(obj.id)
            def locale = request_locale

            job.ownerId = user.id

            try {
              def existing_tipps = []
              def valid = true
              Boolean curated_pkg = false;
              def is_curator = null;

              if (the_pkg) {
                if ( the_pkg.curatoryGroups && the_pkg.curatoryGroups?.size() > 0 ) {
                  is_curator = user.curatoryGroups?.id.intersect(the_pkg.curatoryGroups?.id)

                  if (is_curator?.size() == 1) {
                    job.groupId = is_curator[0]
                  }
                  else if (is_curator?.size() > 1) {
                    log.debug("Got more than one cg candidate!")
                    job.groupId = is_curator[0]
                  }

                  curated_pkg = true;
                }

                if ( is_curator || !curated_pkg  || (user.authorities.contains(Role.findByAuthority('ROLE_SUPERUSER') && force))) {
                  if ( the_pkg.tipps?.size() > 0 ) {
                    existing_tipps = the_pkg.tipps*.id
                    log.debug("Matched package has ${the_pkg.tipps.size()} TIPPs")
                  }

                  Map platform_cache = [:]
                  log.debug("\n\n\nPackage ID: ${the_pkg.id} / ${json.packageHeader}");

                  // Validate and upsert titles and platforms
                  json.eachWithIndex { tipp, idx ->
                    def titleName = null
                    def tipp_plt_dto = tipp.hostPlatform ?: tipp.platform

                    if (tipp.title instanceof Map) {
                      def title_validation = TitleInstance.validateDTO(tipp.title);
                      titleName = tipp.title.name
                      valid &= title_validation.valid

                      if ( title_validation && !title_validation.valid ) {
                        log.warn("Not valid after title validation ${tipp.title}");
                        def preval_errors = [
                          code: 400,
                          message: messageService.resolveCode('crossRef.package.tipps.error.title.preValidation', [tipp.title.name, title_validation.errors], locale),
                          baddata: tipp.title,
                          idx: idx,
                          errors: title_validation.errors
                        ]
                        errors.add(preval_errors)
                      }
                      else {
                        def valid_ti = true

                        TitleInstance.withNewSession {
                          def ti = null
                          def titleObj = tipp.title
                          def title_changed = false
                          def title_class_name = titleLookupService.determineTitleClass(titleObj)

                          try {
                            ti = titleLookupService.findOrCreate(
                              titleObj.name,
                              titleObj.publisher,
                              titleObj.identifiers,
                              user,
                              null,
                              title_class_name,
                              titleObj.uuid
                            )

                            if ( ti?.id && !ti.hasErrors() ) {
                              if ( titleObj.imprint ) {
                                if ( title.imprint?.name == titleObj.imprint ) {
                                  // Imprint already set
                                }
                                else {
                                  def imprint = Imprint.findByName(titleObj.imprint) ?: new Imprint(name:titleObj.imprint).save(flush:true, failOnError:true);
                                  title.imprint = imprint;
                                  title_changed = true
                                }
                              }

                              // Add the core data.
                              componentUpdateService.ensureCoreData(ti, titleObj, fullsync, user)

                              title_changed |= componentUpdateService.setAllRefdata ([
                                    'OAStatus', 'medium',
                                    'pureOA', 'continuingSeries',
                                    'reasonRetired'
                              ], titleObj, ti)

                              def pubFrom = GOKbTextUtils.completeDateString(titleObj.publishedFrom)
                              def pubTo = GOKbTextUtils.completeDateString(titleObj.publishedTo, false)

                              log.debug("Completed date publishedFrom ${titleObj.publishedFrom} -> ${pubFrom}")

                              title_changed |= ClassUtils.setDateIfPresent(pubFrom, ti, 'publishedFrom')
                              title_changed |= ClassUtils.setDateIfPresent(pubTo, ti, 'publishedTo')

                              if ( titleObj.historyEvents?.size() > 0 ) {
                                def he_result = titleHistoryService.processHistoryEvents(ti, titleObj, title_class_name, user, fullsync, locale)

                                if (he_result.errors) {
                                  result.errors = he_result.errors
                                }
                              }

                              if( title_class_name == 'org.gokb.cred.BookInstance' ){

                                log.debug("Adding Monograph fields for ${ti.class.name}: ${ti}")
                                def mg_change = addMonographFields(ti, titleObj)

                                // TODO: Here we will have to add authors and editors, like addPerson() in TSVIngestionService
                                if(mg_change){
                                  title_changed = true
                                }
                              }

                              titleLookupService.addPublisherHistory(ti, titleObj.publisher_history)

                              ti.save(flush:true)

                              tipp.title.internalId = ti.id
                            } else {
                              def errorObj = ['code': 400, 'message': messageService.resolveCode('crossRef.package.tipps.error.title', tipp.title.name, locale), 'baddata': tipp.title]
                              if (ti != null) {
                                errorObj.errors = messageService.processValidationErrors(ti.errors)
                                errors.add(errorObj)
                                ti.discard()
                              }
                              valid_ti = false
                              valid = false
                            }
                          }
                          catch (grails.validation.ValidationException ve) {
                            log.error("ValidationException attempting to cross reference title",ve);
                            valid_ti = false
                            valid = false
                            def validation_errors = [
                              code: 400,
                              message: messageService.resolveCode('crossRef.package.tipps.error.title.validation', [tipp?.title?.name], locale),
                              baddata: tipp,
                              idx: idx,
                              errors: messageService.processValidationErrors(ve.errors)
                            ]
                            errors.add(validation_errors)
                          }
                          catch (org.gokb.exceptions.MultipleComponentsMatchedException mcme) {
                            log.debug("Handling MultipleComponentsMatchedException")
                            valid = false
                            errors.add(['code': 400, idx: idx, 'message': messageService.resolveCode('crossRef.title.error.multipleMatches', [tipp?.title?.name, mcme.matched_ids], locale)])
                          }
                        }

                        if ( valid_ti && tipp.title.internalId == null ) {
                          log.error("Failed to locate a title for ${tipp?.title} when attempting to create TIPP");
                          valid = false
                          errors.add(['code': 400, idx: idx, 'message': messageService.resolveCode('crossRef.package.tipps.error.title', [tipp?.title?.name], locale)])
                        }
                      }
                    }
                    else {
                      TitleInstance.withNewSession {
                        def ti = TitleInstance.get(tipp.title)
                        tipp.title = [name: ti.name, id: ti.id]
                        titleName = ti.name
                      }
                    }

                    if (tipp_plt_dto instanceof Map) {
                      def valid_plt = Platform.validateDTO(tipp_plt_dto);
                      valid &= valid_plt?.valid

                      if ( !valid_plt.valid ) {
                        log.warn("Not valid after platform validation ${tipp_plt_dto}");

                        def plt_errors = [
                          code: 400,
                          idx: idx,
                          message: messageService.resolveCode('crossRef.package.tipps.error.platform.preValidation', [tipp_plt_dto?.name], locale),
                          baddata: tipp_plt_dto,
                          errors: valid_plt.errors
                        ]
                        errors.add([])
                      }

                      if ( valid ) {

                        def pl = null
                        def pl_id
                        if (platform_cache.containsKey(tipp_plt_dto.name) && (pl_id = platform_cache[tipp_plt_dto.name]) != null) {
                          pl = Platform.get(pl_id)
                        } else {
                          // Not in cache.
                          try {
                            pl = Platform.upsertDTO(tipp_plt_dto, user);

                            if(pl){
                              platform_cache[tipp_plt_dto.name] = pl.id

                              componentUpdateService.ensureCoreData(pl, tipp_plt_dto, fullsync)
                            }else{
                              log.error("Could not find/create ${tipp_plt_dto}")
                              errors.add(['code': 400, idx: idx, 'message': messageService.resolveCode('crossRef.package.tipps.error.platform', [tipp_plt_dto.name], locale)])
                              valid = false
                            }
                          }
                          catch (grails.validation.ValidationException ve) {
                            log.error("ValidationException attempting to cross reference title",ve);
                            valid_plt = false
                            valid = false

                            def plt_errors = [
                              code: 400,
                              message: messageService.resolveCode('crossRef.package.tipps.error.platform.validation', [tipp_plt_dto], locale),
                              baddata: tipp_plt_dto,
                              idx: idx,
                              errors: messageService.processValidationErrors(ve.errors)
                            ]
                            errors.add(plt_errors)
                          }
                        }

                        if ( pl && ( tipp_plt_dto.internalId == null ) ) {
                          tipp_plt_dto.internalId = pl.id;
                        }
                        else {
                          log.warn("No platform arising from ${tipp_plt_dto}");
                        }
                      }
                    }

                    if ( ( tipp.package == null ) && ( the_pkg.id ) ) {
                      tipp.package = [ internalId: the_pkg.id ]
                    }
                    else {
                      log.warn("No package");
                      errors.add(['code': 400, idx: idx, 'message': messageService.resolveCode('crossRef.package.tipps.error.pkgId', [titleName], locale)])
                      valid = false
                    }

                    if (idx % 50 == 0) {
                      cleanUpGorm(session)
                    }
                    job.setProgress(idx, json.size() * 2)
                  }
                }
                else{
                  valid = false
                  log.warn("Package update denied!")
                  job_result.result = 'ERROR'
                  job_result.message = messageService.resolveCode('crossRef.package.error.denied', [the_pkg.name], locale)
                  return job_result
                }


                int tippctr=0;
                if ( valid ) {
                  // If valid so far, validate tipps
                  log.debug("Validating tipps [${tippctr++}]");
                  json.eachWithIndex { tipp, idx ->
                    def validation_result = TitleInstancePackagePlatform.validateDTO(tipp)

                    if ( validation_result && !validation_result.valid ) {
                      log.debug("TIPP Validation failed on ${tipp}")
                      valid = false
                      def tipp_errors = [
                        'code': 400,
                        idx: idx,
                        message: messageService.resolveCode('crossRef.package.tipps.error.preValidation', [tipp.title.name, validation_result.errors], locale),
                        baddata: tipp,
                        errors: validation_result.errors
                      ]
                      errors.add(tipp_errors)
                    }

                    if (idx % 50 == 0) {
                      cleanUpGorm(session)
                    }
                  }
                }
                else {
                  log.warn("Not validating tipps - failed pre validation")
                }

                if ( valid ) {
                  log.debug("\n\nupsert tipp data\n\n")
                  tippctr=0

                  def tipps_to_delete = existing_tipps.clone()
                  def num_removed_tipps = 0;
                  def status_current = RefdataCategory.lookup('KBComponent.Status','Current')
                  def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
                  def status_retired = RefdataCategory.lookup('KBComponent.Status', 'Retired')
                  def status_expected = RefdataCategory.lookup('KBComponent.Status', 'Expected')

                  def tipp_upsert_start_time = System.currentTimeMillis()
                  def tipp_fails = 0

                  if ( json?.size() > 0 ) {
                    Package.withNewSession {
                      def pkg_new = Package.get(the_pkg.id)
                      def status_ip = RefdataCategory.lookup('Package.ListStatus', 'In Progress')

                      if (pkg_new.status == status_current && pkg_new?.listStatus != status_ip) {
                        pkg_new.listStatus = status_ip
                        pkg_new.save(flush:true)
                      }
                    }
                  }

                  // If valid, upsert tipps
                  json.eachWithIndex { tipp, idx ->
                    tippctr++

                    log.debug("Upsert tipp [${tippctr}] ${tipp}")
                    def upserted_tipp = null

                    try {
                      upserted_tipp = TitleInstancePackagePlatform.upsertDTO(tipp, user)
                      log.debug("Upserted TIPP ${upserted_tipp} with URL ${upserted_tipp?.url}")
                      upserted_tipp = upserted_tipp?.merge(flush: true)

                      componentUpdateService.ensureCoreData(upserted_tipp, tipp, fullsync)
                    }
                    catch (grails.validation.ValidationException ve) {
                      log.error("ValidationException attempting to cross reference TIPP",ve);
                      valid = false
                      tipp_fails++
                      def tipp_errors = [
                        code: 400,
                        idx: idx,
                        message: messageService.resolveCode('crossRef.package.tipps.error.validation', [tipp.title.name], locale),
                        baddata: tipp,
                        errors: messageService.processValidationErrors(ve.errors)
                      ]
                      errors.add(tipp_errors)

                      if (upserted_tipp)
                        upserted_tipp.discard()
                    }
                    catch (Exception ge) {
                      log.error("Exception attempting to cross reference TIPP:", ge)
                      valid = false
                      tipp_fails++
                      def tipp_errors = [
                        code: 500,
                        idx: idx,
                        message: messageService.resolveCode('crossRef.package.tipps.error', [tipp.title.name], locale),
                        baddata: tipp
                      ]
                      errors.add(tipp_errors)

                      if (upserted_tipp)
                        upserted_tipp.discard()
                    }

                    if (upserted_tipp) {
                      if ( existing_tipps.size() > 0 && upserted_tipp && existing_tipps.contains(upserted_tipp.id) ) {
                        log.debug("Existing TIPP matched!")
                        tipps_to_delete.remove(upserted_tipp.id)
                      }

                      if ( upserted_tipp && upserted_tipp?.status != status_deleted && tipp.status == "Deleted" ) {
                        upserted_tipp.deleteSoft()
                        num_removed_tipps++;
                      }
                      else if ( upserted_tipp && upserted_tipp?.status != status_retired && tipp.status == "Retired" ) {
                        upserted_tipp.retire()
                        num_removed_tipps++;
                      }
                      else if ( upserted_tipp && upserted_tipp.status != status_current && (!tipp.status || tipp.status == "Current") ) {
                        upserted_tipp.setActive()
                      }
                    }
                    else {
                      log.debug("Could not reference TIPP")
                      valid = false
                      tipp_fails++
                      def tipp_errors = [
                        code: 500,
                        idx: idx,
                        message: messageService.resolveCode('crossRef.package.tipps.error', [tipp.title.name], locale),
                        baddata: tipp
                      ]
                      errors.add(tipp_errors)
                    }

                    if (idx % 50 == 0) {
                      cleanUpGorm(session)
                    }
                    job.setProgress(idx + json.size(), json.size() * 2)
                  }

                  if (!valid) {
                    job_result.result = 'ERROR'
                    job_result.message = "Package was created, but ${tipp_fails} TIPPs could not be created!"
                  }
                  else {
                    if ( !update && existing_tipps.size() > 0 ) {


                      tipps_to_delete.eachWithIndex { ttd, idx ->

                        def to_retire = TitleInstancePackagePlatform.get(ttd)

                        if ( to_retire?.isCurrent() ) {
                          if (fullsync) {
                            to_retire.deleteSoft()
                          }
                          else {
                            to_retire.retire()
                          }
                          to_retire.save(failOnError: true)

                          num_removed_tipps++;
                        }else{
                          log.debug("TIPP to retire has status ${to_retire?.status?.value ?: 'Unknown'}")
                        }

                        if ( idx % 50 == 0 ) {
                          cleanUpGorm(session)
                        }
                      }
                      if( num_removed_tipps > 0 ) {
                        reviewRequestService.raise(
                            the_pkg,
                            "TIPPs retired.",
                            "An update to package ${the_pkg.id} did not contain ${num_removed_tipps} previously existing TIPPs.",
                            user,
                            null,
                            null,
                            RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'TIPPs Retired')
                        )
                      }
                    }
                    log.debug("Found ${num_removed_tipps} TIPPS to delete/retire from the matched package!")
                    job_result.result = 'OK'
                    the_pkg.refresh()
                    job_result.message = messageService.resolveCode('crossRef.package.success', [the_pkg.name, tippctr, existing_tipps.size(), num_removed_tipps], locale)

                    Package.withNewSession {
                      def pkg_obj = Package.get(the_pkg.id)
                      if ( pkg_obj.status.value != 'Deleted' ) {
                        pkg_obj.lastUpdateComment = job_result.message
                        pkg_obj.save(flush:true)
                      }
                    }

                    job_result.pkgId = the_pkg.id
                    job_result.uuid = the_pkg.uuid
                    log.debug("Elapsed tipp processing time: ${System.currentTimeMillis()-tipp_upsert_start_time} for ${tippctr} records")
                  }
                }
                else {
                  job_result.result = 'ERROR'
                  the_pkg.refresh()
                  job_result.message = messageService.resolveCode('crossRef.package.error.tipps', [the_pkg.name], locale)
                  log.warn("Not loading tipps - failed validation")

                  if (the_pkg) {
                    def additionalInfo = [:]

                    if (errors.global.size() > 0 || errors.size() > 0) {
                      additionalInfo.errorObjects = errors
                    }

                    reviewRequestService.raise(
                      the_pkg,
                      "Invalid TIPPs.",
                      "An update for this package failed because of invalid TIPP information (JOB ${job.id}).",
                      user,
                      null,
                      (additionalInfo as JSON).toString(),
                      RefdataCategory.lookupOrCreate('ReviewRequest.StdDesc', 'Invalid TIPPs')
                    )
                  }
                }
              }else{
                job_result.result = 'ERROR'
                errors.global.add(['code': 400, 'message': message.resolveCode('crossRef.package.error', null, locale)])
              }
            }
            catch (Exception e) {
              log.error("Package Crossref failed with Exception",e)
              job_result.result = "ERROR"
              job_result.message = "Package referencing failed with exception!"
              job_result.code = 500
              errors.global.add([code: 500, message: messageService.resolveCode('crossRef.package.error.unknown', null, locale), data: json.packageHeader])
            }
            cleanUpGorm(session)
          }

          job.message(job_result.message.toString())
          job.setProgress(100)
          job.endTime = new Date()

          if (errors.global.size() > 0 || errors.size() > 0) {
            job_result.errors = errors
          }

          return job_result
        }
        log.debug("Starting job ${background_job}..")

        background_job.description = "Package CrossRef (${obj.name})"
        background_job.type = RefdataCategory.lookupOrCreate('Job.Type', 'PackageCrossRef')
        background_job.startOrQueue()
        background_job.startTime = new Date()

        if (async == false) {
          result = background_job.get()
        }
        else {
          result.job_id = background_job.id
        }
      }
      else if (request_user) {
        log.debug("Not ingesting package without name!")
        result.result = "ERROR"
        result.message = messageService.resolveCode('crossRef.package.error.name', [], request_locale)
        result.errors = [name: [[message: messageService.resolveCode('crossRef.package.error.name', null, request_locale), baddata: null]]]
        response.setStatus(400)
      }
      else {
        log.debug("Unable to reference user!")
      }
    }

    render result as JSON
  }

  private def cleanUpGorm(session) {
    log.debug("Clean up GORM");

    // flush and clear the session.
    session.flush()
    session.clear()
  }
}
