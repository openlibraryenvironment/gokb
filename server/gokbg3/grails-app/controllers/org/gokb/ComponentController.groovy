package org.gokb

import org.gokb.cred.*
import org.springframework.security.access.annotation.Secured;
import grails.converters.JSON
import groovy.util.logging.*

@Slf4j
class ComponentController {

  def springSecurityService
  def sessionFactory
  def genericOIDService

  def index() { }

  @Secured(['ROLE_EDITOR', 'IS_AUTHENTICATED_FULLY'])
  def identifierConflicts() {
    log.debug("identifierConflicts :: ${params}")

    def result = [result:'OK', dispersedIds: [], singleTitles: []]
    User user = springSecurityService.currentUser
    def components = []
    def dupe_ids = []

    if (params.id) {
      IdentifierNamespace ns = genericOIDService.resolveOID(params.id)
      
      if (ns) {
        KBComponent.withNewSession { session ->
          log.debug("fetching results for ${ns} ..")

          RefdataValue status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
          RefdataValue combo_type = RefdataCategory.lookup('Combo.Type', 'KBComponent.Ids')

          if (!params.ctype || params.ctype != 'di') {

            final String query = "SELECT kbc_id FROM title_instance AS kbc WHERE (SELECT count(c.combo_id) FROM combo AS c JOIN identifier AS id ON (c.combo_to_fk = id.kbc_id) WHERE c.combo_from_fk = kbc.kbc_id AND c.combo_type_rv_fk = :comboType AND id.id_namespace_fk = :namespace) > 1;"

            final singleTitles = session.createSQLQuery(query)
              .setParameter('namespace', ns.id)
              .setParameter('comboType', combo_type.id)
              .list()
            
            components = singleTitles
          }
          
          if (!params.ctype || params.ctype != 'st') {
            final String dquery = "select kbc_id from identifier as id where id_namespace_fk = :namespace and (select count(*) from combo as c where c.combo_to_fk = id.kbc_id and c.combo_type_rv_fk = :comboType) > 1;"

            final dispersedIds = session.createSQLQuery(dquery)
              .setParameter('namespace', ns.id)
              .setParameter('comboType', combo_type.id)
              .list()

            dupe_ids = dispersedIds
          }
        }

        result.namespace = ns
        result.ctype = params.ctype

        components.each {
          result.singleTitles.add(KBComponent.get(it))
        }

        dupe_ids.each {
          result.dispersedIds.add(Identifier.get(it))
        }
      }
    }

    withFormat {
      html { result }
      json { render result as JSON }
    }
  }
}
