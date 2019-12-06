package org.gokb.rest

import grails.converters.*
import grails.core.GrailsClass
import grails.gorm.transactions.*

import groovyx.net.http.URIBuilder

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.gokb.cred.*
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*
import org.springframework.security.access.annotation.Secured;

@Transactional(readOnly = true)
class PackageController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService
  def ESSearchService
  def messsageService
  def restMappingService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() {
    def result = [:]
    def base = grailsApplication.config.serverURL
    def retainedFields = ['id', 'name', 'altname', 'curatoryGroups', 'uuid','listStatus','contentType','status','links','titleCount','identifiers']

    params.componentType = params.componentType ?: "Package" // Tells ESSearchService what to look for

    def es_result = ESSearchService.find(params)
    result['links'] = [:]
    result['embedded'] = ['packages': []]
    result.count = es_result.max
    result.total = es_result.count
    result.offset = es_result.offset

    def selfLink = new URIBuilder(base + '/packages')
    selfLink.addQueryParams(params)
    selfLink.removeQueryParam('controller')
    selfLink.removeQueryParam('action')
    selfLink.removeQueryParam('componentType')
    result['links']['self'] = [href: selfLink.toString()]


    if (es_result.count > es_result.offset+es_result.max) {
      def link = new URIBuilder(base + '/packages')
      link.addQueryParams(params)
      if(link.query.offset){
        link.removeQueryParam('offset')
      }
      link.removeQueryParam('controller')
      link.removeQueryParam('action')
      link.removeQueryParam('componentType')
      link.addQueryParam('offset', "${es_result.offset + es_result.max}")
      result['links']['next'] = ['href': (link.toString())]
    }
    if (es_result.offset > 0) {
      def link = new URIBuilder(base + '/packages')
      link.addQueryParams(params)
      if(link.query.offset){
        link.removeQueryParam('offset')
      }
      link.removeQueryParam('controller')
      link.removeQueryParam('action')
      link.removeQueryParam('componentType')
      link.addQueryParam('offset', "${(es_result.offset - es_result.max) > 0 ? es_result.offset - es_result.max : 0}")
      result['links']['prev'] = ['href': link.toString()]
    }
    result['links']['item'] = []

    es_result.records.each { pkg ->
      def halPkg = [:]
      def pkgId = genericOIDService.oidToId(pkg.id)
      result['links']['item'] << ['href': base + "/packages/${pkgId}"]
      halPkg['name'] = pkg.name
      halPkg['id'] = pkgId
      halPkg['uuid'] = pkg.uuid
      halPkg['links'] = []

      halPkg['links'] << ['self': ['href': (base + "/packages/${pkgId}"),]]
      halPkg['links'] << ['tipps': ['href': (base + "/packages/${pkgId}/tipps")]]

      if (pkg.provider) {
        halPkg['links'] << ['provider': ['href': (base + "/orgs/${genericOIDService.oidToId(pkg.provider)}"),'title':pkg.cpname]]
      }

      if (pkg.nominalPlatform) {
        halPkg['links'] << ['nominalPlatform': ['href': (base + "/platforms/${genericOIDService.oidToId(pkg.nominalPlatform)}"),'title': pkg.platformName]]
      }

      halPkg['embedded'] = [curatoryGroups:[], ids:[], variantNames:[]]
      
      pkg.ids.each { i ->
        halPkg['embedded']['ids'] << ['value': i.value, 'namespace': i.type]
      }

      pkg.curatoryGroups.each { cg ->
        def cg_obj = CuratoryGroup.findByName(cg)
        halPkg['embedded']['curatoryGroups'] << [
          'links': [
            'self': [
              'href': base + "/groups/" + cg_obj.id
            ]
          ],
          'name': cg_obj.name,
          'id': cg_obj.id
        ]
      }

      pkg.altname.each {
        halPkg['embedded']['variantNames'] << ['variantName': it, 'links': ['owner': ['href': base + "/packages/${pkgId}"]]]
      }

      result['embedded']['packages'] << halPkg
    }

    render result as JSON
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def show() {
    def result = [:]
    def obj = null
    def base = grailsApplication.config.serverURL
    def embed_active = params['_embed']?.split(',') ?: []

    if (params.oid || params.id) {
      obj = Package.findByUuid(params.id) 
      
      if (!obj) {
        obj = genericOIDService.resolveOID(params.id)
      }

      if (!obj) {
        obj = Package.get(params.id)
      }

      if (obj.isReadable()) {
        result = restMappingService.mapObject(obj, embed_active)

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

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def update() {
    def result = ['result':'OK', 'params': params]
    def reqBody = request.JSON
    def errors = []
    def user = User.get(springSecurityService.principal.id)
    def editable = true
    def pkg = Package.findByUuid(params.id) ?: genericOIDService.resolveOID(params.id)
    DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT

    if (pkg && reqBody) {
      if ( !user.hasRole('ROLE_ADMIN') && pkg.curatoryGroups && pkg.curatoryGroups.size() > 0 ) {
        def cur = user.curatoryGroups?.id.intersect(pkg.curatoryGroups?.id)
        
        if (!cur) {
          editable = false
        }
      }
      if (editable) {
        GrailsClass obj_cls = grailsApplication.getArtefact('Domain','org.gokb.cred.Package')
        PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(obj_cls.fullName)

        pent.getPersistentProperties().each { p -> // list of PersistentProperties
          log.debug("${p.name} (assoc=${p instanceof Association}) (oneToMany=${p instanceof OneToMany}) (ManyToOne=${p instanceof ManyToOne}) (OneToOne=${p instanceof OneToOne})");
          if (!jsonMap.ignore.contains(p.name) && !jsonMap.immutable.contains(p.name) && reqBody[p.name]) {
            if ( p instanceof Association ) {
              if ( p instanceof ManyToOne || p instanceof OneToOne ) {
                // Set ref property
                log.debug("set assoc ${p.name} to lookup of OID ${reqBody[p.name].oid}");
                pkg[p.name] = genericOIDService.resolveOID(reqBody[p.name].oid)
              }
              else {
                // Add to collection
                log.debug("Skip handling collections}");
              }
            }
            else {
              log.debug("checking for type of property -> ${p.type}")
              switch ( p.type ) {
                case Long.class:
                  log.debug("Set simple prop ${p.name} = ${reqBody[p.name]} (as long=${Long.parseLong(reqBody[p.name])})");
                  pkg[p.name] = Long.parseLong(reqBody[p.name]);
                  break;

                case Date.class:
                  LocalDateTime dateObj = reqBody[p.name] ? LocalDate.parse(reqBody[p.name], formatter) : null
                  pkg[p.name] = dateObj ? java.sql.Timestamp.valueOf(dateObj) : null
                  log.debug("Set simple prop ${p.name} = ${reqBody[p.name]} (as date ${dateObj}))");
                  break;
                default:
                  log.debug("Default for type ${p.type}")
                  log.debug("Set simple prop ${p.name} = ${reqBody[p.name]}");
                  pkg[p.name] = reqBody[p.name]
                  break;
              }
            }
          }
        }

        if (reqBody.provider) {
          pkg.provider = genericOIDService.resolveOID(reqBody.provider.oid)
        }

        if (reqBody.nominalPlatform) {
          pkg.nominalPlatform = genericOIDService.resolveOID(reqBody.nominalPlatform.oid)
        }


        if( pkg.validate() ) {
          if(errors.size() == 0) {
            pkg.save(flush:true)
          }
        }
        else {
          result.result = 'ERROR'
          response.setStatus(422)
          errors.addAll(messsageService.processValidationErrors(pkg.errors))
        }
      }
      else {
        result.result = 'ERROR'
        response.setStatus(403)
        result.message = "User must belong to at least one curatory group of an existing package to make changes!"
      }
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
    def tippParams = params
    def base = grailsApplication.config.serverURL
    def pkgOid = (params.id?.contains(':') ? params.id : "org.gokb.cred.Package:${params.id}")
    def pkgId = genericOIDService.oidToId(params.id)

    result['links'] = ['self':['href': base + "/packages/" + pkgId + "/tipps"]]

    tippParams.remove('componentType')
    tippParams.componentType = "TIPP" // Tells ESSearchService what to look for
    tippParams.tippPackage = pkgOid
    tippParams.remove('id')
    tippParams.remove('uuid')

    def es_result =  ESSearchService.find(tippParams)

    result.count = es_result.max
    result.total = es_result.count
    result.offset = es_result.offset

    if (es_result.count > es_result.offset+es_result.max) {
      def link = new URIBuilder(base + "/packages/${pkgId}/tipps")
      link.addQueryParams(params)
      if(link.query.offset){
        link.removeQueryParam('offset')
      }
      link.removeQueryParam('tippPackage')
      link.removeQueryParam('controller')
      link.removeQueryParam('action')
      link.removeQueryParam('componentType')
      link.addQueryParam('offset', "${es_result.offset + es_result.max}")
      result['links']['next'] = ['href': (link.toString())]
    }
    if (es_result.offset > 0) {
      def link = new URIBuilder(base + "/packages/${pkgId}/tipps")
      link.addQueryParams(params)
      if(link.query.offset){
        link.removeQueryParam('offset')
      }
      link.removeQueryParam('tippPackage')
      link.removeQueryParam('controller')
      link.removeQueryParam('action')
      link.removeQueryParam('componentType')
      link.addQueryParam('offset', "${(es_result.offset - es_result.max) > 0 ? es_result.offset - es_result.max : 0}")
      result['links']['prev'] = ['href': link.toString()]
    }

    result['embedded'] = ['tipps':[]]
    result['links']['items'] = []

    es_result.records.each { tipp ->
      result['links']['items'] << ['href': base + "/tipps/" + genericOIDService.oidToId(tipp.id)]
      def ntip = [
        'links': [
          'self': ['href': base + "/tipps/" + genericOIDService.oidToId(tipp.id)],
          'pkg': ['href': base + "/packages/" + pkgId, 'title': tipp.tippPackageName ?: ''],
          'hostPlatform': ['href': base + "/platforms/" + genericOIDService.oidToId(tipp.hostPlatform), 'title': tipp.hostPlatformName],
          'title': ['href': base + genericOIDService.resolveOID(tipp.tippTitle).restPath + "/${genericOIDService.oidToId(tipp.tippTitle)}", 'title': tipp.tippTitleName ?: '']
        ]
      ]

      ntip.id = genericOIDService.oidToId(tipp.id)
      ntip.lastUpdated = tipp.lastUpdatedDisplay
      ntip.status = tipp.status
      ntip.uuid = tipp.uuid
      ntip.coverage = tipp.coverage
      ntip.url = tipp.url

      result['embedded']['tipps'] << ntip
    }

    render result as JSON
  }
}
