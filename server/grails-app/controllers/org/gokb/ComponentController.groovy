package org.gokb

import org.gokb.cred.*
import org.hibernate.Session
import org.springframework.security.access.annotation.Secured;
import grails.converters.JSON
import groovy.util.logging.*

@Slf4j
class ComponentController {

  def springSecurityService
  def sessionFactory
  def genericOIDService

  def index() { }

  def identifierConflicts() {
    log.debug("identifierConflicts :: ${params}")

    Map result = [result:'OK', dispersedIds: [], ambiguousComponents: []]
    Session session = sessionFactory.currentSession
    User user = springSecurityService.currentUser
    int max = params.int('max') ?: user.defaultPageSize
    int offset = params.int('offset') ?: 0
    List components = []
    Map knownIdentifiedTypes = [
      title: [
        sql: 'title_instance',
        cls: 'TitleInstance'
      ],
      journal: [
        sql: 'journal_instance',
        cls: 'JournalInstance'
      ],
      book: [
        sql: 'book_instance',
        cls: 'BookInstance'
      ],
      database: [
        sql: 'database_instance',
        cls: 'DatabaseInstance'
      ],
      other: [
        sql: 'other_instance',
        cls: 'OtherInstance'
      ],
      tipp: [
        sql: 'title_instance_package_platform',
        cls: 'TitleInstancePackagePlatform'
      ],
      package: [
        sql: 'package',
        cls: 'Package'
      ],
      org: [
        sql: 'org',
        cls: 'Org'
      ]
    ]

    List dupe_ids = []
    Map resolvedComponentType = params.componentType ? knownIdentifiedTypes[params.componentType] : [:]

    result.max = max
    result.offset = offset

    if (params.oid && resolvedComponentType) {
      IdentifierNamespace ns = genericOIDService.resolveOID(params.oid)

      if (ns) {
        result.ctype = params.ctype
        result.componentType = params.componentType
        result.oid = params.oid
        result.nsname = ns.name ?: ns.value
        result.namespace = ns.value
        result.withoutJump = result

        log.debug("fetching results for ${ns} (${params.componentType})..")

        RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
        RefdataValue ci_status = RefdataCategory.lookup('ComponentIdentifier.Status', 'Active')

        if (params.ctype == 'st') {
          String staticClause = ''' kbc.kbc_status_rv_fk <> :deleted
            AND (SELECT count(c.id) FROM component_identifier AS c JOIN identifier AS id ON (c.ci_ident_fk = id.kbc_id) WHERE
              c.ci_comp_fk = kbc.kbc_id AND c.ci_status_rv_fk = :ciStatus
              AND id.id_namespace_fk = :namespace) > 1'''

          StringWriter query = new StringWriter()
          StringWriter cqry = new StringWriter()

          query.write('SELECT kbc.kbc_id FROM kbcomponent as kbc ')
          cqry.write('SELECT count(kbc.kbc_id) FROM kbcomponent as kbc ')

          if (resolvedComponentType) {
            query.write("WHERE EXISTS (SELECT 1 FROM ${resolvedComponentType.sql} WHERE kbc_id = kbc.kbc_id) AND")
            cqry.write("WHERE EXISTS (SELECT 1 FROM ${resolvedComponentType.sql} WHERE kbc_id = kbc.kbc_id) AND")
          }
          else {
            query.write('WHERE')
            cqry.write('WHERE')
          }

          query.write(staticClause)
          cqry.write(staticClause)

          query.write(' order by kbc.kbc_id limit :limit offset :offset ;')
          cqry.write(';')

          final ambiguousComponentsCount = session.createSQLQuery(cqry.toString())
            .setParameter('deleted', status_deleted.id)
            .setParameter('namespace', ns.id)
            .setParameter('ciStatus', ci_status.id)
            .list()

          result.titleCount = ambiguousComponentsCount[0]

          final ambiguousComponents = session.createSQLQuery(query.toString())
            .setParameter('deleted', status_deleted.id)
            .setParameter('namespace', ns.id)
            .setParameter('ciStatus', ci_status.id)
            .setParameter('limit', max)
            .setParameter('offset', offset)
            .list()

          components = ambiguousComponents
        }

        if (params.ctype == 'di') {
          String staticOuterClause = '''FROM identifier AS id WHERE id.id_namespace_fk = :namespace
            AND (SELECT COUNT(c.id) FROM component_identifier AS c JOIN kbcomponent as kbc ON (c.ci_comp_fk = kbc.kbc_id) '''

          String staticInnerClause = ''' kbc.kbc_status_rv_fk <> :deleted
              AND c.ci_ident_fk = id.kbc_id
              AND c.ci_status_rv_fk = :ciStatus) > 1'''

          StringWriter query = new StringWriter()
          StringWriter cqry = new StringWriter()

          query.write('''SELECT id.kbc_id ''')
          cqry.write('''SELECT count(id.kbc_id) ''')

          query.write(staticOuterClause)
          cqry.write(staticOuterClause)

          if (resolvedComponentType) {
            query.write("WHERE EXISTS (SELECT 1 FROM ${resolvedComponentType.sql} WHERE kbc_id = kbc.kbc_id) AND")
            cqry.write("WHERE EXISTS (SELECT 1 FROM ${resolvedComponentType.sql} WHERE kbc_id = kbc.kbc_id) AND")
          }
          else {
            query.write('WHERE')
            cqry.write('WHERE')
          }

          query.write(staticInnerClause)
          cqry.write(staticInnerClause)

          query.write(' order by id.kbc_id limit :limit offset :offset ;')
          cqry.write(';')

          final dispersedIdsCount = session.createSQLQuery(cqry.toString())
            .setParameter('deleted', status_deleted.id)
            .setParameter('namespace', ns.id)
            .setParameter('ciStatus', ci_status.id)
            .list()

          result.idsCount = dispersedIdsCount[0]

          final dispersedIds = session.createSQLQuery(query.toString())
            .setParameter('deleted', status_deleted.id)
            .setParameter('namespace', ns.id)
            .setParameter('ciStatus', ci_status.id)
            .setParameter('limit', max)
            .setParameter('offset', offset)
            .list()

          dupe_ids = dispersedIds
        }
      }

      components.each { cpid ->
        KBComponent item = KBComponent.get(cpid)

        Map info_map = [
          id: item.id,
          name: item.name,
          uuid: item.uuid,
          editStatus: (item.editStatus?.value ?: null),
          status: item.status.value,
          ids: item.activeIdInfo
        ]

        if (TitleInstance.isAssignableFrom(item.class)) {
          info_map.currentPublisher = item.currentPublisher?.name ?: null
        }

        if (item.class == Package) {
          info_map.listStatus = item.listStatus?.value ?: null
          info_map.global = item.global?.value ?: null
          info_map.tipps = [
            current: item.getTippCountForStatus('Current'),
            retired: item.getTippCountForStatus('Retired'),
            expected: item.getTippCountForStatus('Expected'),
            deleted: item.getTippCountForStatus('Deleted')
          ]
        }

        if (item.class == Org) {
          info_map.providedPackagesCount = item.providedPackages.size()
          info_map.providedPlatforms = []

          item.providedPlatforms.each { pp ->
            info_map.providedPlatforms.add([id: pp.id, name: pp.name, url: pp.primaryUrl])
          }
        }

        result.ambiguousComponents.add(info_map)
      }

      dupe_ids.each { did ->
        Identifier item = Identifier.get(did)

        Map info_map = [
          id: item.id,
          uuid: item.uuid,
          value: item.value,
          namespace: item.namespace.value,
          components: []
        ]

        item.getActiveIdentifiedComponents(resolvedComponentType?.cls ?: null).each { aic ->
          info_map.components.add([id: aic.id, uuid: aic.uuid, name: aic.name, status: aic.status.value, niceName: aic.niceName])
        }

        result.dispersedIds.add(info_map)
      }
    }

    withFormat {
      html {
        result
      }
      json {
        render result as JSON
      }
    }
  }
}
