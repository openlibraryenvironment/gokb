package org.gokb

import org.gokb.cred.*
import org.hibernate.Session
import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.bean.CsvToBean
import au.com.bytecode.opencsv.bean.HeaderColumnNameMappingStrategy
import au.com.bytecode.opencsv.bean.HeaderColumnNameTranslateMappingStrategy
import java.text.SimpleDateFormat
import org.apache.commons.io.ByteOrderMark

class FolderService {

  // https://www.javacodegeeks.com/2014/11/executorservice-10-tips-and-tricks.html
  // http://sysgears.com/articles/thread-synchronization-in-grails-application-using-hazelcast/

  def executorService
  def sessionFactory

  static def columns_config = [
    'list.name':[action:'process',target:'listname'],   // For ingesting into multiple folders
    'Title':[action:'process',target:'title'],
    'title.title':[action:'process',target:'title'],
    'Author(s)':[action:'process',target:'author'],
    'title.primary_author':[action:'process',target:'author'],
    'Editor(s)':[action:'process',target:''],
    'Importance':[action:'ignore'],
    'title.identifier':[action:'process',target:'title.identifier.isbn'],
    'ISBN10':[action:'process',target:'title.identifier.isbn'],
    'ISBN13':[action:'process',target:'title.identifier.isbn'],
    'Date of Publication':[action:'process',target:'pubdate'],
    'title.publication_year':[action:'process',target:'pubdate'],
    'Publisher':[action:'process',target:'publisher.name'],
    'title.publisher':[action:'process',target:'publisher.name'],
    'Web Address':[action:'process',target:'custom.url'],
    'Time Period':[action:'ignore'],
    'CKEY':[action:'process',target:'custom.ckey'],
  ];

  def enqueTitleList(file, default_folder_id, config) {
    def future = executorService.submit({
      processTitleList(file, folder_id, config)
    } as java.util.concurrent.Callable)
  }

  def processTitleList(file, default_folder_id, config) {

    try {

      log.debug("processTitleList(${file}, ${folder_id}, ${config})");

      // Open File
      if ( file ) {
        log.debug("Got file ${file}");
  
        def charset='UTF-8'

        def csv = new CSVReader(new InputStreamReader(
                                   new org.apache.commons.io.input.BOMInputStream(
                                     file.newInputStream(),
                                     ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_32LE, ByteOrderMark.UTF_32BE,ByteOrderMark.UTF_8),
                                 java.nio.charset.Charset.forName(charset)),'\t' as char,'"' as char)   // Use \0 for no quote char
  

        log.debug("Process rows.. config is ${columns_config}");

        String[] header = csv.readNext()
        log.debug("Got header ${header}");

        String[] nl=csv.readNext()
        int rownum = 0;
        while(nl!=null) {
          def row_result = [:]
          nl=csv.readNext()
          log.debug("Got row ${nl}");
          int colctr = 0;
          nl.each {
            def col_cfg = columns_config[header[colctr]]
            log.debug("using column config for ${header[colctr]} : ${col_cfg}");

            if ( ( col_cfg ) && ( it ) && ( it.trim().length() > 0 ) ) {
              if ( col_cfg.action=='process' ) {
                if ( row_result[col_cfg.target] == null ) {
                  row_result[col_cfg.target] = it
                }
                else {
                  if ( row_result[col_cfg.target] instanceof List ) {
                    row_result[col_cfg.target].add(it);
                  }
                  else {
                    row_result[col_cfg.target] = [ row_result[col_cfg.target], it ]
                  }
                }
              }
            }
            colctr++
          }

          log.debug("Row result: ${row_result}");
        }
      }

      // Delete file
      log.debug("Delete temp file");
      file.delete()
    }
    catch ( Throwable t ) {
      log.error("Problem in processTitleList",t);
    }

    // Return
    return
  }
}
