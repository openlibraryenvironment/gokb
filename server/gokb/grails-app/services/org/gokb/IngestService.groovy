package org.gokb

import org.gokb.refine.*;
import org.apache.commons.compress.compressors.gzip.*
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.*

class IngestService {

  def grailsApplication

  def extractRefineproject(String zipFilename) {
    def result = null;


    try {
      def full_filename = grailsApplication.config.project_dir + zipFilename

      log.debug("Extract ${full_filename}");

      FileInputStream fin = new FileInputStream("archive.tar.gz");
      GzipCompressorInputStream gzIn = new GzipCompressorInputStream(fin);
      TarArchiveInputStream tin = new TarArchiveInputStream(gzIn)
      ArchiveEntry ae = tin.getNextEntry()
      while ( ae ) {
        log.debug("Processing archive entry: ${ae}");
        ae = tin.getNextEntry()
      }
      tin.close();
      gzIn.close();
      in.close();
      fin.close();

      // The file we are ingesting is a zip file, with a data.zip at the top level, this is the file we're interested in.
      // java.util.zip.ZipFile zf = new java.util.zip.ZipFile(full_filename);

      // if ( zf ) {
      //   java.util.zip.ZipEntry data_zipfile_entry = zf.getEntry('data.zip');
      //   if ( data_zipfile_entry ) {
      //     log.debug("Got zipfile entry....");
      //     // This is a ballache. We have to transfer the input stream to a file so we can re-open it since ZipFile has no input stream constructor.
      //     java.io.InputStream data_zip_input_stream = zf.getInputStream(data_zipfile_entry);
      //     data_zipfile = File.createTempFile('gokb_','_refinedata.zip',null)
      //     data_zipfile.append(data_zip_input_stream);
      //     log.debug("data zipfile: ${data_zipfile}");
      //   }
      // }
      // else {
      //   log.error("Unable to open zip file");
      // }
    }
    catch ( Exception e ) {
      log.error("Unexpected error trying to extrat refine data.",e);
      e.printStackTrace();
    }
    
    result
  }

  def validate(RefineProject p) {
    log.debug("Validate");
    def project_data = extractRefineproject(p.file);

    def result = [:]
    result.status = project_data ? true : false
    result.messages = []
    result.messages.add([text:'Checked in file passes GoKB validation step, proceed to ingest']);
    result
  }

  def ingest(RefineProject p) {
    log.debug("Ingest");
    def project_data = extractRefineproject(p.file);

    def result = [:]
    result.status = project_data ? true : false

    result
  }
}
