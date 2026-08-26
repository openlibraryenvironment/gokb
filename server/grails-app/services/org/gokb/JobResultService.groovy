package org.gokb

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService.Job

import grails.gorm.transactions.Transactional

import groovy.util.logging.Slf4j

import java.time.*

import org.gokb.cred.*
import org.hibernate.Session

import static grails.async.Promises.*

@Slf4j
class JobResultService {
  def sessionFactory

  public Map fetchJobs(params, max, offset) {
    Map result = [:]
    boolean first = true
    KBComponent linkedItemObj
    CuratoryGroup groupFilterObj

    Map qry_pars = [:]
    String base_qry = "from JobResult as jr "

    if (params.long('user')) {
      base_qry += "where jr.ownerId = :user"

      qry_pars.user = params.long('user')
      first = false
    }

    if (params.long('curatoryGroup')) {
      groupFilterObj = CuratoryGroup.get(params.long('curatoryGroup'))

      if (groupFilterObj) {
        if (first) {
          base_qry += "where "
        }
        else {
          base_qry += "and "
        }

        base_qry += "where jr.groupId = :group"

        qry_pars.group = groupFilterObj.id
        first = false
      }
      else {
        result.result = 'ERROR'
        result.message = 'Unable to reference curatoryGroup parameter!'
      }
    }

    if (params.linkedItem) {
      linkedItemObj = params.long('linkedItem') ? KBComponent.get(params.long('linkedItem')) : KBComponent.findByUuid(params.linkedItem)

      if (linkedItemObj) {
        if (first) {
          base_qry += "where "
        }
        else {
          base_qry += "and "
        }

        base_qry += "where jr.linkedItemId = :linkedItemId"

        qry_pars.linkedItemId = linkedItemObj.id
        first = false
      }
      else {
        result.result = 'ERROR'
        result.message = 'Unable to reference linkedItem parameter!'
      }
    }

    if (params.date) {
      LocalDate dateFilter

      try {
        dateFilter = LocalDate.parse(params.date)
      }
      catch (Exception e) {
        log.debug(e)
        result.result = 'ERROR'
        result.message = 'Unable to parse provided date parameter!'

        return result
      }

      if (dateFilter) {
        if (!first) {
          base_qry += " and "
        }
        else {
          base_qry += " where "
        }

        base_qry += "jr.startTime > :date and jr.startTime < :nd"

        qry_pars.date = java.sql.Date.valueOf(dateFilter)
        qry_pars.nd = java.sql.Date.valueOf(dateFilter.plusDays(1))
      }
    }

    if (params.type == 'import') {
      qry_pars.jt = [RefdataCategory.lookup('Job.Type','KBARTIngest'), RefdataCategory.lookup('Job.Type','KBARTSourceIngest')]

      base_qry += " where type in (:jt)"
      first = false
    }
    else if (params.long('type')) {
      RefdataValue rdv_type = RefdataValue.get(params.long('type'))

      if (rdv_type) {
        if (rdv_type.owner = RefdataCategory.lookup('Job.Type')) {
          qry_pars.jt = rdv_type

          base_qry += " where type = :jt"
          first = false
        }
        else {
          result.result = 'ERROR'
          result.message = 'Reference value ${params.type} is not a job type!'
        }
      }
      else {
        result.result = 'ERROR'
        result.message = 'Unable to reference job type via ID ${params.type}!'
      }
    }

    if (params.status) {
      if (first) {
        base_qry += " where statusText = :st"
      }
      else {
        base_qry *= " and statusText = :st"
      }

      qry_pars.st = params.status

      first = false
    }

    if (result.result != 'ERROR') {
      Long hqlTotal = JobResult.executeQuery("select count(jr.id) ${base_qry}".toString(), qry_pars)[0]
      List jobs = JobResult.executeQuery("${base_qry} order by jr.startTime desc".toString(),  qry_pars, [max: max, offset: offset])
      result.data = []

      jobs.each { j ->
        KBComponent component = linkedItemObj ?: (j.linkedItemId ? KBComponent.get(j.linkedItemId) : null)
        CuratoryGroup cg = groupFilterObj ?: CuratoryGroup.get(j.groupId)

        result.data << [
            group      : cg ? [id: cg.id, name: cg.name, uuid: cg.uuid] : null,
            uuid       : j.uuid,
            description: j.description,
            type       : j.type ? [id: j.type.id, name: j.type.value, value: j.type.value] : null,
            linkedItem : (component ? [id: component.id, type: component.niceName, uuid: component.uuid, name: component.name] : null),
            startTime  : j.startTime,
            endTime    : j.endTime,
            status     : j.statusText
        ]
      }

      result['_pagination'] = [
          offset: offset,
          limit : max,
          total : hqlTotal
      ]
    }

    return result
  }
}
