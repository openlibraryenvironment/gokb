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

  public final static Map jsonMap = [
    'ignore': [
      'lastProject',
      'bucketHash',
      'shortcode',
      'normname',
      'people',
      'lastSeen',
      'updateBenchmark',
      'systemComponent',
      'provenance',
      'insertBenchmark',
      'componentHash',
      'prices',
      'subjects',
      'lastUpdateComment',
      'reference',
      'duplicateOf',
      'componentDiscriminator'
    ],
    'combos': [
      'ids',
      'curatoryGroups',
      'nominalPlatform',
      'provider'
    ],
    'immutable': [
      'id',
      'uuid',
      'lastUpdated',
      'dateCreated',
      'lastUpdatedBy',
      'variantNames'
    ]
  ];

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() {
    def result = [:]
    def base = grailsApplication.config.serverURL
    def retainedFields = ['id', 'name', 'altname', 'curatoryGroups', 'uuid','listStatus','contentType','status','_links','titleCount','identifiers']

    params.componentType = params.componentType ?: "Package" // Tells ESSearchService what to look for

    def es_result = ESSearchService.find(params)
    result['_links'] = [:]
    result['_embedded'] = ['packages': []]
    result.count = es_result.max
    result.total = es_result.count
    result.offset = es_result.offset

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
      result['_links']['next'] = ['href': (link.toString())]
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
      result['_links']['prev'] = ['href': link.toString()]
    }

    es_result.records.each { pkg ->
      def halPkg = [:]
      halPkg['name'] = pkg.name
      halPkg['oid'] = pkg.id
      halPkg['uuid'] = pkg.uuid
      halPkg['listStatus']
      halPkg
      halPkg['_links'] = []

      halPkg['_links'] << ['self': ['href': (base + "/packages/${pkg.id}"), 'type': "application/hal+json"]]
      halPkg['_links'] << ['tipps': ['href': (base + "/packages/${pkg.id}/tipps"), 'type': "application/hal+json"]]

      if (pkg.provider) {
        halPkg['_links'] << ['provider': ['href': (base + "/orgs/${pkg.provider}"), 'type': "application/hal+json"]]
      }

      if (pkg.nominalPlatform) {
        halPkg['_links'] << ['nominalPlatform': ['href': (base + "/platforms/${pkg.nominalPlatform}"), 'type': "application/hal+json"]]
      }
    }

    render result as JSON
  }

  @Secured(['ROLE_USER'])
  def show() {
    log.debug("${springSecurityService.principal}")
    def result = [:]
    def obj = null

    if (params.oid || params.id) {
      obj = Package.findByUuid(params.id) 
      
      if (!obj) {
        obj = genericOIDService.resolveOID(params.oid ?: params.id)
      }

      if (!obj) {
        obj = Package.get(params.id)
      }

      if ( obj?.isReadable() ) {
        result = obj.getAllPropertiesWithLinks(false)
        jsonMap.ignore.each {
          result.remove(it)
        }

        result.currentTipps = obj.currentTippCount
        result.reviewRequests = ['_link']
        result.linkedOpenRequests = obj.getReviews(true,true).size()
        result.ids = obj.ids.collect { id -> ["oid":"org.gokb.cred.Identifier:${id.id}", "value": id.value, "namespace": id.namespace.value] }
        result.curatoryGroups = obj.curatoryGroups.collect { cg -> ["oid": "org.gokb.cred.CuratoryGroup:${cg.id}", 'name': cg.name, 'uuid': cg.uuid] }
        result.nominalPlatform = obj.nominalPlatform ? ["oid": "org.gokb.cred.Platform:${obj.nominalPlatform.id}", 'name': obj.nominalPlatform.name, 'uuid': obj.nominalPlatform.uuid] : null
        result.provider = obj.provider ? ["oid": "org.gokb.cred.Org:${obj.provider.id}", 'name': obj.provider.name, 'uuid': obj.provider.uuid] : null
      }
      else if (!obj) {
        result.error = "Object ID could not be resolved!"
        response.setStatus(404)
        result.code = 404
        result.result = 'ERROR'
      }
      else {
        result.error = "Access to object was denied!"
        response.setStatus(403)
        result.code = 403
        result.result = 'ERROR'
      }
    }
    else {
      result.result = 'ERROR'
      response.setStatus(400)
      result.code = 400
      result.error = 'No object id supplied!'
    }

    render result as JSON
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  @Transactional
  def update() {
    def result = ['result':'OK', 'params': params]
    def reqBody = request.JSON
    def errors = []
    def editable = true
    def pkg = Package.findByUuid(params.id) ?: genericOIDService.resolveOID(params.id)
    DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT

    if (pkg && reqBody) {
      GrailsClass pkg_cls = grailsApplication.getArtefact('Domain','org.gokb.cred.Package')

      if ( !springSecurity.currentUser.hasRole('ROLE_ADMIN') && pkg.curatoryGroups && pkg.curatoryGroups.size() > 0 ) {
        def cur = springSecurity.currentUser.curatoryGroups?.id.intersect(pkg.curatoryGroups?.id)
        
        if (!cur) {
          editable = false
        }
      }
      if (editable) {
        PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(pkg_cls.fullName)

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

    tippParams.remove('componentType')
    tippParams.componentType = "TIPP" // Tells ESSearchService what to look for
    tippParams.linkedPackage = "${params.id}"
    tippParams.remove('id')
    tippParams.remove('uuid')

    result =  ESSearchService.find(tippParams)

    render result as JSON
  }
}
