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
    def knownIdentifiedTypes = [
      title: 'title_instance',
      journal: 'journal_instance',
      book: 'book_instance',
      database: 'database_instance',
      other: 'other_instance',
      tipp: 'title_instance_package_platform',
      package: 'package',
      org: 'org'
    ]
    def dupe_ids = []

    result.max = max

    if (params.id) {
      IdentifierNamespace ns = genericOIDService.resolveOID(params.id)

      if (ns) {
        Identifier.withNewSession { session ->
          log.debug("fetching results for ${ns} (${params.componentType})..")

          RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
          RefdataValue combo_type = RefdataCategory.lookup('Combo.Type', 'KBComponent.Ids')
          RefdataValue combo_status = RefdataCategory.lookup('Combo.Status', 'Active')

          if (params.ctype == 'st') {
            String staticClause = '''kbcomponent as kbc WHERE kbc.kbc_status_rv_fk <> :deleted
              AND (SELECT count(c.combo_id) FROM combo AS c JOIN identifier AS id ON (c.combo_to_fk = id.kbc_id) WHERE
                c.combo_from_fk = kbc.kbc_id AND c.combo_status_rv_fk = :comboStatus
                AND c.combo_type_rv_fk = :comboType
                AND id.id_namespace_fk = :namespace) > 1'''

            def query = new StringWriter()
            def cqry = new StringWriter()

            query.write('SELECT kbc.kbc_id FROM ')
            cqry.write('SELECT count(kbc.kbc_id) FROM ')

            if (params.componentType && knownIdentifiedTypes[params.componentType]) {
              query.write(knownIdentifiedTypes[params.componentType] + ' NATURAL JOIN ')
              cqry.write(knownIdentifiedTypes[params.componentType] + ' NATURAL JOIN ')
            }

            query.write(staticClause)
            cqry.write(staticClause)

            query.write(' order by kbc.kbc_id limit :limit offset :offset ;')
            cqry.write(';')

            log.debug("Fetching count with ${cqry.toString()}")

            final singleTitlesCount = session.createSQLQuery(cqry.toString())
              .setParameter('deleted', status_deleted)
              .setParameter('namespace', ns.id)
              .setParameter('comboType', combo_type.id)
              .setParameter('comboStatus', combo_status.id)
              .list()

            result.titleCount = singleTitlesCount[0]

            final singleTitles = session.createSQLQuery(query.toString())
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
            String staticOuterClause = '''FROM identifier AS id WHERE id.id_namespace_fk = :namespace
              AND (SELECT COUNT(c.combo_id) FROM combo AS c JOIN '''

            String staticInnerClause = '''kbcomponent as kbc ON (c.combo_from_fk = kbc.kbc_id) WHERE
                kbc.kbc_status_rv_fk <> :deleted
                AND c.combo_to_fk = id.kbc_id
                AND c.combo_type_rv_fk = :comboType
                AND c.combo_status_rv_fk = :comboStatus) > 1'''

            def query = new StringWriter()
            def cqry = new StringWriter()

            query.write('''SELECT id.kbc_id ''')
            cqry.write('''SELECT count(id.kbc_id) ''')

            query.write(staticOuterClause)
            cqry.write(staticOuterClause)

            if (params.componentType && knownIdentifiedTypes[params.componentType]) {
              query.write(knownIdentifiedTypes[params.componentType] + ' NATURAL JOIN ')
              cqry.write(knownIdentifiedTypes[params.componentType] + ' NATURAL JOIN ')
            }

            query.write(staticInnerClause)
            cqry.write(staticInnerClause)

            query.write(' order by id.kbc_id limit :limit offset :offset ;')
            cqry.write(';')

            final dispersedIdsCount = session.createSQLQuery(cqry.toString())
              .setParameter('deleted', status_deleted)
              .setParameter('namespace', ns.id)
              .setParameter('comboType', combo_type.id)
              .setParameter('comboStatus', combo_status.id)
              .list()

            result.idsCount = dispersedIdsCount[0]

            final dispersedIds = session.createSQLQuery(query.toString())
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
        result.componentType = params.componentType

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
