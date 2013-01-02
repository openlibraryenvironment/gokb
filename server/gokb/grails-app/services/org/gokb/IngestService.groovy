package org.gokb

import org.gokb.refine.*;
import org.apache.commons.compress.compressors.gzip.*
import org.apache.commons.compress.archivers.tar.*
import org.apache.commons.compress.archivers.*

class IngestService {

  def grailsApplication

  def extractRefineproject(String zipFilename) {
    def result = null;


    try {
      def full_filename = grailsApplication.config.project_dir + zipFilename

      log.debug("Extract ${full_filename}");

      FileInputStream fin = new FileInputStream(full_filename);
      GzipCompressorInputStream gzIn = new GzipCompressorInputStream(fin);
      TarArchiveInputStream tin = new TarArchiveInputStream(gzIn)
      TarArchiveEntry ae = tin.getNextTarEntry()
      while ( ae ) {
        log.debug("Processing archive entry: ${ae} ${ae.name} isFile:${ae.isFile()}");
        switch ( ae.name ) {
          case 'metadata.json':
            log.debug("Handle metadata");
            break;
          case 'data.zip':
            log.debug("Handle Data.. create zipfile. need to copy ${ae.getSize()} bytes from tin to a buffer and re-read as a zip file");

            // Copy bytes from tar stream into temp zip file
            def temp_data_zipfile = File.createTempFile('gokb_','_refinedata.zip',null)
            FileOutputStream fos = new FileOutputStream(temp_data_zipfile);
            int bytes_to_read = ae.getSize()
            byte[] buffer = new byte[4096]
            while (bytes_to_read) {
              int bytes_read = tin.read(buffer,0,4096)
              log.debug("Copying ${bytes_read} bytes to temp file");
              fos.write(buffer, 0, bytes_read)
              bytes_to_read -= bytes_read
            }
            fos.flush()
            fos.close();

            // Open temp zip file as a zip object
            if ( temp_data_zipfile ) {
              java.util.zip.ZipFile zf = new java.util.zip.ZipFile(temp_data_zipfile)
              log.debug("Getting data.txt");
              java.util.zip.ZipEntry ze = zf.getEntry('data.txt');
              if ( ze ) {
                log.debug("Got data.txt");
              }
              else {
                log.error("Problem getting data.txt");
              }
            }
            else {
              log.debug("zip file is null");
            }
            break;
          default:
            break;
        }
        
        ae = tin.getNextTarEntry()
      }
      tin.close();
      gzIn.close();
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
