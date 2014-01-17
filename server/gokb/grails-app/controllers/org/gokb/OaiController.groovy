package org.gokb

import grails.converters.*
import grails.plugins.springsecurity.Secured
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.gokb.cred.*
import groovy.xml.MarkupBuilder
import groovy.xml.StreamingMarkupBuilder

class OaiController {

  def grailsApplication
  def genericOIDService

  // JSON.registerObjectMarshaller(DateTime) {
  //     return it?.toString("yyyy-MM-dd'T'HH:mm:ss'Z'")
  // }

  def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

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
            result.oaiConfig = o
            
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
          defaut:
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
    
    // Add the metadata element and populate it depending on the config.
    builder.'oai:metadata'() {
      "${prefix}" (
        "xmlns:${prefix}" : "${config.metadataNamespace}",
        "xsi:schemaLocation" : "${config.metadataNamespace}") {
          subject."${config.methodName}" (builder)
      }
      
      'oai_dc:dc'(
        'xmlns:oai_dc' : "http://www.openarchives.org/OAI/2.0/oai_dc/",
        'xmlns:dc' : "http://purl.org/dc/elements/1.1/",
        'xsi:schemaLocation' : "http://www.openarchives.org/OAI/2.0/oai_dc/ http://www.openarchives.org/OAI/2.0/oai_dc.xsd") {
          'dc:description' (result.oaiConfig.textDescription)
      }
    }
  } 

  def getRecord(result) {

    log.debug("getRecord - ${result}");

    def oid = params.identifier
    def record = genericOIDService.resolveOID(oid);

    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)

    def prefixHandler = result.oaiConfig.schemas[params.metadataPrefix]

    log.debug("prefix handler for ${params.metadataPrefix} is ${params.metadataPrefix}");

    if ( record && prefixHandler ) {
      xml.'oai:OAI-PMH'('xmlns' : 'http://www.openarchives.org/OAI/2.0/',
                      'xmlns:oai' : 'http://www.openarchives.org/OAI/2.0/',
                      'xmlns:xsi' : 'http://www.w3.org/2001/XMLSchema-instance',
                      'xsi:schemaLocation' : 'http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd') {
        'oai:responseDate'( sdf.format(new Date()) )
        'oai:request'('verb':'GetRecord', 'identifier':params.id, 'metadataPrefix':params.metadataPrefix, request.forwardURI+'?'+request.queryString)
        'oai:GetRecord'() {
          'oai:record'() {
            'oai:header'() {
              identifier(oid)
              datestamp(sdf.format(record.lastUpdated))
            }
            buildMetadata(record, xml, result, params.metadataPrefix, prefixHandler)
          }
        }
      }
    }
    else {
      // error response
    }

    log.debug("Created XML, write");

    render(text: writer.toString(), contentType: "text/xml", encoding: "UTF-8")
  }

  def identify(result) {
    
    // Get the information needed to describe this entry point.
    def obj = KBComponent.executeQuery("from ${result.className} as o ORDER BY ${result.oaiConfig.lastModified} ASC", [], [max:1, readOnly:true])[0];
    
    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)

    xml.'oai:OAI-PMH'('xmlns'                 : 'http://www.openarchives.org/OAI/2.0/',
                      'xmlns:oai'             : 'http://www.openarchives.org/OAI/2.0/',
                      'xmlns:xsi'             : 'http://www.w3.org/2001/XMLSchema-instance',
                      'xsi:schemaLocation'    : 'http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd') {
        'oai:responseDate'( sdf.format(new Date()) )
        'oai:request'('verb':'Identify', request.forwardURI+'?'+request.queryString)
        'oai:Identify'() {
          'oai:repositoryName'("GOKb ${result.oaiConfig.id}")
          'oai:baseURL'(new URL(
            request.scheme, 
            request.serverName, 
            request.serverPort,
            request.forwardURI
          ))
          'oai:protocolVersion'('2.0')
          'oai:adminEmail'('admin@gokb.org')
          'oai:earliestDatestamp'(sdf.format(obj."${result.oaiConfig.lastModified}"))
          'oai:deletedRecord'('transient')
          'oai:granularity'('YYYY-MM-DDThh:mm:ssZ')
          'oai:compression'('deflate')
          'oai:description'() {
            'oai_dc:dc'(
                  'xmlns:oai_dc' : "http://www.openarchives.org/OAI/2.0/oai_dc/",
                  'xmlns:dc' : "http://purl.org/dc/elements/1.1/",
                  'xsi:schemaLocation' : "http://www.openarchives.org/OAI/2.0/oai_dc/ http://www.openarchives.org/OAI/2.0/oai_dc.xsd") {
                'dc:description' (result.oaiConfig.textDescription)
            }
//            'oai-id:identifier'(
//                 'xmlns:oai-id'  : "http://www.openarchives.org/OAI/2.0/oai-identifier",
//                 'xsi:schemaLocation' : "http://www.openarchives.org/OAI/2.0/oai-identifier http://www.openarchives.org/OAI/2.0/oai-identifier.xsd") {
//              'oai-id:scheme'('oai')
//              'oai-id:repositoryIdentifier'("${result.className}")
//              'oai-id:delimiter'(':')
//              'oai-id:sampleIdentifier'("${result.className}:${obj.id}")
//            }
          }
        }
        
//        <description>
//        <oai-identifier
//          xmlns="http://www.openarchives.org/OAI/2.0/oai-identifier";
//          xmlnssi="http://www.w3.org/2001/XMLSchema-instance";
//          xsichemaLocation=
//              "http://www.openarchives.org/OAI/2.0/oai-identifier
//          http://www.openarchives.org/OAI/2.0/oai-identifier.xsd">;
//          <scheme>oai</scheme>
//          <repositoryIdentifier>lcoa1.loc.gov</repositoryIdentifier>
//          <delimiter>:</delimiter>
//          <sampleIdentifier>oai:lcoa1.loc.gov:loc.music/musdi.002</sampleIdentifier>
//        </oai-identifier>
//      </description>
    }
    render(text: writer.toString(), contentType: "text/xml", encoding: "UTF-8")
  }

  def listIdentifiers(result) {
    def writer = new StringWriter()
    def xml = new StreamingMarkupBuilder()

    def resp =  { mkp ->
      'oai:OAI-PMH'(
          'xmlns':'http://www.openarchives.org/OAI/2.0/',
          'xmlns:oai':'http://www.openarchives.org/OAI/2.0/',
          'xmlns:xsi':'http://www.w3.org/2001/XMLSchema-instance') {
        'oai:responseDate'( sdf.format(new Date()) )
        'oai:request'('verb':'ListIdentifiers', request.forwardURI+'?'+request.queryString)
        'oai:ListIdentifiers'() {
          
          result.oaiConfig.schemas.each { prefix, conf ->
            'oai:metadataFormat' () {
              'metadataPrefix' ("${prefix}")
              'schema' ("${conf.schema}")
              'metadataNamespace' ("${conf.metadataNamespace}")
            }
          }
        }
      }
    }

    writer << xml.bind(resp)

    render(text: writer.toString(), contentType: "text/xml", encoding: "UTF-8")
  }

  def listMetadataFormats(result) {
    def writer = new StringWriter()
    def xml = new StreamingMarkupBuilder()

    def resp =  { mkp ->
      'oai:OAI-PMH'(
          'xmlns':'http://www.openarchives.org/OAI/2.0/',
          'xmlns:oai':'http://www.openarchives.org/OAI/2.0/',
          'xmlns:xsi':'http://www.w3.org/2001/XMLSchema-instance') {
        'oai:responseDate'( sdf.format(new Date()) )
        'oai:request'('verb':'ListMetadataFormats', request.forwardURI+'?'+request.queryString)
        'oai:ListMetadataFormats'() {
          
          result.oaiConfig.schemas.each { prefix, conf ->
            'oai:metadataFormat' () {
              'metadataPrefix' ("${prefix}")
              'schema' ("${conf.schema}")
              'metadataNamespace' ("${conf.metadataNamespace}")
            }
          }
        }
      }
    }

    writer << xml.bind(resp)

    render(text: writer.toString(), contentType: "text/xml", encoding: "UTF-8")
  }


  def listRecords(result) {
    response.contentType = "application/xml"
    response.setCharacterEncoding("UTF-8");
    def out = response.outputStream

    out.withWriter { writer ->

      // def writer = new StringWriter()
      def xml = new StreamingMarkupBuilder()
      def offset = 0;
      def resumption = null
      def metadataPrefix = null

      if ( ( params.resumptionToken != null ) && ( params.resumptionToken.length() > 0 ) ) {
        def rtc = params.resumptionToken.split('\\|');
        log.debug("Got resumption: ${rtc}")
        if ( rtc.length == 4 ) {
          if ( rtc[0].length() > 0 ) {
          }
          if ( rtc[1].length() > 0 ) {
          }
          if ( rtc[2].length() > 0 ) {
            offset=Long.parseLong(rtc[2]);
          }
          if ( rtc[3].length() > 0 ) {
            metadataPrefix=rtc[3];
          }
          log.debug("Resume from cursor ${offset} using prefix ${metadataPrefix}");
        }
        else {
          log.error("Unexpected number of components in resumption token: ${rtc}");
        }
      }
      else {
        metadataPrefix = params.metadataPrefix
      }

      def prefixHandler = result.oaiConfig.schemas[metadataPrefix]

      // This bit of the query needs to come from the oai config in the domain class
      def query_params = []
      // def query = " from Package as p where p.status.value != 'Deleted'"
      def query = result.oaiConfig.query

      if ((params.from != null)&&(params.from.length()>0)) {
        query += ' and o.lastUpdated > ?'
        query_params.add(sdf.parse(params.from))
      }
      if ((params.until != null)&&(params.until.length()>0)) {
        query += ' and o.lastUpdated < ?'
        query_params.add(sdf.parse(params.until))
      }
      query += ' order by o.lastUpdated'

      log.debug("prefix handler for ${metadataPrefix} is ${prefixHandler}");
      def rec_count = Package.executeQuery("select count(o) ${query}",query_params)[0];
      def records = Package.executeQuery("select o ${query}",query_params,[offset:offset,max:3])

      log.debug("rec_count is ${rec_count}, records_size=${records.size()}");

      if ( offset + records.size() < rec_count ) {
        // Query returns more records than sent, we will need a resumption token
        resumption="${params.from?:''}|${params.until?:''}|${offset+records.size()}|${metadataPrefix}"
      }

      if ( prefixHandler ) {
        log.debug("Calling prefix handler...");
        def resp =  { mkp ->
          'oai:OAI-PMH'('xmlns':'http://www.openarchives.org/OAI/2.0/', 
                        'xmlns:oai':'http://www.openarchives.org/OAI/2.0/', 
                        'xmlns:xsi':'http://www.w3.org/2001/XMLSchema-instance') {
            'oai:responseDate'( sdf.format(new Date()) )
            'oai:request'('verb':'ListRecords', 'identifier':params.id, 'metadataPrefix':params.metadataPrefix, request.forwardURI+'?'+request.queryString)
            'oai:ListRecords'() {
              records.each { rec ->
                'oai:record'() {
                  'oai:header'() {
                    identifier("${rec.class.name}:${rec.id}")
                    datestamp(sdf.format(rec.lastUpdated))
                  }
                  buildMetadata(rec, mkp, result, metadataPrefix, prefixHandler)
                }
              }
              if ( resumption != null ) {
                'oai:resumptionToken'(completeListSize:rec_count, cursor:offset, resumption);
              }
            }
          }
        }
        log.debug("prefix handler complete..... write");
  
        writer << xml.bind(resp)
      }

      log.debug("Render");
    }
  }

  def listSets(result) {

    def writer = new StringWriter()
    def xml = new StreamingMarkupBuilder()
    def resp =  { mkp ->
      'oai:OAI-PMH'('xmlns':'http://www.openarchives.org/OAI/2.0/', 
                      'xmlns:oai':'http://www.openarchives.org/OAI/2.0/', 
                      'xmlns:xsi':'http://www.w3.org/2001/XMLSchema-instance') {
        'oai:responseDate'( sdf.format(new Date()) )
        'oai:request'('verb':'ListSets', request.forwardURI+'?'+request.queryString)
        
        // For now we are not supporting sets...
        'oai:error'('code' : "noSetHierarchy", "This repository does not support sets" )
      }
    }

    writer << xml.bind(resp)

    render(text: writer.toString(), contentType: "text/xml", encoding: "UTF-8")
  }

}
