package org.gokb

import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

import org.gokb.cred.*

/**
 * Look at uploaded files to see if there is any post-processing we can do on them
 */

class UploadAnalysisService {

  // Consider org.apache.tika.Tika for detection
  def grailsApplication

  def analyse(uploaded_file, datafile) {
    log.debug('UploadAnalysisService::analyse');

    def t = new org.apache.tika.Tika()
    def content_type = t.detect(uploaded_file)

    if ( content_type.equalsIgnoreCase('application/xml') ||
         content_type.equalsIgnoreCase('text/xml') ) {
      analyseXML(uploaded_file, datafile)
    }
    else {
      log.debug("Unhandled file type in analysis: ${datafile.uploadMimeType}");
    }
  }

  def analyseXML(uploaded_file, datafile) {
    log.debug("analyseXML");

    try {
      // Get schema

      // Open the new file so that we can parse the xml
      def xml = new XmlSlurper().parse(new FileInputStream(uploaded_file))

      def root_element_namespace = xml.namespaceURI();
      def root_element_name = xml.name();

      // Root node information....
      log.debug( "Root element namespace: ${root_element_namespace} root element: ${root_element_name}")

      if ( root_element_namespace != null && root_element_name != null ) {
        datafile.doctype="${root_element_namespace}:${root_element_name}"
        datafile.save(flush:true);
        // Select any local handlers based on the root namespace/element
        if ( root_element_namespace?.equalsIgnoreCase('http://www.editeur.org/onix-pl') && 
             root_element_name?.equalsIgnoreCase('PublicationsLicenseExpression') ) {
          processOnixLicense(xml, uploaded_file, datafile);
        }
      }
    }
    catch ( Exception e ) {
      log.error("Problem analysing XML document upload",e);
    }
  }

  def processOnixLicense(parsedXml, uploaded_file, datafile) {
    // Create a license relating to this file
    def license = new License()

    // Extract high level details
    license.name = "${parsedXml.ExpressionDetail.Description.text()}"
    if ( license.name == '' ) {
      license.name = "${parsedXml.LicenseDetail.Description.text()}"
      if ( license.name == '' )
        license.name = 'No description in license'
    }

    // Generate Summary
    license.summaryStatement = generateSummary(uploaded_file)

    if ( license.save(flush:true) ) {
      license.fileAttachments.add(datafile);
      license.save(flush:true);
    }
    else {
      log.debug("Problem saving new license: ${license.errors}");
    }

  }

  def generateSummary(onix_file) {
    String result = null
    def baos = new ByteArrayOutputStream()
    // def xslt = grailsApplication.mainContext.getResource('/WEB-INF/resources/onixToSummary.xsl').inputStream
    def xslt = grailsApplication.mainContext.getResource('/WEB-INF/resources/build-onix-pl-summary-view.xsl').inputStream

    if ( xslt != null ) {
      // Run transform against document and store output in license.summaryStatement
      def factory = TransformerFactory.newInstance()
      def transformer = factory.newTransformer(new StreamSource(xslt))
      transformer.transform(new StreamSource(new FileReader(onix_file)), new StreamResult(baos))

      result = baos.toString()
    }
    else {
      log.error("Unable to get handle to /onixToSummary.xsl XSL");
    }
    log.debug("generateSummary returning byte[] of length ${result?.length()}");
    result
  }
}
