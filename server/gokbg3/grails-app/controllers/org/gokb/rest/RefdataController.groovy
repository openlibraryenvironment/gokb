package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.springframework.security.access.annotation.Secured

@Transactional(readOnly = true)
class RefdataController {

  static final namespace = 'rest'

  def genericOIDService
  def springSecurityService

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    def base = grailsApplication.config.serverURL + "/" + namespace
    def result = [:]

    result['links'] = ['self':['href': base + "/refdata/"]]
    result['embedded'] = [
      'categories': []
    ]
    
    RefdataCategory.list().each { rc ->
      def rdc = [:]
      rdc['links'] = ['self':['href': base + "/refdata/categories/${rc.id}" ]]
      rdc['label'] = rc.label
      rdc['id'] = rc.id
      rdc['embedded'] = [
        'values' : []
      ]

      rc.values.each { rv ->
        def rdv = [:]
        rdv['links'] = ['self':['href': base + "/refdata/values/${rv.id}" ],'owner':['href': base + "/refdata/categories/${rc.id}" ]]
        rdv['value'] = rv.value
        rdv['id'] = rv.id
        rdc['embedded']['values'] << rdv
      }
      result['embedded']['categories'] << rdc
    }
    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def showCategory() {
    def result = [:]
    def cat = null
    def base = grailsApplication.config.serverURL + "/" + namespace

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
      result['links'] = ['self':['href': base + "/refdata/categories/${cat.id}"]]
      result['label'] = cat.label
      result['embedded'] = ['values':[]]

      cat.values.each { v ->
        def val = [:]
        val['links'] = [
          ['self':['href': base + "/refdata/values/${v.id}"]],
          ['owner':['href': base + "/refdata/categories/${cat.id}"]]
        ]

        val['value'] = v.value
        val['id'] = v.id

        result['embedded']['values'].add(val)
      }
    }
    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def showValue() {
    def result = [:]
    def val = null
    def base = grailsApplication.config.serverURL + "/" + namespace

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
      result['links'] = [
        ['self':['href': base + "/refdata/values/${val.id}"]],
        ['owner':['href': base + "/refdata/categories/${val.owner.id}"]]
      ]
      result['value'] = val.value
      result['embedded'] = [:]
      result['embedded']['owner'] = [
        'links': [
          'self':['href': base+ "/refdata/categories/${val.owner.id}"]
        ],
        'label': val.owner.label,
        'id': val.owner.id,
        'embedded': [
          'values': []
        ]
      ]

      val.owner.values.each { v ->
        def siblings = [:]
        siblings['links'] = [
          ['self':['href': base + "/refdata/values/${v.id}"]],
          ['owner':['href': base + "/refdata/categories/${val.owner.id}"]]
        ]
        siblings['value'] = v.value
        siblings['id'] = v.id

        result['embedded']['owner']['embedded']['values'].add(siblings)
      }
    }
    render result as JSON
  }
}
