package org.gokb

import org.gokb.cred.*
import org.hibernate.Session
import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.bean.CsvToBean
import au.com.bytecode.opencsv.bean.HeaderColumnNameMappingStrategy
import au.com.bytecode.opencsv.bean.HeaderColumnNameTranslateMappingStrategy
import org.apache.commons.io.ByteOrderMark

class FolderService {

  // https://www.javacodegeeks.com/2014/11/executorservice-10-tips-and-tricks.html
  // http://sysgears.com/articles/thread-synchronization-in-grails-application-using-hazelcast/

  def executorService
  def sessionFactory
  def titleLookupService

  static def columns_config = [
    'list.name':[action:'process',target:'listname'],   // For ingesting into multiple folders
    'title':[action:'process',target:'title'],
    'title.title':[action:'process',target:'title'],
    'author(s)':[action:'process',target:'author'],
    'title.primary_author':[action:'process',target:'author'],
    'editor(s)':[action:'process',target:''],
    'importance':[action:'ignore'],
    'title.identifier':[action:'process',target:'title.identifier.isbn'],
    'isbn10':[action:'process',target:'title.identifier.isbn'],
    'isbn13':[action:'process',target:'title.identifier.isbn'],
    'date of publication':[action:'process',target:'pubdate'],
    'title.publication_year':[action:'process',target:'pubdate'],
    'publisher':[action:'process',target:'publisher.name'],
    'title.publisher':[action:'process',target:'publisher.name'],
    'web address':[action:'process',target:'custom.url'],
    'time period':[action:'ignore'],
    'ckey':[action:'process',target:'custom.ckey'],
  ];

  def enqueTitleList(file, default_folder, user, org, config) {
    log.debug("enqueTitleList(${file}, ${default_folder}, ${user}, ${org}, ${config})")
    def future = executorService.submit({
      processTitleList(file, default_folder, user, org, config)
    } as java.util.concurrent.Callable)
    log.debug("Job enqueued");
  }

  def processTitleList(file, default_folder, user, org, config) {
    log.debug("processTitleList....");

    try {

      log.debug("processTitleList(file:${file}, default:${default_folder}, user:${user}, org:${org}, cfg:${config})");

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
        log.debug("First row: ${nl}");
        int rownum = 0;

        while(nl!=null) {
          def row_result = [:]
          log.debug("Got row ${nl}");
          int colctr = 0;
          nl.each {
            def colname = header[colctr].trim().toLowerCase();
            def col_cfg = columns_config[colname]
            log.debug("using column config for \"${colname}\" \"${header[colctr].trim()}\" \"${header[colctr].trim()}\": ${col_cfg}");

            if ( ( col_cfg ) && 
                 ( it ) && 
                 ( it.trim().length() > 0 ) ) {

              if ( col_cfg.action=='process' ) {
                if ( row_result[col_cfg.target] == null ) {
                  row_result[col_cfg.target] = it.trim()
                }
                else {
                  if ( row_result[col_cfg.target] instanceof List ) {
                    row_result[col_cfg.target].add(it.trim());
                  }
                  else {
                    row_result[col_cfg.target] = [ row_result[col_cfg.target], it.trim() ]
                  }
                }
              }
            }
            colctr++
          }

          log.debug("Row result: ${row_result}");
          if ( row_result.size() > 0 ) {
            processRow(row_result, user, org, default_folder);
          }
          nl=csv.readNext()
        }

        // log.debug("Completed processing rows");
      }
      else {
        log.error("Unable to locate file");
      }

      // Delete file
      log.debug("Delete temp file");
      file.delete()
    }
    catch ( Exception e ) {
      log.error("Problem in processTitleList",e);
    }

    // Return
    return
  }

  private void processRow(row, user, org, default_folder) {
    log.debug("processRow(${row},${user},${org},${default_folder})");
    if ( org ) {

      def folder = null;

      if ( ( row['listname'] == null || row['listname'].length() == 0 ) && default_folder == null ) {
        log.error("No listname or default folder - cannot continue");
      }
      else {
        // Try and lookup folder owned by this org with the given name
        if ( row['listname'] != null && row['listname'].length() > 0 ) {
          def folders = Folder.executeQuery('select f from Folder as f where f.owner=:owner and f.name=:fname',[owner:org, fname:row['listname']]);
          switch ( folders.size() ) {
            case 0:
              log.debug("Create new folder or use default if present and no row level name");
              folder = new Folder(name:row['listname'], owner:org).save(flush:true, failOnError:true);
              break;
            case 1:
              log.debug("Found Folder");
              folder = folders.get(0);
              break;
            default:
              log.warn("Matched multiple folders");
              break;
          }
        }
        else {
          folder = default_folder
        }
      }

      // log.debug("Folder for row will be ${folder}");

      def identifiers = []

      if ( row['title.identifier.isbn'] ) {
        if ( row['title.identifier.isbn'] instanceof List ) {
          row['title.identifier.isbn']?.each {
            if ( it.trim().length() > 0 ) {
              identifiers.add([type:'isbn',value:it.trim()]);
            }
          }
        }
        else {
          if ( row['title.identifier.isbn']?.trim().length() > 0 ) {
            identifiers.add([type:'isbn',value:row['title.identifier.isbn'].trim()]);
          }
        }
      }

      // Process the title...
      // Preflight
      if ( ( row['title.identifier.isbn'] ) &&
           ( row['title'] ) &&
           ( row['title'].trim().length() > 0 ) &&
           ( identifiers.size() > 0 ) &&
           ( folder ) ) {
        def title = titleLookupService.find(row['title'], row['publisher.name'], identifiers, null, null, 'org.gokb.cred.BookInstance' )  ;

        // log.debug("Result of lookup ${identifiers} ${title}");
        def fe = KBComponentFolderEntry.executeQuery('select fe from KBComponentFolderEntry as fe where fe.linkedComponent = :c and fe.folder = :f',[c:title, f:folder]);
        switch(fe.size()) {
          case 0:
            // log.debug("No current folder entry.. create");
            def nfe = new KBComponentFolderEntry(linkedComponent:title, folder:folder).save(flush:true, failOnError:true);
            break;
          case 1:
            // log.debug("Found existing folder entry.. ignore");
            break;
          default:
            log.warn("Multiple matching folder entries. PANIC");
            break;
        }
      }
      
    }
    else {
      log.warn("No org - cannot continue");
    }
  }
}
