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
            r = true
          }
        }
        r
      }
  
      if ( result.oaiConfig ) {
        switch ( params.verb ) {
          case 'GetRecord':
            getRecord(result);
            break;
          case 'Identify':
            identify(result);
            break;
          case 'ListIdentifiers':
            listIdentifiers(result);
            break;
          case 'ListMetadataFormats':
            listMetadataFormats(result);
            break;
          case 'ListRecords':
            listRecords(result);
            break;
          case 'ListSets':
            listSets(result);
            break;
        }
        log.debug("done");
      }
      else {
        // Unknown OAI config
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
        'oai:responseDate'('value')
        'oai:request'('verb':'GetRecord', 'identifier':params.id, 'metadataPrefix':params.metadataPrefix, request.forwardURI+'?'+request.queryString)
        'oai:GetRecord'() {
          'oai:record'() {
            'oai:header'() {
              identifier(oid)
              datestamp(sdf.format(record.lastUpdated))
            }
            'oai:metadata'() {
              record."${prefixHandler.methodName}"(xml)
            }
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
  }

  def listIdentifiers(result) {
  }

  def listRecords(result) {
    def writer = new StringWriter()
    def xml = new StreamingMarkupBuilder()
    def offset = 0;
    def resumption = null

    if ( params.resumptionToken != null ) {
      def rtc = params.resumptionToken.split('\\|');
      log.debug("Got resumption: ${rtc}")
      if ( rtc[0].length() > 0 ) {
      }
      if ( rtc[1].length() > 0 ) {
      }
      if ( rtc[2].length() > 0 ) {
        offset=Long.parseLong(rtc[2]);
      }
      
    }

    def prefixHandler = result.oaiConfig.schemas[params.metadataPrefix]

    def query_params = []
    def query = " from Package as p where p.status.value != 'Deleted'"

    if ((params.from != null)&&(params.from.length()>0)) {
      query += ' and p.lastUpdated > ?'
      query_params.add(sdf.parse(params.from))
    }
    if ((params.until != null)&&(params.until.length()>0)) {
      query += ' and p.lastUpdated < ?'
      query_params.add(sdf.parse(params.until))
    }
    query += ' order by p.lastUpdated'

    log.debug("prefix handler for ${params.metadataPrefix} is ${params.metadataPrefix}");
    def rec_count = Package.executeQuery("select count(p) ${query}",query_params)[0];
    def records = Package.executeQuery("select p ${query}",query_params,[offset:offset,max:5])

    log.debug("rec_count is ${rec_count}, records_size=${records.size()}");

    if ( offset + records.size() < rec_count ) {
      // Query returns more records than sent, we will need a resumption token
      resumption="${params.from?:''}|${params.until?:''}|${offset+records.size()}"
    }

    if ( prefixHandler ) {
      def resp =  { mkp ->
        'oai:OAI-PMH'('xmlns':'http://www.openarchives.org/OAI/2.0/', 
                      'xmlns:oai':'http://www.openarchives.org/OAI/2.0/', 
                      'xmlns:xsi':'http://www.w3.org/2001/XMLSchema-instance') {
          'oai:responseDate'('value')
          'oai:request'('verb':'GetRecord', 'identifier':params.id, 'metadataPrefix':params.metadataPrefix, request.forwardURI+'?'+request.queryString)
          'oai:ListRecords'() {
            records.each { rec ->
              'oai:record'() {
                'oai:header'() {
                  identifier("${rec.class.name}:${rec.id}")
                  datestamp(sdf.format(rec.lastUpdated))
                }
                'oai:metadata'() {
                   rec."${prefixHandler.methodName}"(mkp)
                 }
              }
            }
            if ( resumption != null ) {
              'oai:resumptionToken'(completeListSize:rec_count, cursor:offset, resumption);
            }
          }
        }
      }

      writer << xml.bind(resp)
    }


    render(text: writer.toString(), contentType: "text/xml", encoding: "UTF-8")
  }

  def listSets(result) {
  }
}
