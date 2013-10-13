package org.gokb

/**
 * Look at uploaded files to see if there is any post-processing we can do on them
 */

class UploadAnalysisService {

  // Consider org.apache.tika.Tika for detection

  def analyse(uploaded_file, datafile) {
    log.debug('UploadAnalysisService::analyse');
    if ( datafile.mimetype?.equalsIgnoreCase('application/xml') ||
         datafile.mimetype?.equalsIgnoreCase('text/xml') ) {
      log.debug("uploaded file is XML...");
      analyseXML(uploaded_file, datafile)
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

      // Select any local handlers based on the root namespace/element
    }
    catch ( Exception e ) {
      log.error("Problem analysing XML document upload",e);
    }

  }
}
