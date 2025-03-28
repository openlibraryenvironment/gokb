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
  def OAIService

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

  private def buildMetadata (subject, builder, config) {
    log.debug("buildMetadata....");

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

  private def buildHeader(record, builder, options, request) {
    Boolean cachedPackageResponse = (options.oaiConfig.id == 'packages' && grailsApplication.config.getProperty('gokb.packageOaiCaching.enabled', Boolean, false))
    def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')

    builder.'header'() {
      identifier("${record.class.name}:${record.id}")

      if (options.oaiConfig.uriPath) {
        uri(request.serverPort == 80 ? new URL(request.scheme, request.serverName, "${options.oaiConfig.uriPath}/${record.uuid}") : new URL(request.scheme, request.serverName, request.serverPort, "${options.oaiConfig.uriPath}/${record.uuid}"))
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

  def getRecord(options) {
    log.debug("getRecord - ${options}");
    response.contentType = "text/xml"
    response.setCharacterEncoding("UTF-8");

    try {
      def errors = []
      def oid = params.identifier
      def record = null
      Boolean returnAttrs = true
      Boolean cachedPackageResponse = (options.oaiConfig.id == 'packages' && grailsApplication.config.getProperty('gokb.packageOaiCaching.enabled', Boolean, false))
      def request_map = params
      def legalClassNames = (options.className == 'org.gokb.cred.TitleInstance' ?
                                ['org.gokb.cred.TitleInstance', 'org.gokb.cred.BookInstance', 'org.gokb.cred.JournalInstance', 'org.gokb.cred.DatabaseInstance', 'org.gokb.cred.OtherInstance'] :
                                [options.className])

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


        def prefixHandler = options.oaiConfig.schemas[params.metadataPrefix]

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
                  buildHeader(record, mkp, options, request)
                  buildMetadata(record, mkp, prefixHandler)
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

  def identify(options) {

    // Get the information needed to describe this entry point.
    def first_timestamp = KBComponent.executeQuery("select ${result.oaiConfig.lastModified} from ${result.className} as o ORDER BY ${result.oaiConfig.lastModified} ASC".toString(), [], [max:1, readOnly:true])[0]
    def last_timestamp = KBComponent.executeQuery("select ${result.oaiConfig.lastModified} from ${result.className} as o where ${result.oaiConfig.lastModified} IS NOT NULL ORDER BY ${result.oaiConfig.lastModified} DESC".toString(), [], [max:1, readOnly:true])[0]

    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)

    xml.'OAI-PMH'('xmlns'   : 'http://www.openarchives.org/OAI/2.0/',
    'xmlns:xsi'             : 'http://www.w3.org/2001/XMLSchema-instance',
    'xsi:schemaLocation'    : 'http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd') {
      'responseDate'( dateFormatService.formatIsoTimestamp(new Date()) )
      'request'('verb':'Identify', request.requestURL)
      'Identify'() {
        'repositoryName'("GOKb ${options.oaiConfig.id}")
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
              'dc:description' (options.oaiConfig.textDescription)
          }
        }
      }
    }
    render(text: writer.toString(), contentType: "text/xml", encoding: "UTF-8")
  }

  def listIdentifiers(options) {
    def writer = new StringWriter()
    def xml = new StreamingMarkupBuilder()
    def request_map = params

    request_map.keySet().removeAll(['controller','action','id'])

    options.oaiConfig.pageSize = 100
    options.oaiConfig.cachedPackageResponse = (options.oaiConfig.id == 'packages' && grailsApplication.config.getProperty('gokb.packageOaiCaching.enabled', Boolean, false))

    def fetch_result = OAIService.fetchRecordList(params, options)

    def resp =  { mkp ->
      'OAI-PMH'('xmlns':'http://www.openarchives.org/OAI/2.0/',
      'xmlns:xsi':'http://www.w3.org/2001/XMLSchema-instance',
      'xsi:schemaLocation'    : 'http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd') {
        'responseDate'( dateFormatService.formatIsoTimestamp(new Date()) )

        if (fetch_result.errors) {
          if (fetch_result.returnAttrs) {
            'request'(request_map, request.requestURL)
          }else {
            'request'(request.requestURL)
          }

          fetch_result.errors.each { er ->
            'error' (code: er.code, parameter: er.name, er.expl)
          }
        }
        else {
          'request'(request_map, request.requestURL)
          'ListIdentifiers'() {
            fetch_result.records.each { rec ->
              mkp.'header'() {
                identifier("${rec.class.name}:${rec.id}")

                if (options.oaiConfig.uriPath) {
                  uri(request.serverPort == 80 ?
                      new URL(request.scheme, request.serverName, "${options.oaiConfig.uriPath}/${rec.uuid}") :
                      new URL(request.scheme, request.serverName, request.serverPort, "${options.oaiConfig.uriPath}/${rec.uuid}")
                  )
                }
                uuid(rec.uuid)
                datestamp(dateFormatService.formatIsoTimestamp(rec.lastUpdated))
              }
            }

            if (fetch_result.resumption != null) {
              'resumptionToken'(completeListSize: fetch_result.rec_count, cursor: fetch_result.offset, fetch_result.resumption)
            }
            else if (params.resumptionToken) {
              'resumptionToken'(completeListSize: fetch_result.rec_count, cursor: fetch_result.offset)
            }
          }
        }
      }
    }
    log.debug("prefix handler complete..... write")

    writer << xml.bind(resp)

    log.debug("Render");
    render(text: writer.toString(), contentType: "text/xml", encoding: "UTF-8")
  }

  def listMetadataFormats(options) {
    def writer = new StringWriter()
    def xml = new StreamingMarkupBuilder()

    def resp =  { mkp ->
      mkp.'OAI-PMH'(
          'xmlns':'http://www.openarchives.org/OAI/2.0/',
          'xmlns:xsi':'http://www.w3.org/2001/XMLSchema-instance') {
            'responseDate'( dateFormatService.formatIsoTimestamp(new Date()) )
            'request'('verb':'ListMetadataFormats', request.requestURL)
            'ListMetadataFormats'() {

              options.oaiConfig.schemas.each { prefix, conf ->
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

  def listRecords(options) {
    response.contentType = "text/xml"
    response.setCharacterEncoding("UTF-8")

    try {
      def out = response.outputStream

      out.withWriter { writer ->

        // def writer = new StringWriter()
        def xml = new StreamingMarkupBuilder()
        def rec_count = null
        def records = []
        RefdataValue status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
        def request_map = params

        request_map.keySet().removeAll(['controller','action','id'])

        options.oaiConfig.cachedPackageResponse = (options.oaiConfig.id == 'packages' && grailsApplication.config.getProperty('gokb.packageOaiCaching.enabled', Boolean, false))

        def fetch_result = OAIService.fetchRecordList(params, options)

        def resp =  { mkp ->
          'OAI-PMH'('xmlns':'http://www.openarchives.org/OAI/2.0/',
          'xmlns:xsi':'http://www.w3.org/2001/XMLSchema-instance',
          'xsi:schemaLocation':'http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd') {
            'responseDate'( dateFormatService.formatIsoTimestamp(new Date()) )

            if (fetch_result.errors) {
              if (fetch_result.returnAttrs) {
                'request'(request_map, request.requestURL)
              } else {
                'request'(request.requestURL)
              }

              fetch_result.errors.each { er ->
                'error' (code: er.code, parameter: er.name, er.expl)
              }
            }
            else {
              'request'(request_map, request.requestURL)
              'ListRecords'() {
                fetch_result.records.each { rec ->
                  mkp.'record'() {
                    buildHeader(rec, mkp, options, request)
                    buildMetadata(rec, mkp, options.oaiConfig.schemas[fetch_result.pagination.metadataPrefix])
                  }
                }

                if (fetch_result.resumption != null) {
                  'resumptionToken'(completeListSize: fetch_result.rec_count, cursor: fetch_result.pagination.offset, fetch_result.resumption)
                }
                else if (params.resumptionToken) {
                  'resumptionToken'(completeListSize: fetch_result.rec_count, cursor: fetch_result.pagination.offset)
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

  def listSets(options) {

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

  def badVerb(options) {

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
