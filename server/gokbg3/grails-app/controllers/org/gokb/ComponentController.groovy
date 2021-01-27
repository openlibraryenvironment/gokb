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
    def max = params.int('max') ?: user.defaultPageSize
    def offset = params.int('offset') ?: 0
    def components = []
    def dupe_ids = []

    result.max = max

    if (params.id) {
      IdentifierNamespace ns = genericOIDService.resolveOID(params.id)

      if (ns) {
        KBComponent.withNewSession { session ->
          log.debug("fetching results for ${ns} ..")

          RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
          RefdataValue combo_type = RefdataCategory.lookup('Combo.Type', 'KBComponent.Ids')
          RefdataValue combo_status = RefdataCategory.lookup('Combo.Status', 'Active')

          if (params.ctype == 'st') {

            final String query = '''SELECT ti.kbc_id FROM title_instance AS ti NATURAL JOIN kbcomponent as kbc WHERE kbc.kbc_status_rv_fk <> :deleted
              AND (SELECT count(c.combo_id) FROM combo AS c JOIN identifier AS id ON (c.combo_to_fk = id.kbc_id) WHERE
                c.combo_from_fk = kbc.kbc_id AND c.combo_status_rv_fk = :comboStatus
                AND c.combo_type_rv_fk = :comboType
                AND id.id_namespace_fk = :namespace
                AND EXISTS (select kt.kbc_id from title_instance as kt where kt.kbc_id = c.combo_from_fk)) > 1
              order by ti.kbc_id limit :limit offset :offset ;
            '''

            final String cqry = '''SELECT count(ti.kbc_id) FROM title_instance AS ti NATURAL JOIN kbcomponent as kbc WHERE kbc.kbc_status_rv_fk <> :deleted
              AND (SELECT count(c.combo_id) FROM combo AS c JOIN identifier AS id ON (c.combo_to_fk = id.kbc_id) WHERE
                c.combo_from_fk = kbc.kbc_id
                AND c.combo_status_rv_fk = :comboStatus
                AND c.combo_type_rv_fk = :comboType
                AND id.id_namespace_fk = :namespace
                AND EXISTS (select kt.kbc_id from title_instance as kt where kt.kbc_id = c.combo_from_fk)) > 1;
            '''

            final singleTitlesCount = session.createSQLQuery(cqry)
              .setParameter('deleted', status_deleted)
              .setParameter('namespace', ns.id)
              .setParameter('comboType', combo_type.id)
              .setParameter('comboStatus', combo_status.id)
              .list()

            result.titleCount = singleTitlesCount[0]

            final singleTitles = session.createSQLQuery(query)
              .setParameter('deleted', status_deleted)
              .setParameter('namespace', ns.id)
              .setParameter('comboType', combo_type.id)
              .setParameter('comboStatus', combo_status.id)
              .setParameter('limit', max)
              .setParameter('offset', offset)
              .list()

            components = singleTitles
          }

          if (params.ctype == 'di') {
            final String dquery = '''SELECT id.kbc_id FROM identifier AS id WHERE id.id_namespace_fk = :namespace
              AND (SELECT COUNT(c.combo_id) FROM combo AS c JOIN kbcomponent as kbc ON (c.combo_from_fk = kbc.kbc_id) WHERE
                kbc.kbc_status_rv_fk <> :deleted
                AND c.combo_to_fk = id.kbc_id
                AND c.combo_type_rv_fk = :comboType
                AND c.combo_status_rv_fk = :comboStatus
                AND EXISTS (select kt.kbc_id from title_instance as kt where kt.kbc_id = c.combo_from_fk)) > 1
              order by id.kbc_id limit :limit offset :offset ;
            '''

            final String dcqry = '''SELECT count(id.kbc_id) FROM identifier AS id WHERE id.id_namespace_fk = :namespace
              AND (SELECT COUNT(c.combo_id) FROM combo AS c JOIN kbcomponent as kbc ON (c.combo_from_fk = kbc.kbc_id) WHERE
                kbc.kbc_status_rv_fk <> :deleted
                AND c.combo_to_fk = id.kbc_id
                AND c.combo_type_rv_fk = :comboType
                AND c.combo_status_rv_fk = :comboStatus
                AND EXISTS (select kt.kbc_id from title_instance as kt where kt.kbc_id = c.combo_from_fk)) > 1;
            '''

            final dispersedIdsCount = session.createSQLQuery(dcqry)
              .setParameter('deleted', status_deleted)
              .setParameter('namespace', ns.id)
              .setParameter('comboType', combo_type.id)
              .setParameter('comboStatus', combo_status.id)
              .list()

            result.idsCount = dispersedIdsCount[0]

            final dispersedIds = session.createSQLQuery(dquery)
              .setParameter('deleted', status_deleted)
              .setParameter('namespace', ns.id)
              .setParameter('comboType', combo_type.id)
              .setParameter('comboStatus', combo_status.id)
              .setParameter('limit', max)
              .setParameter('offset', offset)
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
