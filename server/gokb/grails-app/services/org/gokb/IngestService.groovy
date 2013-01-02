package org.gokb

import org.gokb.refine.*;

class IngestService {

  def grailsApplication

  def extractRefineproject(String zipFilename) {
    log.debug("Extract ${zipFilename}");
    File f = new File(zipFilename)
    def result = [:]

    result
  }

  def validate(RefineProject p) {
    log.debug("Validate");
    def project_data = extractRefineproject(p.file);

    def result = [:]
    result.status = true
    result.messages = []
    result.messages.add([text:'Checked in file passes GoKB validation step, proceed to ingest']);
    result
  }

  def ingest(RefineProject p) {
    log.debug("Ingest");
    def project_data = extractRefineproject(p.file);

  }
}
