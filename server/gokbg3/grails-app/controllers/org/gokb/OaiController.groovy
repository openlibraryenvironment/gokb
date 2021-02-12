package org.gokb


import org.gokb.cred.*
import groovy.xml.MarkupBuilder
import groovy.xml.StreamingMarkupBuilder

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

            // Combine the default props with the locally set ones.
            result.oaiConfig = grailsApplication.config.defaultOaiConfig + o

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
    config.metadataNamespaces.each {ns, url ->
      ns = (ns == '_default_' ? '' : ":${ns}")

      attr["xmlns${ns}"] = url
    }

    log.debug("proceed...");

    // Add the metadata element and populate it depending on the config.
    builder.'metadata'() {
      subject."${config.methodName}" (builder, attr)
    }
    log.debug("buildMetadata.... done");
  }

  def getRecord(result) {
    // long session for possible huge requests
    request.getSession(true).setMaxInactiveInterval(12000)
    log.debug("getRecord - ${result}");

    def errors = []
    def oid = params.identifier
    def record = null
    def returnAttrs = true
    def request_map = params
    request_map.keySet().removeAll(['controller','action','id'])

    if (oid) {
      record = genericOIDService.resolveOID(oid);
    }
    else {
      errors.add([code:'badArgument', name: 'identifier', expl: 'The request is missing a mandatory argument.'])
      returnAttrs = false
    }

    if (oid && !record) {

      record = KBComponent.findByUuid(oid)


      if( !record ) {
        errors.add([code:'idDoesNotExist', name: 'identifier', expl: 'The value of the identifier argument is unknown or illegal in this repository.'])
      }
    }

    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')


    def prefixHandler = result.oaiConfig.schemas[params.metadataPrefix]

    log.debug("Using prefixHandler ${prefixHandler}")

    if( !params.metadataPrefix || !prefixHandler ) {
      errors.add([code:'badArgument', name: 'metadataPrefix', expl: 'Metadata format missing or not supported'])
      returnAttrs = false
    }

    log.debug("prefix handler for ${params.metadataPrefix} is ${params.metadataPrefix}");

    xml.'OAI-PMH'('xmlns' : 'http://www.openarchives.org/OAI/2.0/',
    'xmlns:xsi' : 'http://www.w3.org/2001/XMLSchema-instance',
    'xsi:schemaLocation' : 'http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd') {
      'responseDate'(dateFormatService.formatIsoTimestamp(new Date()) )
      if (errors) {
        if (!returnAttrs) {
          'request'(request_map, request.requestURL)
        }
        else {
          'request'(request.requestURL)
        }

        errors.each { er ->
          'error' (code: er.code, parameter: er.name, er.expl)
        }
      }
      else{
        'request'(request_map, request.requestURL)

        'GetRecord'() {
          xml.'record'() {
            xml.'header'() {
              identifier("${record.class.name}:${record.id}")
              uuid(record.uuid)
              datestamp(dateFormatService.formatIsoTimestamp(record.lastUpdated))
              if (record.status == status_deleted) {
                status('deleted')
              }
            }
            buildMetadata(record, xml, result, params.metadataPrefix, prefixHandler)
          }
        }
      }
    }

    log.debug("Created XML, write");

    render(text: writer.toString(), contentType: "text/xml", encoding: "UTF-8")
  }

  def identify(result) {

    // Get the information needed to describe this entry point.
    def obj = KBComponent.executeQuery("from ${result.className} as o ORDER BY ${result.oaiConfig.lastModified} ASC".toString(), [], [max:1, readOnly:true])[0];

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
        'earliestDatestamp'(dateFormatService.formatIsoTimestamp(obj."${result.oaiConfig.lastModified}"))
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

    if(!prefixHandler) {
      errors.add([code: 'cannotDisseminateFormat', name: 'metadataPrefix', expl: 'Metadata format missing or not supported'])
    }


    // This bit of the query needs to come from the oai config in the domain class
    def query_params = []
    // def query = " from Package as p where p.status.value != 'Deleted'"
    def query = result.oaiConfig.query
    def wClause = false

    if(from){
      if(!wClause){
        query += 'where '
        wClause = true
      }
      else{
        query += ' and '
      }
      query += 'o.lastUpdated > ?'
      query_params.add(from)
    }
    else if ((params.from != null)&&(params.from.trim())) {
      def fparam = params.from

      if( params.from.length() == 10 ) {
        fparam += 'T00:00:00Z'
      }

      try {
        from = dateFormatService.parseIsoTimestamp(fparam)

        if(!wClause){
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

    if(until){
      if(!wClause){
        query += 'where '
        wClause = true
      }
      else{
        query += ' and '
      }
      query += 'o.lastUpdated < ?'
      query_params.add(until)
    }
    else if ((params.until != null)&&(params.until.trim())) {
      def uparam = params.until

      if( params.until.length() == 10 ) {
        uparam += 'T00:00:00Z'
      }

      try {
        until = dateFormatService.parseIsoTimestamp(uparam)

        if(!wClause){
          query += 'where '
          wClause = true
        }
        else{
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

    if(errors) {
      log.debug("Request had errors .. not executing query!")
    }
    else {
      rec_count = Package.executeQuery("select count(o) ${query}".toString(),query_params)[0];
      records = Package.executeQuery("select o ${query} ${order_by_clause}".toString(),query_params,[offset:offset,max:params.int('max')?:100])

      log.debug("${query} rec_count is ${rec_count}, records_size=${records.size()}");

      if ( offset + records.size() < rec_count ) {
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
    // long session for possible huge requests
    request.getSession(true).setMaxInactiveInterval(12000)
    response.contentType = "text/xml"
    response.setCharacterEncoding("UTF-8");
    def out = response.outputStream

    out.withWriter { writer ->

      // def writer = new StringWriter()
      def xml = new StreamingMarkupBuilder()
      def offset = 0;
      def resumption = null
      def metadataPrefix = null
      def errors = []
      def from = null
      def until = null
      def max = result.oaiConfig.pageSize ?: 10
      def rec_count = null
      def records = []
      def order_by_clause = 'order by o.lastUpdated'
      def returnAttrs = true
      def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
      def request_map = params
      request_map.keySet().removeAll(['controller','action','id'])

      if ( params.resumptionToken && ( params.resumptionToken.trim() ) ) {
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

      if(!prefixHandler) {
        errors.add([code: 'cannotDisseminateFormat', name: 'metadataPrefix', expl: 'Metadata format missing or not supported'])
      }

      def wClause = false

      // This bit of the query needs to come from the oai config in the domain class
      def query_params = []
      // def query = " from Package as p where p.status.value != 'Deleted'"
      def query = result.oaiConfig.query

      def status_filter = result.oaiConfig.statusFilter

      if ( params.curator && result.oaiConfig.curators) {
        def cg = CuratoryGroup.findByName(params.curator)
        def comboType = RefdataCategory.lookupOrCreate('Combo.Type', result.oaiConfig.curators)

        if (cg) {
          query += ', Combo as cgCombo, CuratoryGroup as cg where cgCombo.toComponent = ? and cgCombo.type = ? and cgCombo.fromComponent = o '
          wClause = true
          query_params.add(cg)
          query_params.add(comboType)
        } else {
          errors.add([code:'badArgument', name: 'curator', expl: 'Unable to lookup Curatory Group.'])
          returnAttrs = false
        }
      }

      if ( params.pkg && result.oaiConfig.pkg ) {
        def linked_pkg = Package.findByUuid(params.pkg)

        if (!linked_pkg) {
          try {
            linked_pkg = Package.get(genericOIDService.oidToId(params.pkg))
          }
          catch (Exception e) {

          }
        }

        if (linked_pkg) {

          def comboType = RefdataCategory.lookupOrCreate('Combo.Type', result.oaiConfig.pkg)

          query += ', Combo as pkgCombo, Package as pkg where pkgCombo.fromComponent = ? and pkgCombo.type = ? and pkgCombo.toComponent = o '
          wClause = true
          query_params.add(linked_pkg)
          query_params.add(comboType)
        }
        else {
          errors.add([code:'badArgument', name: 'pkg', expl: 'Unable to lookup Package.'])
          returnAttrs = false
        }
      }

      if(status_filter && status_filter.size() > 0){
        status_filter.eachWithIndex { val, index ->
          if(!wClause){
            query += 'where '
            wClause = true
          }
          else{
            query += ' and '
          }

          if (val instanceof String) {
            query += 'o.status != ?'
            def qry_rdc = RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, val)
            query_params.add(qry_rdc)
            wClause = true
          }
          else if (val instanceof org.gokb.cred.RefdataValue) {
            query += 'o.status != ?'
            query_params.add(val)
            wClause = true
          }
          else {
            log.warn("Unknown parameter format!")
          }
        }
      }

      if(from){
        if(!wClause){
          query += 'where '
          wClause = true
        }
        else{
          query += ' and '
        }
        query += 'o.lastUpdated > ?'
        query_params.add(from)
      }
      else if ((params.from != null)&&(params.from.trim())) {
        def fparam = params.from

        if( params.from.length() == 10 ) {
          fparam += 'T00:00:00Z'
        }

        try {
          from = dateFormatService.parseIsoTimestamp(fparam)

          if(!wClause){
            query += 'where '
            wClause = true
          }
          else{
            query += ' and '
          }

          query += 'o.lastUpdated > ? '
                    query_params.add(from)
        }
        catch (Exception pe) {
          errors.add([code:'badArgument', name: 'from', expl: 'This date format is not supported.'])
          returnAttrs = false
        }
      }

      if(until){
        if(!wClause){
          query += 'where '
          wClause = true
        }
        else{
          query += ' and '
        }
        query += 'o.lastUpdated < ?'
        query_params.add(until)
      }
      else if ((params.until != null)&&(params.until.trim())) {
        def uparam = params.until

        if( params.until.length() == 10 ) {
          uparam += 'T00:00:00Z'
        }

        try {
          until = dateFormatService.parseIsoTimestamp(uparam)

          if(!wClause){
            query += 'where '
            wClause = true
          }
          else{
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

      log.debug("qry is: ${query}");
      log.debug("prefix handler for ${metadataPrefix} is ${prefixHandler}");

      if (errors) {
        log.debug("Request had errors .. not executing query!")
      }
      else {
        rec_count = Package.executeQuery("select count(distinct o) ${query}".toString(),query_params)[0];
        records = Package.executeQuery("select distinct o ${query} ${order_by_clause}".toString(),query_params,[offset:offset,max:max])

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
                  mkp.'header' () {
                    identifier("${rec.class.name}:${rec.id}")
                    uuid(rec.uuid)
                    datestamp(dateFormatService.formatIsoTimestamp(rec.lastUpdated))
                    if (rec.status == status_deleted) {
                      status('deleted')
                    }
                  }
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

  def listSets(result) {

    def writer = new StringWriter()
    def xml = new StreamingMarkupBuilder()
    def resp =  { mkp ->
      'OAI-PMH'('xmlns':'http://www.openarchives.org/OAI/2.0/',
      'xmlns:xsi':'http://www.w3.org/2001/XMLSchema-instance') {
        'responseDate'( dateFormatService.formatIsoTimestamp(new Date()) )
        'request'('verb':'ListSets', request.requestURL)

        // For now we are not supporting sets...
        'error'('code' : "noSetHierarchy", "This repository does not support sets" )
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
}
