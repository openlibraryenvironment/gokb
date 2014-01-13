package org.gokb

import grails.converters.*
import grails.plugins.springsecurity.Secured
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.gokb.cred.*
import groovy.xml.MarkupBuilder

class OaiController {

  def grailsApplication
  def genericOIDService

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
        'oai:request'('verb':'GetRecord', 'identifier':'theid', 'metadataPrefix':'mdp', 'The Original URL')
        'oai:GetRecord'() {
          'oai:record'() {
            'oai:header'() {
              identifier(oid)
              datestamp(record.lastUpdated)
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
  }

  def listSets(result) {
  }
}
