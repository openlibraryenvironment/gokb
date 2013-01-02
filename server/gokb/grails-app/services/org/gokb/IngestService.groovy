package org.gokb

import org.gokb.refine.*;
import org.apache.commons.compress.compressors.gzip.*
import org.apache.commons.compress.archivers.tar.*
import org.apache.commons.compress.archivers.*

class IngestService {

  def grailsApplication

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
            def temp_data_zipfile
            try {
              log.debug("Handle Data.. create zipfile. need to copy ${ae.getSize()} bytes from tin to a buffer and re-read as a zip file");

              // Copy bytes from tar stream into temp zip file
              temp_data_zipfile = File.createTempFile('gokb_','_refinedata.zip',null)
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
                  result=[:]
                  processData(result, zf.getInputStream(ze));
                }
                else {
                  log.error("Problem getting data.txt");
                }
              }
              else {
                log.debug("zip file is null");
              }
            }
            finally {
              if ( temp_data_zipfile ) {
                try {
                  temp_data_zipfile.delete();
                }
                catch ( Throwable t ) {
                }
              }
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
    }
    catch ( Exception e ) {
      log.error("Unexpected error trying to extrat refine data.",e);
      e.printStackTrace();
    }
    
    result
  }

  def processData(result, is) {
    log.debug("processing refine data.txt");
    def bis = new BufferedReader(new InputStreamReader(is));

    // First line is the refine version
    result.refineVersion = bis.readLine()

    // 2.5

    // Header info
    result.columnModel=valuePart(bis.readLine())
    result.maxCellIndex=valuePart(bis.readLine())
    result.keyColumnIndex=valuePart(bis.readLine())
    result.columnCount=Integer.decode(valuePart(bis.readLine()))

    result.columnDefinitions = []
    for ( int i=0; i<result.columnCount; i++ ) {
      log.debug("Reading column ${i}");
      result.columnDefinitions.add(bis.readLine());
    }

    result.columnGroupCount = Integer.decode(valuePart(bis.readLine()))
    for (int i=0; i<result.columnGroupCount; i++ ) {
      // Skipping column group info
      def row = bis.readLine()
    }

    if ( bis.readLine() != '/e/' ) {
      log.error("Unexpected row!");
    }
  
    result.history = valuePart(bis.readLine())

    result.pastEntryCount = Integer.decode(valuePart(bis.readLine()))
    for (int i=0; i<result.pastEntryCount; i++ ) {
      // Skipping past entry
      def row = bis.readLine()
    }

    result.futureEntryCount = Integer.decode(valuePart(bis.readLine()))
    for (int i=0; i<result.futureEntryCount; i++ ) {
      // Skipping future entry
      def row = bis.readLine()
    }

    if ( bis.readLine() != '/e/' ) {
      log.error("Unexpected row!");
    }

    result.overlayModel = valuePart(bis.readLine())
    result.rowCount = Integer.decode(valuePart(bis.readLine()))
    for (int i=0; i<result.rowCount; i++ ) {
      def row = bis.readLine()
      log.debug("Row ${i}");
      // Skipping row
    }

    bis.close();
  }

  def valuePart(s) {
    int equalsPos = s.indexOf('=');
    def result = s.substring(equalsPos+1, s.length());
    log.debug("valuePart(${s})=${result}");
    result;
  }

}
