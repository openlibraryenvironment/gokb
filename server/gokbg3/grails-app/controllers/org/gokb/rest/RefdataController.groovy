package org.gokb.rest

import grails.converters.*
import grails.gorm.transactions.*

import org.gokb.cred.*
import org.springframework.security.access.annotation.Secured;

@Transactional(readOnly = true)
class RefdataController {

  static namespace = 'rest'

  def genericOIDService
  def springSecurityService

  def showCategory() {
    def result = [:]
    def cat = null
    def user = springSecurityService.principal
    
    if (params.id.contains('.')) {
      cat = RefdataCategory.findByDesc(params.id)
    }
    else if (params.id.contains(':')) {
      cat = genericOIDService.resolveOID(params.id)
    }
    else {
      cat = RefdataCategory.get(params.id)
    }

    if (cat) {
      result['_links'] = ['self':['href': grailsApplication.config.serverURL + "/refdata/categories/${cat.id}"]]
      result['label'] = cat.label
      result['_embedded'] = ['values':[]]

      cat.values.each { v ->
        def val = [:]
        val['_links'] = [
          ['self':['href': grailsApplication.config.serverURL + "/refdata/values/${v.id}"]],
          ['owner':['href': grailsApplication.config.serverURL + "/refdata/categories/${cat.id}"]]
        ]
        if (user)

        val['value'] = v.value
        val['id'] = v.id

        result['_embedded']['values'].add(val)
      }
    }
    render result as JSON
  }

  def showValue() {
    def result = [:]
    def val = null

    if (params.id.contains(':')) {
      val = genericOIDService.resolveOID(params.id)
    }
    else if (params.id.contains('-')) {
      val = RefdataValue.findByUuid(params.id)
    }
    else {
      val = RefdataValue.get(params.id)
    }

    if (val) {
      result['_links'] = [
        ['self':['href': grailsApplication.config.serverURL + "/refdata/values/${val.id}"]],
        ['owner':['href': grailsApplication.config.serverURL + "/refdata/categories/${val.owner.id}"]]
      ]
      result['value'] = val.value
      result['_embedded'] = [:]
      result['_embedded']['owner'] = [
        '_links': [
          'self':['href': grailsApplication.config.serverURL + "/refdata/categories/${val.owner.id}"]
        ],
        'label': val.owner.label,
        'id': val.owner.id,
        '_embedded': [
          'values': []
        ]
      ]

      val.owner.values.each { v ->
        def siblings = [:]
        siblings['_links'] = [
          ['self':['href': grailsApplication.config.serverURL + "/refdata/values/${v.id}"]],
          ['owner':['href': grailsApplication.config.serverURL + "/refdata/categories/${val.owner.id}"]]
        ]
        siblings['value'] = v.value
        siblings['id'] = v.id

        result['_embedded']['owner']['_embedded']['values'].add(siblings)
      }
    }
    render result as JSON
  }
}
