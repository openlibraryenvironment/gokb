package org.gokb


import org.gokb.cred.*
import groovy.xml.MarkupBuilder
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import java.time.Duration
import java.time.Instant

class OaiController {

  def genericOIDService
  def dateFormatService

  // JSON.registerObjectMarshaller(DateTime) {
  //     return it?.toString("yyyy-MM-dd'T'HH:mm:ss'Z'")
  // }

  def index() {
    def result = [:]

    log.debug("index (${params})");

    if ( params.id ) {
      grailsApplication.getArtefacts("Domain").find { dc ->
        def r = false
        def cfg = dc.clazz.declaredFields.find { it.name == 'oaiConfig' }
        if ( cfg ) {
          log.debug("has config");

          def o = dc.clazz.oaiConfig
          if ( o.id == params.id ) {

            def defaultOaiConfig = [
              lastModified:'lastUpdated',
              schemas:[
                'oai_dc':[
                  type:'method',
                  methodName:'toOaiDcXml',
                  schema:'http://www.openarchives.org/OAI/2.0/oai_dc.xsd',
                  metadataNamespaces: [
                    '_default_' : 'http://www.openarchives.org/OAI/2.0/oai_dc/',
                    'dc'        : "http://purl.org/dc/elements/1.1/"
                  ]],
                'gokb':[
                  type:'method',
                  methodName:'toGoKBXml',
                  schema:'http://www.gokb.org/schemas/oai_metadata.xsd',
                  metadataNamespaces: [
                    '_default_': 'http://www.gokb.org/oai_metadata/'
                  ]],
              ]
            ]

            // Combine the default props with the locally set ones.
            result.oaiConfig = defaultOaiConfig + o

            if (params.id == 'packages' && grailsApplication.config.gokb.packageOaiCaching.enabled) {
              result.oaiConfig.lastModified = 'lastCachedDate'
            }

            // Also add the class name.
            result.className = dc.clazz.name
            r = true
          }
        }
        r
      }

      if ( result.oaiConfig ) {
        switch ( params.verb?.toLowerCase() ) {
          case 'getrecord':
            getRecord(result);
            break;
          case 'identify':
            identify(result);
            break;
          case 'listidentifiers':
            listIdentifiers(result);
            break;
          case 'listmetadataformats':
            listMetadataFormats(result);
            break;
          case 'listrecords':
            listRecords(result);
            break;
          case 'listsets':
            listSets(result);
            break;
          default:
            if(params.verb) {
              badVerb(result);
            }
            break;
        }
        log.debug("done");
      }
      else {
        // Unknown OAI config
      }
    }
  }

  private def buildMetadata (subject, builder, result, prefix, config) {
    log.debug("buildMetadata....");

    // def attr = ["xsi:schemaLocation" : "${config.schema}"]
    def attr = [:]
    def newCache = false
    File dir = new File(grailsApplication.config.getProperty('gokb.packageXmlCacheDirectory'))

    if (!dir.exists()) {
      dir.mkdirs()
    }

    def cachedXml = null

    config.metadataNamespaces.each {ns, url ->
      ns = (ns == '_default_' ? '' : ":${ns}")

      attr["xmlns${ns}"] = url
    }

    log.debug("proceed...");

    // Add the metadata element and populate it depending on the config.
    builder.'metadata'() {
      if (subject.class == Package && grailsApplication.config.getProperty('gokb.packageOaiCaching.enabled', Boolean, false)) {
        def currentFile = null

        while (!currentFile) {
          for (File file : dir.listFiles()) {
            if (file.name.contains(subject.uuid)) {
              currentFile = file
            }
          }

          if(!currentFile) {
            sleep(1000)
          }
        }

        mkp.yieldUnescaped XmlUtil.serialize(new XmlParser(false, false).parse(currentFile)).minus('<?xml version=\"1.0\" encoding=\"UTF-8\"?>')
      }
      else {
        subject."${config.methodName}" (builder, attr)
      }
    }
    log.debug("buildMetadata.... done");
  }

  private def buildHeader(record, builder, result, request) {
    Boolean cachedPackageResponse = (result.oaiConfig.id == 'packages' && grailsApplication.config.getProperty('gokb.packageOaiCaching.enabled', Boolean, false))
    def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')

    builder.'header'() {
      identifier("${record.class.name}:${record.id}")

      if (result.oaiConfig.uriPath) {
        uri(request.serverPort == 80 ? new URL(request.scheme, request.serverName, "${result.oaiConfig.uriPath}/${record.uuid}") : new URL(request.scheme, request.serverName, request.serverPort, "${result.oaiConfig.uriPath}/${record.uuid}"))
      }

      uuid(record.uuid)
      datestamp(dateFormatService.formatIsoTimestamp(cachedPackageResponse ? record.lastCachedDate : (record.lastUpdated ?: record.dateCreated)))

      if (record.status == status_deleted) {
        status('deleted')
      }

      if (record.class.simpleName == 'Package') {
        if (!record.global || record.global?.value != 'Local') {
          builder.'set' ("package:validity:nonlocal")
        }

        if (record.global) {
          builder.'set' ("package:validity:${record.global.value.toLowerCase()}")
        }

        record.curatoryGroups.each {
          builder.'set' ("package:curator:${it.id}${record.global?.value == 'Local' ? ':local' : ''}")
        }

        if (record.contentType) {
          builder.'set' ("package:content:${record.contentType.value.toLowerCase()}")
        }
      }
    }
  }

  def getRecord(result) {
    log.debug("getRecord - ${result}");
    response.contentType = "text/xml"
    response.setCharacterEncoding("UTF-8");

    try {
      def errors = []
      def oid = params.identifier
      def record = null
      Boolean returnAttrs = true
      Boolean cachedPackageResponse = (result.oaiConfig.id == 'packages' && grailsApplication.config.getProperty('gokb.packageOaiCaching.enabled', Boolean, false))
      def request_map = params
      def legalClassNames = (result.className == 'org.gokb.cred.TitleInstance' ? ['org.gokb.cred.TitleInstance', 'org.gokb.cred.BookInstance', 'org.gokb.cred.JournalInstance', 'org.gokb.cred.DatabaseInstance', 'org.gokb.cred.OtherInstance'] : [result.className])
      request_map.keySet().removeAll(['controller','action','id'])

      if (oid) {
        record = KBComponent.findByUuid(oid)

        if (record && !legalClassNames.contains(record.class.name)) {
          record = null
          errors.add([code:'idDoesNotExist', name: 'identifier', expl: 'The value of the identifier argument is unknown for this endpoint.'])
        }
        else if (!record) {
          record = genericOIDService.resolveOID(oid)
        }

        if (!record && errors.size() == 0) {
          errors.add([code:'idDoesNotExist', name: 'identifier', expl: 'The value of the identifier argument is unknown or illegal in this repository.'])
        }
        else if (record && cachedPackageResponse && !record.lastCachedDate) {
          errors.add([code:'idDoesNotExist', name: 'identifier', expl: 'The requested resource is not yet ready for exchange. Please try again later.'])
        }
      }
      else {
        errors.add([code:'badArgument', name: 'identifier', expl: 'The request is missing a mandatory argument.'])
        returnAttrs = false
      }

      def out = response.outputStream

      out.withWriter { writer ->
        def xml = new StreamingMarkupBuilder()


        def prefixHandler = result.oaiConfig.schemas[params.metadataPrefix]

        log.debug("Using prefixHandler ${prefixHandler}")

        if( !params.metadataPrefix || !prefixHandler ) {
          errors.add([code:'badArgument', name: 'metadataPrefix', expl: 'Metadata format missing or not supported'])
          returnAttrs = false
        }

        log.debug("prefix handler for ${params.metadataPrefix} is ${params.metadataPrefix}");

        def resp =  { mkp ->
          'OAI-PMH'('xmlns':'http://www.openarchives.org/OAI/2.0/',
          'xmlns:xsi':'http://www.w3.org/2001/XMLSchema-instance',
          'xsi:schemaLocation':'http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd') {
            'responseDate'( dateFormatService.formatIsoTimestamp(new Date()) )

            if(errors) {
              if (returnAttrs) {
                'request'(request_map, request.requestURL)
              }else {
                'request'(request.requestURL)
              }

              errors.each { er ->
                'error' (code: er.code, parameter: er.name, er.expl)
              }
            }
            else {
              'request'(request_map, request.requestURL)
              'GetRecord'() {
                mkp.'record'() {
                  buildHeader(record, mkp, result, request)
                  buildMetadata(record, mkp, result, params.metadataPrefix, prefixHandler)
                }
              }
            }
          }
        }

        writer << xml.bind(resp)
      }
    }
    catch (java.io.IOException e) {
      log.debug("Request cancelled ..")
    }
  }

  def identify(result) {

    // Get the information needed to describe this entry point.
    def first_timestamp = KBComponent.executeQuery("select ${result.oaiConfig.lastModified} from ${result.className} as o ORDER BY ${result.oaiConfig.lastModified} ASC".toString(), [], [max:1, readOnly:true])[0];
    def last_timestamp = KBComponent.executeQuery("select ${result.oaiConfig.lastModified} from ${result.className} as o ORDER BY ${result.oaiConfig.lastModified} DESC".toString(), [], [max:1, readOnly:true])[0];

    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)

    xml.'OAI-PMH'('xmlns'   : 'http://www.openarchives.org/OAI/2.0/',
    'xmlns:xsi'             : 'http://www.w3.org/2001/XMLSchema-instance',
    'xsi:schemaLocation'    : 'http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd') {
      'responseDate'( dateFormatService.formatIsoTimestamp(new Date()) )
      'request'('verb':'Identify', request.requestURL)
      'Identify'() {
        'repositoryName'("GOKb ${result.oaiConfig.id}")
        'baseURL'(new URL(
            request.scheme,
            request.serverName,
            request.serverPort,
            request.forwardURI
            ))
        'protocolVersion'('2.0')
        'adminEmail'('admin@gokb.org')
        'earliestDatestamp'(dateFormatService.formatIsoTimestamp(first_timestamp))
        'lastDatestamp'(dateFormatService.formatIsoTimestamp(last_timestamp))
        'deletedRecord'('transient')
        'granularity'('YYYY-MM-DDThh:mm:ssZ')
        'compression'('deflate')
        'description'() {
          'dc'(
                'xmlns' : "http://www.openarchives.org/OAI/2.0/oai_dc/",
                'xmlns:dc' : "http://purl.org/dc/elements/1.1/",
                'xsi:schemaLocation' : "http://www.openarchives.org/OAI/2.0/oai_dc/ http://www.openarchives.org/OAI/2.0/oai_dc.xsd") {
              'dc:description' (result.oaiConfig.textDescription)
          }
        }
      }
    }
    render(text: writer.toString(), contentType: "text/xml", encoding: "UTF-8")
  }

  def listIdentifiers(result) {
    def writer = new StringWriter()
    def xml = new StreamingMarkupBuilder()

    def offset = 0;
    def resumption = null
    def metadataPrefix = null
    def errors = []
    def from = null
    def until = null
    def rec_count = null
    def records = []
    def order_by_clause = 'order by o.lastUpdated'
    def returnAttrs = true
    def request_map = params
    request_map.keySet().removeAll(['controller','action','id'])

    if ( ( params.resumptionToken != null ) && ( params.resumptionToken.trim() ) ) {
      def rtc = params.resumptionToken.split('\\|');
      log.debug("Got resumption: ${rtc}")
      if ( rtc.length == 4 ) {
        if ( rtc[0].trim() ) {
          try {
            from = dateFormatService.parseIsoTimestamp(rtc[0])
          }
          catch (Exception pe) {
            errors.add([code:'badResumptionToken', name: 'resumptionToken', expl: 'Illegal form of resumption token'])
          }
        }
        if ( rtc[1].trim() ) {
          try {
            until = dateFormatService.parseIsoTimestamp(rtc[1])
          }
          catch (Exception pe) {
            errors.add([code:'badResumptionToken', name: 'resumptionToken', expl: 'Illegal form of resumption token'])
          }
        }
        if ( rtc[2].trim() ) {
          offset=Long.parseLong(rtc[2]);
        }
        if ( rtc[3].trim() ) {
          metadataPrefix=rtc[3];
        }
        log.debug("Resume from cursor ${offset} using prefix ${metadataPrefix}");
      }
      else {
        errors.add([code:'badResumptionToken', name: 'resumptionToken', expl: 'Unexpected number of components in resumption token'])
        log.error("Unexpected number of components in resumption token: ${rtc}");
      }
    }
    else {
      metadataPrefix = params.metadataPrefix
    }

    def prefixHandler = result.oaiConfig.schemas[metadataPrefix]

    if (!prefixHandler) {
      errors.add([code: 'cannotDisseminateFormat', name: 'metadataPrefix', expl: 'Metadata format missing or not supported'])
    }


    // This bit of the query needs to come from the oai config in the domain class
    def query_params = []
    // def query = " from Package as p where p.status.value != 'Deleted'"
    def query = result.oaiConfig.query
    def wClause = false

    if (from) {
      if (!wClause) {
        query += 'where '
        wClause = true
      }
      else{
        query += ' and '
      }
      query += 'o.lastUpdated > ?'
      query_params.add(from)
    }
    else if (params.from != null && params.from.trim()) {
      def fparam = params.from

      if (params.from.length() == 10) {
        fparam += 'T00:00:00Z'
      }

      try {
        from = dateFormatService.parseIsoTimestamp(fparam)

        if (!wClause) {
          query += 'where '
          wClause = true
        }
        else{
          query += ' and '
        }

        query += 'o.lastUpdated > ?'

        query_params.add(from)
      }
      catch (Exception pe) {
        errors.add([code:'badArgument', name: 'from', expl: 'This date format is not supported.'])
        returnAttrs = false
      }
    }

    if (until) {
      if (!wClause) {
        query += 'where '
        wClause = true
      }
      else{
        query += ' and '
      }
      query += 'o.lastUpdated < ?'
      query_params.add(until)
    }
    else if (params.until != null && params.until.trim()) {
      def uparam = params.until

      if (params.until.length() == 10) {
        uparam += 'T00:00:00Z'
      }

      try {
        until = dateFormatService.parseIsoTimestamp(uparam)

        if (!wClause) {
          query += 'where '
          wClause = true
        }
        else {
          query += ' and '
        }

        query += 'o.lastUpdated < ?'

        query_params.add(until)
      }
      catch (Exception pe) {
        errors.add([code:'badArgument', name: 'until', expl: 'This date format is not supported.'])
        returnAttrs = false
      }
    }

    if (errors) {
      log.debug("Request had errors .. not executing query!")
    }
    else {
      rec_count = Package.executeQuery("select count(o) ${query}".toString(),query_params)[0];
      records = Package.executeQuery("select o ${query} ${order_by_clause}".toString(),query_params,[offset:offset,max:params.int('max')?:100])

      log.debug("${query} rec_count is ${rec_count}, records_size=${records.size()}");

      if (offset + records.size() < rec_count) {
        // Query returns more records than sent, we will need a resumption token
        resumption = "${from?dateFormatService.formatIsoTimestamp(from):''}|${until?dateFormatService.formatIsoTimestamp(until):''}|${offset+records.size()}|${metadataPrefix}"
      }
    }

    def resp =  { mkp ->
      'OAI-PMH'('xmlns':'http://www.openarchives.org/OAI/2.0/',
      'xmlns:xsi':'http://www.w3.org/2001/XMLSchema-instance',
      'xsi:schemaLocation'    : 'http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd') {
        'responseDate'( dateFormatService.formatIsoTimestamp(new Date()) )

        if (errors) {
          if (returnAttrs) {
            'request'(request_map, request.requestURL)
          }else {
            'request'(request.requestURL)
          }

          errors.each { er ->
            'error' (code: er.code, parameter: er.name, er.expl)
          }
        }
        else {
          'request'(request_map, request.requestURL)
          'ListIdentifiers'() {
            records.each { rec ->
              mkp.'header'() {
                identifier("${rec.class.name}:${rec.id}")
                if (result.oaiConfig.uriPath) {
                  uri(request.serverPort == 80 ? new URL(request.scheme, request.serverName, "${result.oaiConfig.uriPath}/${rec.uuid}") : new URL(request.scheme, request.serverName, request.serverPort, "${result.oaiConfig.uriPath}/${rec.uuid}"))
                }
                uuid(rec.uuid)
                datestamp(dateFormatService.formatIsoTimestamp(rec.lastUpdated))
              }
            }

            if ( resumption != null ) {
              'resumptionToken'(completeListSize:rec_count, cursor:offset, resumption)
            }
            else if (params.resumptionToken) {
              'resumptionToken'(completeListSize:rec_count, cursor:offset)
            }
          }
        }
      }
    }
    log.debug("prefix handler complete..... write");

    writer << xml.bind(resp)

    log.debug("Render");
    render(text: writer.toString(), contentType: "text/xml", encoding: "UTF-8")
  }

  def listMetadataFormats(result) {
    def writer = new StringWriter()
    def xml = new StreamingMarkupBuilder()

    def resp =  { mkp ->
      mkp.'OAI-PMH'(
          'xmlns':'http://www.openarchives.org/OAI/2.0/',
          'xmlns:xsi':'http://www.w3.org/2001/XMLSchema-instance') {
            'responseDate'( dateFormatService.formatIsoTimestamp(new Date()) )
            'request'('verb':'ListMetadataFormats', request.requestURL)
            'ListMetadataFormats'() {

              result.oaiConfig.schemas.each { prefix, conf ->
                mkp.'metadataFormat' () {
                  'metadataPrefix' ("${prefix}")
                  'schema' ("${conf.schema}")
                  'metadataNamespace' ("${conf.metadataNamespaces['_default_']}")
                }
              }
            }
          }
    }

    writer << xml.bind(resp)

    render(text: writer.toString(), contentType: "text/xml", encoding: "UTF-8")
  }


  def listRecords(result) {
    response.contentType = "text/xml"
    response.setCharacterEncoding("UTF-8");

    try {
      def out = response.outputStream

      out.withWriter { writer ->

        // def writer = new StringWriter()
        def xml = new StreamingMarkupBuilder()
        def offset = 0;
        def resumption = null
        def metadataPrefix = null
        def errors = []
        def setFilters = [
          curator: [],
          content: [],
          validity: []
        ]
        boolean local_cg_only = false
        def from = null
        def until = null
        def max = result.oaiConfig.pageSize ?: 10
        def rec_count = null
        def records = []
        def returnAttrs = true
        def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
        def request_map = params
        def cachedPackageResponse = (result.oaiConfig.id == 'packages' && grailsApplication.config.getProperty('gokb.packageOaiCaching.enabled', Boolean, false))
        def order_by_clause = cachedPackageResponse ? 'order by o.lastCachedDate' : 'order by o.lastUpdated'
        request_map.keySet().removeAll(['controller','action','id'])

        // Check sets for package requests

        if (result.oaiConfig.id == 'packages') {
          if (params.list('set') instanceof List) {
            params.list('set').each { ps ->
              def set_parts = ps.split(':')

              if (set_parts?.size() > 1 && setFilters.containsKey(set_parts[1])) {
                setFilters[set_parts[1]] << set_parts[2]
              }

              if (set_parts.size() == 4 && set_parts[3] == 'local') {
                local_cg_only = true
              }
            }
          }
          else {
            log.debug("${params.list('set')}")
          }
        }

        if ( params.resumptionToken && ( params.resumptionToken.trim() ) ) {
          def rtc = params.resumptionToken.split('\\|')

          log.debug("Got resumption: ${rtc}")
          if ( rtc.length == 4 ) {
            if ( rtc[0].trim() ) {
              try {
                from = dateFormatService.parseIsoTimestamp(rtc[0])
              }
              catch (Exception pe) {
                errors.add([code:'badResumptionToken', name: 'resumptionToken', expl: 'Illegal form of resumption token'])
              }
            }
            if ( rtc[1].trim() ) {
              try {
                until = dateFormatService.parseIsoTimestamp(rtc[1])
              }
              catch (Exception pe) {
                errors.add([code:'badResumptionToken', name: 'resumptionToken', expl: 'Illegal form of resumption token'])
              }
            }
            if ( rtc[2].trim() ) {
              offset=Long.parseLong(rtc[2]);
            }
            if ( rtc[3].trim() ) {
              metadataPrefix=rtc[3];
            }
            log.debug("Resume from cursor ${offset} using prefix ${metadataPrefix}");
          }
          else {
            errors.add([code:'badResumptionToken', name: 'resumptionToken', expl: 'Unexpected number of components in resumption token'])
            log.error("Unexpected number of components in resumption token: ${rtc}");
          }
        }
        else {
          metadataPrefix = params.metadataPrefix
        }

        def prefixHandler = result.oaiConfig.schemas[metadataPrefix]

        if(!prefixHandler) {
          errors.add([code: 'cannotDisseminateFormat', name: 'metadataPrefix', expl: 'Metadata format missing or not supported'])
        }

        def wClause = false

        // This bit of the query needs to come from the oai config in the domain class
        def query_params = [:]
        // def query = " from Package as p where p.status.value != 'Deleted'"
        def query = result.oaiConfig.query

        def status_filter = result.oaiConfig.statusFilter
        def cg = null

        if (setFilters.curators) {
          if (setFilters.curators.size() > 1) {
            errors.add([code:'badArgument', name: 'set', expl: 'Unable to process multiple curator filter sets'])
            returnAttrs = false
          }
          else {
            def cg_id = setFilters.curators[0].split(':')

            if (cg_id.size() > 2 && cg_id[2].trim() && cg_id[2].isInteger()) {
              cg = CuratoryGroup.get(cg_id[2] as int)
            }
          }
        }
        else if (params.curator && result.oaiConfig.curators) {
          cg = CuratoryGroup.findByName(params.curator)

          if (!cg) {
            errors.add([code:'badArgument', name: 'curator', expl: 'Unable to lookup Curatory Group.'])
            returnAttrs = false
          }
        }

        if (cg) {
          query += ', Combo as cgCombo, CuratoryGroup as cg where cgCombo.toComponent = :cgo and cgCombo.type = :cgtype and cgCombo.fromComponent = o '
          wClause = true
          query_params.put('cgo', cg)
          query_params.put('cgtype', RefdataCategory.lookupOrCreate('Combo.Type', result.oaiConfig.curators))
        }

        if (params.pkg && result.oaiConfig.pkg) {
          def linked_pkg = Package.findByUuid(params.pkg)

          if (!linked_pkg) {
            try {
              linked_pkg = Package.get(genericOIDService.oidToId(params.pkg))
            }
            catch (Exception e) {

            }
          }

          if (linked_pkg) {
            query += ', Combo as pkgCombo, Package as pkg where pkgCombo.fromComponent = :lpkg and pkgCombo.type = :cpkgt and pkgCombo.toComponent = o '
            wClause = true
            query_params.put('lpkg', linked_pkg)
            query_params.put('cpkgt', RefdataCategory.lookupOrCreate('Combo.Type', result.oaiConfig.pkg))
          }
          else {
            errors.add([code:'badArgument', name: 'pkg', expl: 'Unable to lookup Package.'])
            returnAttrs = false
          }
        }

        if (cachedPackageResponse) {
          if(!wClause){
            query += 'where '
            wClause = true
          }
          else{
            query += ' and '
          }

          query += 'o.lastCachedDate is not null'
        }

        if (result.oaiConfig.id == 'tipps') {
          if(!wClause){
            query += 'where '
            wClause = true
          }
          else{
            query += ' and '
          }

          query += 'exists (select 1 from Combo as cti where cti.toComponent = o and cti.type = :ctipp)'
          def qry_cti = RefdataCategory.lookupOrCreate(Combo.RD_TYPE, 'TitleInstance.Tipps')
          query_params.put('ctipp', qry_cti)
          wClause = true
        }

        if (result.oaiConfig.id == 'packages') {
          if (!local_cg_only) {
            def vl_objects = []
            def rdv_local = RefdataCategory.lookup('Package.Global', 'Local')
            boolean nonlocal_only = false

            setFilters.validity.each { vl ->
              if (vl == 'nonlocal') {
                nonlocal_only = true
              }
              else {
                def rdv = RefdataCategory.lookup('Package.Global', vl)

                if (rdv) {
                  vl_objects << rdv
                }
              }
            }

            if (nonlocal_only) {
              if(!wClause){
                query += 'where '
                wClause = true
              }
              else{
                query += ' and '
              }

              query += 'o.global != :local'
              query_params.put('local', rdv_local)
              wClause = true
            }
            else if (vl_objects) {
              if(!wClause){
                query += 'where '
                wClause = true
              }
              else{
                query += ' and '
              }

              query += 'o.global in (:global)'
              query_params.put('global', vl_objects)
              wClause = true
            }
          }
          else {
            if(!wClause){
              query += 'where '
              wClause = true
            }
            else{
              query += ' and '
            }

            if (val instanceof String) {
              query += 'o.global = :local'
              query_params.put('local', rdv_local)
              wClause = true
            }
          }

          def ct_objects = []

          setFilters.content.each { ct ->
            def rdv = RefdataCategory.lookup('Package.ContentType', vl.replaceAll('_', ' '))

            if (rdv) {
              vl_objects << rdv
            }
          }

          if (ct_objects) {
            if(!wClause){
              query += 'where '
              wClause = true
            }
            else{
              query += ' and '
            }

            if (val instanceof String) {
              query += 'o.contentType in (:content)'
              query_params.put('content', ct_objects)
              wClause = true
            }
          }
        }

        if (status_filter && status_filter.size() > 0) {
          status_filter.eachWithIndex { val, index ->
            if(!wClause){
              query += 'where '
              wClause = true
            }
            else{
              query += ' and '
            }

            if (val instanceof String) {
              query += 'o.status != :status'
              def qry_rdc = RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, val)
              query_params.put('status', qry_rdc)
              wClause = true
            }
            else if (val instanceof org.gokb.cred.RefdataValue) {
              query += 'o.status != :status'
              query_params.put('status', val)
              wClause = true
            }
            else {
              log.warn("Unknown parameter format!")
            }
          }
        }

        if (!from && params.from != null && params.from.trim()) {
          def fparam = params.from

          if( params.from.length() == 10 ) {
            fparam += 'T00:00:00Z'
          }

          try {
            from = dateFormatService.parseIsoTimestamp(fparam)
          }
          catch (Exception pe) {
            errors.add([code:'badArgument', name: 'from', expl: 'This date format is not supported.'])
            returnAttrs = false
          }
        }

        if (!until && params.until != null && params.until.trim()) {
          def uparam = params.until

          if( params.until.length() == 10 ) {
            uparam += 'T00:00:00Z'
          }

          try {
            until = dateFormatService.parseIsoTimestamp(uparam)
          }
          catch (Exception pe) {
            errors.add([code:'badArgument', name: 'until', expl: 'This date format is not supported.'])
            returnAttrs = false
          }
        }

        if (from) {
          if (!wClause) {
            query += 'where '
            wClause = true
          }
          else {
            query += ' and '
          }

          if (cachedPackageResponse) {
            query += 'o.lastCachedDate > :lupdf'
          }
          else {
            query += 'o.lastUpdated > :lupdf'
          }

          query_params.put('lupdf', from)
        }
        if (until) {
          if (!wClause) {
            query += 'where '
            wClause = true
          }
          else {
            query += ' and '
          }

          if (cachedPackageResponse) {
            query += 'o.lastCachedDate < :lupd'
          }
          else {
            query += 'o.lastUpdated < :lupd'
          }
          query_params.put('lupd', until)
        }

        log.debug("qry is: ${query}");
        log.debug("qry params are: ${query_params}")
        log.debug("prefix handler for ${metadataPrefix} is ${prefixHandler}");

        if (errors) {
          log.debug("Request had errors .. not executing query!")
        }
        else {
          rec_count = Package.executeQuery("select count(o) ${query}".toString(),query_params)[0];
          records = Package.executeQuery("select o ${query} ${order_by_clause}".toString(),query_params,[offset:offset,max:max])

          log.debug("${query} rec_count is ${rec_count}, records_size=${records.size()}");

          if ( offset + records.size() < rec_count ) {
            // Query returns more records than sent, we will need a resumption token

            resumption = "${from?dateFormatService.formatIsoTimestamp(from):''}|${until?dateFormatService.formatIsoTimestamp(until):''}|${offset+records.size()}|${metadataPrefix}"
          }
        }

        def resp =  { mkp ->
          'OAI-PMH'('xmlns':'http://www.openarchives.org/OAI/2.0/',
          'xmlns:xsi':'http://www.w3.org/2001/XMLSchema-instance',
          'xsi:schemaLocation':'http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd') {
            'responseDate'( dateFormatService.formatIsoTimestamp(new Date()) )

            if(errors) {
              if (returnAttrs) {
                'request'(request_map, request.requestURL)
              }else {
                'request'(request.requestURL)
              }

              errors.each { er ->
                'error' (code: er.code, parameter: er.name, er.expl)
              }
            }
            else {
              'request'(request_map, request.requestURL)
              'ListRecords'() {
                records.each { rec ->
                  mkp.'record'() {
                    buildHeader(rec, mkp, result, request)
                    buildMetadata(rec, mkp, result, metadataPrefix, prefixHandler)
                  }
                }

                if ( resumption != null ) {
                  'resumptionToken'(completeListSize:rec_count, cursor:offset, resumption)
                }
                else if (params.resumptionToken) {
                  'resumptionToken'(completeListSize:rec_count, cursor:offset)
                }
              }
            }
          }
        }

        writer << xml.bind(resp)

        log.debug("Render");
      }
    }
    catch (java.io.IOException e) {
      log.debug("Request cancelled ..")
    }
  }

  def listSets(result) {

    def writer = new StringWriter()
    def xml = new StreamingMarkupBuilder()
    def resp =  { mkp ->
      'OAI-PMH'('xmlns':'http://www.openarchives.org/OAI/2.0/',
      'xmlns:xsi':'http://www.w3.org/2001/XMLSchema-instance') {
        'responseDate'( dateFormatService.formatIsoTimestamp(new Date()) )
        'request'('verb':'ListSets', request.requestURL)

        'ListSets'() {
          mkp.'set' () {
            'setSpec'('package')
            'setName'('Package')
          }
          mkp.'set' () {
            'setSpec'('package:validity')
            'setName'('Package Validity range')
          }
          mkp.'set' () {
            'setSpec'("package:validity:nonlocal")
            'setName'("Package validity range is not 'Local'")
          }
          RefdataCategory.lookup('Package.Global').each { rdv ->
            mkp.'set' () {
              'setSpec'("package:validity:${rdv.value.toLowerCase()}")
              'setName'("Package validity range ${rdv.value}")
            }
          }
          mkp.'set' () {
            'setSpec'('package:content')
            'setName'('Package content type')
          }
          RefdataCategory.lookup('Package.ContentType').each { rdv ->
            mkp.'set' () {
              'setSpec'("package:content:${rdv.value.toLowerCase()}")
              'setName'("Package content type ${rdv.value}")
            }
          }
          mkp.'set' () {
            'setSpec'('package:curator')
            'setName'('Package curator')
          }
          CuratoryGroup.list().each { cg ->
            mkp.'set' () {
              'setSpec'("package:curator:${cg.id}")
              'setName'("Package curated by '${cg.name}'")
            }
            mkp.'set' () {
              'setSpec'("package:curator:${cg.id}:local")
              'setName'("Local Package curated by '${cg.name}'")
            }
          }
        }
      }
    }

    writer << xml.bind(resp)

    render(text: writer.toString(), contentType: "text/xml", encoding: "UTF-8")
  }

  def badVerb(result) {

    def writer = new StringWriter()
    def xml = new StreamingMarkupBuilder()
    def resp =  { mkp ->
      'OAI-PMH'('xmlns':'http://www.openarchives.org/OAI/2.0/',
      'xmlns:xsi':'http://www.w3.org/2001/XMLSchema-instance') {
        'responseDate'( dateFormatService.formatIsoTimestamp(new Date()) )
        'request'(request.requestURL)

        'error'('code' : "badVerb", "Illegal OAI verb" )
      }
    }

    writer << xml.bind(resp)

    render(text: writer.toString(), contentType: "text/xml", encoding: "UTF-8")
  }

  private Date getCacheDateForPkgUuid(uuid) {
    File dir = new File(grailsApplication.config.getProperty('gokb.packageXmlCacheDirectory'))
    def cached_uuids = []

    for (File file : dir.listFiles()) {
      if (file.name.contains(uuid)) {
        return new Date(file.lastModified())
      }
    }

    return null
  }
}
