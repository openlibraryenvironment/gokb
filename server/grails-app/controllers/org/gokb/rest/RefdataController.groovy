package org.gokb.rest

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.gokb.cred.RefdataCategory
import org.gokb.cred.RefdataValue
import org.springframework.security.access.annotation.Secured

@Transactional(readOnly = true)
class RefdataController {

  static final namespace = 'rest'

  def genericOIDService
  def springSecurityService

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    def base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"
    def result = [:]

    result['_links'] = ['self': ['href': base + "/refdata/"]]
    result['_embedded'] = [
      'categories': []
    ]

    RefdataCategory.list().each { rc ->
      def rdc = [:]
      rdc['_links'] = ['self': ['href': base + "/refdata/categories/${rc.id}"]]
      rdc['label'] = rc.label
      rdc['id'] = rc.id
      rdc['_embedded'] = [
        'values': []
      ]

      rc.values.each { rv ->
        def rdv = [:]
        rdv['_links'] = ['self': ['href': base + "/refdata/values/${rv.id}"], 'owner': ['href': base + "/refdata/categories/${rc.id}"]]
        rdv['value'] = rv.value
        rdv['deprecated'] = rv.deprecated
        rdv['description'] = rv.description
        rdv['sortKey'] = rv.sortKey
        rdv['useInstead'] = rv.useInstead ? rv.id : null
        rdv['id'] = rv.id

        rdc['_embedded']['values'] << rdv
      }
      result['_embedded']['categories'] << rdc
    }
    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def showCategory() {
    def result = [:]
    def cat = null
    def base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"

    if (params.int('id')) {
      cat = RefdataCategory.get(params.int('id'))
    }
    else if (params.id.contains(':')) {
      cat = genericOIDService.resolveOID(params.id)
    }
    else {
      cat = RefdataCategory.findByDesc(params.id)
    }

    if (cat) {
      result['_links'] = ['self': ['href': base + "/refdata/categories/${cat.id}"]]
      result['label'] = cat.label
      result['_embedded'] = ['values': []]

      def vals = cat.values.sort { it.sortKey }

      vals.each { v ->
        if (!v.useInstead && (!v.deprecated || params.boolean('ignoreDeprecation'))) {
          def val = [:]
          val['_links'] = [
            ['self': ['href': base + "/refdata/values/${v.id}"]],
            ['owner': ['href': base + "/refdata/categories/${cat.id}"]]
          ]

          val['value'] = v.value
          val['id'] = v.id

          result['_embedded']['values'].add(val)
        }
      }
    }
    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def showValue() {
    def result = [:]
    def val = null
    def base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"

    if (params.id.contains(':')) {
      val = genericOIDService.resolveOID(params.id)
    } else {
      val = RefdataValue.get(params.id)
    }

    if (val) {
      result['_links'] = [
        ['self': ['href': base + "/refdata/values/${val.id}"]],
        ['owner': ['href': base + "/refdata/categories/${val.owner.id}"]]
      ]
      result['value'] = val.value
      result['description'] = val.description
      result['deprecated'] = val.deprecated
      result['useInstead'] = val.useInstead
      result['_embedded'] = [:]
      result['_embedded']['owner'] = [
        '_links'   : [
          'self': ['href': base + "/refdata/categories/${val.owner.id}"]
        ],
        'label'    : val.owner.label,
        'id'       : val.owner.id,
        '_embedded': [
          'values': []
        ]
      ]

      val.owner.values.each { v ->
        def siblings = [:]
        siblings['_links'] = [
          ['self': ['href': base + "/refdata/values/${v.id}"]],
          ['owner': ['href': base + "/refdata/categories/${val.owner.id}"]]
        ]
        siblings['value'] = v.value
        siblings['id'] = v.id
        siblings['description'] = v.description
        siblings['deprecated'] = v.deprecated
        siblings['useInstead'] = v.useInstead ? v.useInstead.id : null

        result['_embedded']['owner']['_embedded']['values'].add(siblings)
      }
    }
    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def packageScope() {
    def result = [:]
    def resultData = []
    def cat = null
    def base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"

    cat = RefdataCategory.findByLabel("Package.Scope")

    if (cat) {
      result['_links'] = ['self': ['href': base + "/package-scopes"]]
      result['label'] = cat.label

      cat.values.each { v ->
        def val = [:]
        val['_links'] = [
          ['self': ['href': base + "/refdata/values/${v.id}"]],
          ['owner': ['href': base + "/refdata/categories/${cat.id}"]]
        ]

        val['value'] = v.value
        val['id'] = v.id

        resultData << val
      }
    }
    result.data = resultData
    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def coverageDepth() {
    def result = [:]
    def resultData = []
    def cat = null
    def base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"

    cat = RefdataCategory.findByLabel("TIPPCoverageStatement.CoverageDepth")

    if (cat) {
      result['_links'] = ['self': ['href': base + "/coverage-depth"]]
      result['label'] = cat.label

      cat.values.each { v ->
        def val = [:]
        val['_links'] = [
          ['self': ['href': base + "/refdata/values/${v.id}"]],
          ['owner': ['href': base + "/refdata/categories/${cat.id}"]]
        ]

        val['value'] = v.value
        val['id'] = v.id

        resultData << val
      }
    }
    result.data = resultData
    render result as JSON
  }

  @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
  def reviewType() {
    def result = [:]
    def resultData = []
    def cat = null
    def base = grailsApplication.config.getProperty('grails.serverURL', String, "") + "/rest"

    cat = RefdataCategory.findByLabel("ReviewRequest.StdDesc")

    if (cat) {
      result['_links'] = ['self': ['href': base + "/review-types"]]
      result['label'] = cat.label

      cat.values.each { v ->
        if (!v.deprecated || params.boolean('ignoreDeprecation')) {
          def val = [:]
          val['_links'] = [
            ['self': ['href': base + "/refdata/values/${v.id}"]],
            ['owner': ['href': base + "/refdata/categories/${cat.id}"]]
          ]

          val['value'] = v.value
          val['description'] = val.description
          val['deprecated'] = val.deprecated
          val['useInstead'] = val.useInstead ? val.useInstead.id : null
          val['id'] = v.id

          resultData << val
        }
      }
    }
    result.data = resultData
    render result as JSON
  }
}
