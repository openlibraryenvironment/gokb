package org.gokb

import java.time.LocalDateTime
import java.time.ZoneOffset

import org.gokb.cred.*

class OAIService {
  def dateFormatService
  def genericOIDService

  def fetchRecordList(params, config) {
    Map result = [
      query: config.oaiConfig.query,
      query_params: [:],
      records: [],
      errors: [],
      rec_count: null,
      returnAttrs: true
    ]

    RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
    Map pagination = [
      from: null,
      until: null,
      offset: 0,
      max: (config.oaiConfig.pageSize ?: 10),
      no_offset_rt: true,
      min_id: null,
      metadataPrefix: null
    ]

    Map setConfig = [
      local_only: false,
      curator: [],
      content: [],
      validity: []
    ]

    String order_by_clause = config.oaiConfig.cachedPackageResponse ? 'order by o.lastCachedDate, o.id' : 'order by o.lastUpdated, o.id'

    if (config.oaiConfig.cachedPackageResponse) {
      result.query += 'where o.lastCachedDate is not null'
    }

    if (params.list('set').size() > 0) {
      if (config.oaiConfig.id == 'packages') {
        handleSetFilters(result, setConfig, params.list('set'))
      }
      else {
        result.errors.add([code:'noSetHierarchy', expl: 'Sets are not available for this component type'])
      }
    }


    if (config.oaiConfig.id == 'packages') {
      handleCurators(result, setConfig, config.oaiConfig.curators, params)

      processValidityFilters(result, setConfig)
      processContentTypeFilters(result, setConfig)
    }

    if (config.oaiConfig.id == 'tipps') {
      handleTippFields(result, config.oaiConfig.pkg, params)
    }

    processStatusFilter(result, config.oaiConfig.statusFilter)

    processPagination(pagination, result, config, params)

    if (result.errors) {
      log.debug("Request had errors .. not executing query!")
    }
    else {
      result.pagination = pagination

      log.debug("qry is: ${result.query}")
      log.debug("qry params are: ${result.query_params}")

      Long new_rec_count = Package.executeQuery("select count(o) ${result.query}".toString(), result.query_params)[0]

      if (!pagination.no_offset_rt) {
        result.records = Package.executeQuery("select o ${result.query} ${order_by_clause}".toString(), result.query_params, [offset: pagination.offset, max: pagination.max, readOnly: true])
        result.rec_count = new_rec_count
      }
      else {
        result.records = Package.executeQuery("select o ${result.query} ${order_by_clause}".toString(), result.query_params, [max: pagination.max, readOnly: true])

        result.rec_count = new_rec_count + pagination.offset
      }

      log.debug("${result.query} rec_count is ${result.rec_count}, records_size=${result.records.size()}")

      if ((!pagination.no_offset_rt && (pagination.offset + result.records.size() < result.rec_count)) || result.records.size() < new_rec_count) {
        Date new_min_date = result.records[result.records.size() - 1][config.oaiConfig.cachedPackageResponse ? 'lastCachedDate' : 'lastUpdated']
        Long new_min_id = result.records[result.records.size() - 1].id
        String until_date_string = pagination.until ? dateFormatService.formatIsoMsTimestamp(pagination.until) : ''
        Long new_offset = pagination.offset + result.records.size()

        result.resumption = "${pagination.metadataPrefix}|${dateFormatService.formatIsoMsTimestamp(new_min_date)}|${until_date_string}|${new_min_id}|${new_offset}"
      }
    }

    result
  }

  private void processStatusFilter(result, status_filter) {
    Boolean wClause = result.query.contains('where')
    String query_additions = ""

    if (status_filter && status_filter.size() > 0) {
      status_filter.eachWithIndex { val, index ->
        if(!wClause){
          query_additions += 'where '
          wClause = true
        }
        else{
          query_additions += ' and '
        }

        if (val instanceof String) {
          query_additions += 'o.status != :status'
          RefdataValue qry_rdc = RefdataCategory.lookup(KBComponent.RD_STATUS, val)
          result.query_params.put('status', qry_rdc)
        }
        else if (val instanceof org.gokb.cred.RefdataValue) {
          query_additions += 'o.status != :status'
          result.query_params.put('status', val)
        }
        else {
          log.warn("Unknown parameter format!")
        }
      }

      result.query = result.query + query_additions
    }
  }

  private void handleCurators(result, setFilters, rdv, params) {
    Boolean wClause = result.query.contains('where')
    String query_additions = ""
    CuratoryGroup cg = null

    if (setFilters.curators) {
      if (setFilters.curators.size() > 1) {
        result.errors.add([code:'badArgument', name: 'set', expl: 'Unable to process multiple curator filter sets'])
        result.returnAttrs = false
      }
      else {
        def cg_id = setFilters.curators[0].split(':')

        if (cg_id.size() > 2 && cg_id[2].trim() && cg_id[2].isInteger()) {
          cg = CuratoryGroup.get(cg_id[2] as int)
        }
      }
    }
    else if (params.curator && rdv) {
      cg = CuratoryGroup.findByName(params.curator)

      if (!cg) {
        result.errors.add([code:'badArgument', name: 'curator', expl: 'Unable to lookup Curatory Group.'])
        result.returnAttrs = false
      }
    }

    if (!result.errors && cg) {
      query_additions += ', Combo as cgCombo, CuratoryGroup as cg where cgCombo.toComponent = :cgo and cgCombo.type = :cgtype and cgCombo.fromComponent = o '
      result.query_params.put('cgo', cg)
      result.query_params.put('cgtype', RefdataCategory.lookup('Combo.Type', rdv))
    }

    result.query = result.query + query_additions
  }

  private void handleTippFields(result, rdv, params) {
    Boolean wClause = result.query.contains('where')
    String query_additions = ""

    // Package filter

    if (params.pkg && rdv) {
      Package linked_pkg = Package.findByUuid(params.pkg)

      if (!linked_pkg) {
        try {
          linked_pkg = Package.get(genericOIDService.oidToId(params.pkg))
        }
        catch (Exception e) {

        }
      }

      if (linked_pkg) {
        query_additions += ', Combo as pkgCombo, Package as pkg where pkgCombo.fromComponent = :lpkg and pkgCombo.type = :cpkgt and pkgCombo.toComponent = o '
        wClause = true
        result.query_params.put('lpkg', linked_pkg)
        result.query_params.put('cpkgt', RefdataCategory.lookup('Combo.Type', rdv))
      }
      else {
        result.errors.add([code:'badArgument', name: 'pkg', expl: 'Unable to lookup Package.'])
        result.returnAttrs = false
      }
    }

    if (!wClause){
      query_additions += 'where '
    }
    else{
      query_additions += ' and '
    }

    // Filter out TIPPs without linked TitleInstance
    query_additions += 'exists (select 1 from Combo as cti where cti.toComponent = o and cti.type = :ctipp)'
    RefdataValue qry_cti = RefdataCategory.lookup(Combo.RD_TYPE, 'TitleInstance.Tipps')
    result.query_params.put('ctipp', qry_cti)

    result.query = result.query + query_additions
  }

  private void processPagination(pagination, result, config, params) {
    Boolean wClause = result.query.contains('where')
    String query_additions = ""

    if (params.resumptionToken && params.resumptionToken.trim()) {
      processResumptionToken(pagination, result, config, params)
    }
    else {
      pagination.metadataPrefix = params.metadataPrefix
    }

    if (!pagination.from && params.from != null && params.from.trim()) {
      def fparam = params.from

      if (fparam.length() == 10) {
        fparam += 'T00:00:00.000Z'
      }
      else if (fparam.length() == 20) {
        fparam = fparam.substring(0, 18) + ".000Z"
      }

      try {
        pagination.from = dateFormatService.parseIsoMsTimestamp(fparam)
      }
      catch (Exception pe) {
        result.errors.add([code:'badArgument', name: 'from', expl: 'This date format is not supported.'])
        result.returnAttrs = false
      }
    }

    if (!pagination.until && params.until != null && params.until.trim()) {
      def uparam = params.until

      if(uparam.length() == 10) {
        uparam += 'T00:00:00.000Z'
      }
      else if (uparam.length() == 20) {
        uparam = uparam.substring(0, 18) + ".000Z"
      }

      try {
        pagination.until = dateFormatService.parseIsoMsTimestamp(uparam)
      }
      catch (Exception pe) {
        result.errors.add([code:'badArgument', name: 'until', expl: 'This date format is not supported.'])
        result.returnAttrs = false
      }
    }

    if (pagination.from) {
      if (!wClause) {
        query_additions += 'where '
        wClause = true
      }
      else {
        query_additions += ' and '
      }

      if (config.oaiConfig.cachedPackageResponse) {
        if (!pagination.no_offset_rt) {
          query_additions += 'o.lastCachedDate > :lupdf'
        }
        else {
          query_additions += '(o.lastCachedDate > :lupdf OR (o.lastCachedDate = :lupdf AND o.id > :minId))'
          result.query_params.put('minId', pagination.min_id)
        }
      }
      else {
        if (!pagination.no_offset_rt) {
          query_additions += 'o.lastUpdated > :lupdf'
        }
        else {
          query_additions += '(o.lastUpdated > :lupdf OR (o.lastUpdated = :lupdf AND o.id > :minId))'
          result.query_params.put('minId', pagination.min_id)
        }
      }

      result.query_params.put('lupdf', pagination.from)
    }

    if (pagination.until) {
      if (!wClause) {
        query_additions += 'where '
        wClause = true
      }
      else {
        query_additions += ' and '
      }

      if (config.oaiConfig.cachedPackageResponse) {
        query_additions += 'o.lastCachedDate < :lupd'
      }
      else {
        query_additions += 'o.lastUpdated < :lupd'
      }

      result.query_params.put('lupd', pagination.until)
    }

    if (!pagination.metadataPrefix || !config.oaiConfig.schemas[pagination.metadataPrefix]) {
      result.errors.add([code: 'cannotDisseminateFormat', name: 'metadataPrefix', expl: 'Metadata format missing or not supported'])
      result.returnAttrs = false
    }

    result.query = result.query + query_additions
  }

  private void processResumptionToken(pagination, result, config, params) {
    def knownPrefixes = config.oaiConfig.schemas.keySet()
    List rtc = params.resumptionToken.split('\\|')

    log.debug("Got resumption: ${rtc}")

    if (rtc.size() > 3 && rtc.size() <= 5) {
      if (knownPrefixes.contains(rtc[0])) {
        pagination.metadataPrefix = rtc[0]
        rtc = rtc.drop(1)
      }
      else {
        log.debug("Incoming resumptionToken is using old offset method ..")
        pagination.no_offset_rt = false

        pagination.min_id = 1L
      }

      if (rtc[0].trim()) {
        def fparam = rtc[0]

        if (fparam.length() == 20) {
          fparam = fparam.substring(0, 18) + ".000Z"
        }

        try {
          pagination.from = dateFormatService.parseIsoMsTimestamp(fparam)
        }
        catch (Exception pe) {
          result.errors.add([code:'badResumptionToken', name: 'resumptionToken', expl: 'Illegal form of resumption token'])
        }
      }

      if (rtc[1].trim()) {
        def tparam = rtc[0]

        if (tparam.length() == 20) {
          tparam = tparam.substring(0, 18) + ".000Z"
        }

        try {
          pagination.until = dateFormatService.parseIsoMsTimestamp(tparam)
        }
        catch (Exception pe) {
          result.errors.add([code:'badResumptionToken', name: 'resumptionToken', expl: 'Illegal form of resumption token'])
        }
      }

      if (rtc[2].trim()) {
        if (!pagination.no_offset_rt) {
          pagination.offset = Long.parseLong(rtc[2])
        }
        else {
          pagination.min_id = Long.parseLong(rtc[2])
        }
      }

      if (rtc[3].trim()) {
        if (!pagination.no_offset_rt) {
          pagination.metadataPrefix = rtc[3]
        }
        else {
          pagination.offset = Long.parseLong(rtc[3])
        }

        log.debug("Resume from cursor ${pagination.offset} using prefix ${pagination.metadataPrefix}")
      }
    }
    else {
      result.errors.add([code:'badResumptionToken', name: 'resumptionToken', expl: 'Unexpected number of components in resumption token'])
      log.error("Unexpected number of components in resumption token: ${rtc}")
    }
  }

  private void handleSetFilters(result, setFilters, items) {
    Boolean wClause = result.query.contains('where')
    String query_additions = ""

    items.each { ps ->
      def set_parts = ps.split(':')

      if (set_parts?.size() > 1 && setFilters.containsKey(set_parts[1])) {
        setFilters[set_parts[1]] << set_parts[2]
      }

      if (set_parts.size() == 4 && set_parts[3] == 'local') {
        setFilters.local_only = true
      }
    }

    result.query = result.query + query_additions
  }

  private void processValidityFilters(result, setFilters) {
    Boolean wClause = result.query.contains('where')

    if (!setFilters.local_only) {
      List vl_objects = []
      RefdataValue rdv_local = RefdataCategory.lookup('Package.Global', 'Local')
      Boolean nonlocal_only = false

      setFilters.validity.each { vl ->
        if (vl == 'nonlocal') {
          nonlocal_only = true
        }
        else {
          RefdataValue rdv = RefdataCategory.lookup('Package.Global', vl)

          if (rdv) {
            vl_objects << rdv
          }
        }
      }

      if (nonlocal_only) {
        if (!wClause){
          query_additions += 'where '
          wClause = true
        }
        else{
          query_additions += ' and '
        }

        query_additions += 'o.global != :local'
        result.query_params.put('local', rdv_local)
        wClause = true
      }
      else if (vl_objects) {
        if (!wClause){
          query_additions += 'where '
          wClause = true
        }
        else{
          query_additions += ' and '
        }

        query_additions += 'o.global in (:global)'
        result.query_params.put('global', vl_objects)
        wClause = true
      }
    }
    else {
      if (!wClause){
        query_additions += 'where '
        wClause = true
      }
      else{
        query_additions += ' and '
      }

      if (val instanceof String) {
        query_additions += 'o.global = :local'
        result.query_params.put('local', rdv_local)
        wClause = true
      }
    }
  }

  private void processContentTypeFilters(result, setFilters) {
    Boolean wClause = result.query.contains('where')
    List ct_objects = []

    setFilters.content.each { ct ->
      RefdataValue rdv = RefdataCategory.lookup('Package.ContentType', ct.replaceAll('_', ' '))

      if (rdv) {
        ct_objects << rdv
      }
    }

    if (ct_objects) {
      if (!wClause){
        query_additions += 'where '
      }
      else {
        query_additions += ' and '
      }

      if (val instanceof String) {
        query_additions += 'o.contentType in (:content)'
        result.query_params.put('content', ct_objects)
      }
    }
  }
}