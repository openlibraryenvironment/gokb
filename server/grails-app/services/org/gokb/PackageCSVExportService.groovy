package org.gokb

import com.k_int.ClassUtils

import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVParser
import com.opencsv.CSVParserBuilder

import grails.gorm.transactions.Transactional
import grails.io.IOUtils

import groovy.util.logging.Slf4j

import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import org.apache.commons.io.FileUtils
import org.gokb.cred.*
import org.hibernate.ScrollMode
import org.hibernate.ScrollableResults
import org.hibernate.Session
import org.hibernate.type.StandardBasicTypes
import org.springframework.util.FileCopyUtils

@Slf4j
class PackageCSVExportService {
  def sessionFactory
  def concurrencyManagerService
  def grailsApplication
  def dateFormatService
  def packageCachingService

  private static final String[] KBART_FIELDS = ['publication_title',
     'print_identifier',
     'online_identifier',
     'date_first_issue_online',
     'num_first_vol_online',
     'num_first_issue_online',
     'date_last_issue_online',
     'num_last_vol_online',
     'num_last_issue_online',
     'title_url',
     'first_author',
     'title_id',
     'embargo_info',
     'coverage_depth',
     'coverage_notes',
     'publisher_name',
     'preceding_publication_title_id',
     'date_monograph_published_print',
     'date_monograph_published_online',
     'monograph_volume',
     'monograph_edition',
     'first_editor',
     'parent_publication_title_id',
     'publication_type',
     'access_type',
     'zdb_id',
     'gokb_tipp_uid',
     'gokb_title_uid']

  public static final enum ExportType {
    KBART_TIPP, KBART_TITLE, TSV
  }

  public String updateExportFiles(Package pkg, boolean force = false) {
    //log.info("Caching KBART & CSV for ${pkg}..")
    String result = 'OK'
    boolean activeJobs = concurrencyManagerService.getComponentJobs(pkg.id)?.data?.size() > 0

    if (!activeJobs) {
      result = createKbartExport(pkg, ExportType.KBART_TIPP, force)

      if (result == 'OK') {
        result = createKbartExport(pkg, ExportType.KBART_TITLE, force)
      }

      if (result == 'OK') {
        result = createTsvExport(pkg, force)
      }
    }
    else {
      result = 'SKIPPED_ACTIVE_JOB'
    }
    //log.info("Finished caching KBART & CSV files")

    result
  }

  /**
   * collects the data of the given package into a KBART formatted TSV file for later download
   */
  private String createKbartExport(Package pkg, ExportType exportType = ExportType.KBART_TIPP, boolean force_rewrite = false) {
    String result = 'OK'

    if (pkg) {
      String oldExportFileName = generateExportFileName(pkg, exportType, false)
      String exportFileName = generateExportFileName(pkg, exportType)
      String path = exportFilePath()
      boolean activeJobs = concurrencyManagerService.getComponentJobs(pkg.id)?.data?.size() > 0

      if (!activeJobs) {
        try {
          boolean selectiveUpdate = false
          boolean cancelled = false
          String latestFileName = getLatestFile(pkg, path, oldExportFileName, exportType)
          def existingFileMap = [:]
          File out = new File("${path}${exportFileName}")
          File old_out = new File("${path}${oldExportFileName}")
          boolean existing_ms_accuracy = false
          Date currentCacheDate

          if (latestFileName) {
            try {
              currentCacheDate = dateFormatService.parseTimestampMs(latestFileName.substring(latestFileName.length() - 27, latestFileName.length() - 4))
              existing_ms_accuracy = true
            }
            catch (Exception e) {
              log.debug("Got old timestamp accuracy")

              currentCacheDate = dateFormatService.parseTimestamp(latestFileName.substring(latestFileName.length() - 23, latestFileName.length() - 4))
            }
          }

          if (!force_rewrite && out.isFile()) {
            // log.debug("createKbartExport :: Current file for new uuid pattern ${exportFileName} already exists!")
            return result
          }

          if (!force_rewrite && old_out.isFile()) {
            // log.debug("createKbartExport :: Current file for old name pattern ${oldExportFileName} already exists!")
            return result
          }

          log.debug("Existing file: ${latestFileName}")

          if (force_rewrite ||
              (Duration.between(pkg.lastUpdated.toInstant(), Instant.now()).getSeconds() > 60 &&
                (!latestFileName ||
                  pkg.lastUpdated.toInstant() > (existing_ms_accuracy ? currentCacheDate.toInstant() : currentCacheDate.toInstant().plusSeconds(1))
                )
              )
          ){
            log.info("createKbartExport :: Package ${pkg}, type: ${exportType}, rewrite: ${force_rewrite}")

            if (latestFileName?.startsWith(pkg.uuid) && !force_rewrite) {
              CSVReader csv = initReader(path + latestFileName)

              String[] header = csv.readNext().collect { it.toLowerCase().trim() }
              def col_positions = [:]
              int col_ctr = 0
              boolean header_conflicts = false

              header.each { col ->
                if (col != KBART_FIELDS[col_ctr]) {
                  log.error("createKbartExport :: Header conflict in KBART caching .. ${col} != ${KBART_FIELDS[col_ctr]}")
                  header_conflicts = true
                }

                col_positions[col] = col_ctr++
              }

              if (header_conflicts) {
                log.warn("createKbartExport :: No selective update due to header discrepancies!")
              }
              else {
                selectiveUpdate = true
                boolean more = true

                while (more) {
                  String[] row_data = csv.readNext()

                  if (row_data) {
                    def tipp_uuid = row_data[col_positions['gokb_tipp_uid']]

                    if (!existingFileMap[tipp_uuid]) {
                      existingFileMap[tipp_uuid] = []
                    }

                    existingFileMap[tipp_uuid].push(row_data)
                  }
                  else {
                    more = false
                  }
                }

                log.debug("createKbartExport :: Old map has ${existingFileMap.keySet().size()} entries!")
              }
            }
            else if (!force_rewrite) {
              log.debug("Full rewrite for old filename ${latestFileName} ..")
            }

            def tmpFile = new File("${grailsApplication.config.getProperty('gokb.baseTempDirectory')}${exportFileName}")

            if (tmpFile.isFile()) {
              tmpFile.delete()
            }

            tmpFile.withWriter { writer ->
              // As per spec header at top of file / section
              // II: Need to add in preceding_publication_title_id
              KBART_FIELDS.eachWithIndex { field, i ->
                writer.write(field)
                writer.write(i < KBART_FIELDS.size() - 1 ? '\t' : '\n')
              }

              def session = sessionFactory.getCurrentSession()
              def combo_tipps = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
              def status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
              def status_expected = RefdataCategory.lookup('KBComponent.Status', 'Expected')
              def qry_string_full = '''select tipp.id from TitleInstancePackagePlatform as tipp,
                                                Combo as c
                                                where c.fromComponent.id = :p
                                                and c.toComponent = tipp
                                                and tipp.status in (:status)
                                                and c.type = :ct
                                                order by tipp.id'''
              def qry_string_selective = '''select tipp.id from TitleInstancePackagePlatform as tipp,
                                                Combo as c
                                                where c.fromComponent.id = :p
                                                and c.toComponent = tipp
                                                and c.type = :ct
                                                and tipp.lastUpdated > :ts
                                                order by tipp.id'''


              def query = session.createQuery(selectiveUpdate ? qry_string_selective : qry_string_full)
              query.setReadOnly(true)
              query.setParameter('p', pkg.getId(), StandardBasicTypes.LONG)
              query.setParameter('ct', combo_tipps)

              if (!selectiveUpdate) {
                query.setParameterList('status', [status_current, status_expected])
              }
              else {
                query.setParameter('ts', currentCacheDate)
              }

              ScrollableResults tippIDs = query.scroll(ScrollMode.FORWARD_ONLY)
              int ctr = 0

              while (tippIDs.next()) {
                def tipp_id = tippIDs.get(0)
                TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tipp_id)

                if (selectiveUpdate) {
                  if (tipp.status != status_expected && tipp.status != status_current && existingFileMap[tipp.uuid]) {
                    existingFileMap.remove(tipp.uuid)
                  }
                  else {
                    existingFileMap[tipp.uuid] = []

                    kbartRecordsFor(tipp, exportType).each { record ->
                      def new_row_data = []

                      KBART_FIELDS.eachWithIndex { fieldName, i ->
                        new_row_data << sanitize(record[fieldName])
                      }

                      existingFileMap[tipp.uuid].push(new_row_data)
                    }
                  }
                }
                else {
                  kbartRecordsFor(tipp, exportType).each { record ->
                    KBART_FIELDS.eachWithIndex { fieldName, i ->
                      writer.write(sanitize(record[fieldName]))
                      writer.write(i < KBART_FIELDS.size() - 1 ? '\t' : '\n')
                    }
                  }
                }

                if (ctr % 50 == 0) {
                  session.flush()
                  session.clear()
                }

                if (Thread.currentThread().isInterrupted()) {
                  log.info("KBART caching was cancelled!")
                  cancelled = true
                  break
                }
                ctr++
              }

              if (selectiveUpdate && !cancelled) {
                Map orderedMap = existingFileMap.sort { it.value[0][0].toLowerCase() }

                orderedMap.each { uuid, rows ->
                  rows.each { row_items ->
                    writer.write(row_items.join('\t'))
                    writer.write('\n')
                  }
                }
              }

              log.debug("Rewritten lines for ${ctr} TIPPs")

              tippIDs.close()
              writer.close()
            }

            if (!cancelled) {
              if (latestFileName) {
                log.debug("Deleting old file ${latestFileName} for ${pkg}")
                new File(path + latestFileName).delete()
              }

              FileUtils.moveFile(tmpFile, out)
            }
            else {
              result = 'CANCELLED'
            }
          }
        }
        catch (Exception e) {
          log.error("Problem with creating KBART export data", e)
          result = 'ERROR'
        }

        log.info("createKbartExport :: Finished caching for Package ${pkg}, type: ${exportType}, rewrite: ${force_rewrite}")
      }
      else {
        log.debug("createKbartExport:: Waiting for active Jobs to finish!")
      }
    }
    else {
      log.error("Unable to reference package!")
    }

    result
  }

  private String createTsvExport(Package pkg, boolean force_rewrite = false) {
    String result = 'OK'
    String export_date = dateFormatService.formatDate(new Date())
    String oldExportFileName = generateExportFileName(pkg, ExportType.TSV, false)
    String exportFileName = generateExportFileName(pkg, ExportType.TSV)
    String path = exportFilePath()
    String pkgName = pkg.name
    boolean activeJobs = concurrencyManagerService.getComponentJobs(pkg.id)?.data?.size() > 0

    if (!activeJobs) {
      try {
        boolean selectiveUpdate = false
        boolean cancelled = false
        String latestFileName = getLatestFile(pkg, path, oldExportFileName, ExportType.TSV)
        def existingFileMap = [:]
        File out = new File("${path}${exportFileName}")
        File old_out = new File("${path}${oldExportFileName}")
        Date currentCacheDate
        boolean existing_ms_accuracy = false

        if (latestFileName) {
          try {
            currentCacheDate = dateFormatService.parseTimestampMs(latestFileName.substring(latestFileName.length() - 27, latestFileName.length() - 4))
            existing_ms_accuracy = true
          }
          catch (Exception e) {
            log.debug("Got old timestamp accuracy")

            currentCacheDate = dateFormatService.parseTimestamp(latestFileName.substring(latestFileName.length() - 23, latestFileName.length() - 4))
          }
        }

        if (!force_rewrite && out.isFile()) {
          // log.debug("createTsvExport :: Current file for new uuid pattern ${exportFileName} already exists!")
          return result
        }

        if (!force_rewrite && old_out.isFile()) {
          // log.debug("createTsvExport :: Current file for old name pattern ${oldExportFileName} already exists!")
          return result
        }

        if (force_rewrite ||
            (Duration.between(pkg.lastUpdated.toInstant(), Instant.now()).getSeconds() > 60 &&
              (!latestFileName ||
                pkg.lastUpdated.toInstant() > (existing_ms_accuracy ? currentCacheDate.toInstant() : currentCacheDate.toInstant().plusSeconds(1))
              )
            )
        ) {
          log.info("createTsvExport :: Caching start for Package ${pkg}, rewrite: ${force_rewrite}")

          if (latestFileName && !force_rewrite) {
            selectiveUpdate = true
            CSVReader csv = initReader(path + latestFileName)

            def fileNameHeader = csv.readNext()
            String[] header = csv.readNext().collect { it.toLowerCase().trim() }
            boolean more = true
            int rownum = 0

            while (more) {
              String[] row_data = csv.readNext()

              if (row_data) {
                def tipp_id = row_data[0]

                if (!existingFileMap[tipp_id]) {
                  existingFileMap[tipp_id] = []
                }

                existingFileMap[tipp_id] << row_data
              }
              else {
                more = false
              }
            }
          }

          def tmpFile = new File("${grailsApplication.config.getProperty('gokb.baseTempDirectory')}${exportFileName}")

          if (tmpFile.isFile()) {
            tmpFile.delete()
          }

          tmpFile.withWriter { writer ->
            def sanitize = { it ? "${it}".trim() : "" }

            // As per spec header at top of file / section
            writer.write("GOKb Export : ${pkg.provider?.name} : ${pkg.name} : ${export_date}\n")

            writer.write('TIPP ID\t' +
                'TIPP URL\t' +
                'Title ID\t' +
                'Title\t' +
                'TIPP Status\t' +
                '[TI] Publisher\t' +
                '[TI] Imprint\t' +
                '[TI] Published From\t' +
                '[TI] Published to\t' +
                '[TI] Medium\t' +
                '[TI] OA Status\t' +
                '[TI] Continuing series\t' +
                '[TI] ISSN\t' +
                '[TI] EISSN\t' +
                '[TI] ZDB-ID\t' +
                'Package\t' +
                'Package ID\t' +
                'Package URL\t' +
                'Platform\t' +
                'Platform URL\t' +
                'Platform ID\t' +
                'Reference\t' +
                'Edit Status\t' +
                'Access Start Date\t' +
                'Access End Date\t' +
                'Coverage Start Date\t' +
                'Coverage Start Volume\t' +
                'Coverage Start Issue\t' +
                'Coverage End Date\t' +
                'Coverage End Volume\t' +
                'Coverage End Issue\t' +
                'Embargo\t' +
                'Coverage depth\t' +
                'Coverage note\t' +
                'Host Platform URL\t' +
                'Format\t' +
                'Payment Type\t' +
                '[TI] DOI\t' +
                '[TI] ISBN\t' +
                '[TI] pISBN' +
                '\n');

            def session = sessionFactory.getCurrentSession()
            def combo_tipps = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
            def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
            def qry_string_full = '''select tipp.id
                                      from TitleInstancePackagePlatform as tipp,
                                      Combo as c
                                      where c.fromComponent.id=:p
                                      and c.toComponent=tipp
                                      and tipp.status <> :sd
                                      and c.type = :ct
                                      order by tipp.id'''
            def qry_string_selective = '''select tipp.id
                                      from TitleInstancePackagePlatform as tipp,
                                      Combo as c
                                      where c.fromComponent.id=:p
                                      and c.toComponent=tipp
                                      and c.type = :ct
                                      and tipp.lastUpdated > :ts
                                      order by tipp.id'''
            def query = session.createQuery(selectiveUpdate ? qry_string_selective : qry_string_full)

            query.setReadOnly(true)
            query.setParameter('p', pkg.getId(), StandardBasicTypes.LONG)
            query.setParameter('ct', combo_tipps)


            if (!selectiveUpdate) {
              query.setParameter('sd', status_deleted)
            }
            else {
              query.setParameter('ts', currentCacheDate)
            }

            ScrollableResults tipps = query.scroll(ScrollMode.FORWARD_ONLY)
            int ctr = 0

            TitleInstancePackagePlatform.withNewSession { tsession ->
              while (tipps.next()) {
                def tipp_id = tipps.get(0)
                TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tipp_id)

                List ordered_rows = tsvRecordsFor(tipp)

                if (selectiveUpdate) {
                  if (tipp.status == status_deleted && existingFileMap[tipp.id]) {
                    existingFileMap.remove(tipp.id)
                  }
                  else {
                    existingFileMap[tipp.id] = ordered_rows
                  }
                }
                else {
                  ordered_rows.each { row ->
                    writer.write(row.join('\t'))
                    writer.write('\n')
                  }
                }

                ctr++

                if (Thread.currentThread().isInterrupted()) {
                  cancelled = true
                  break
                }

                if (ctr % 50 == 0) {
                  tsession.flush()
                  tsession.clear()
                }
              }

              if (selectiveUpdate) {
                Map orderedMap = existingFileMap.sort { it.value[0][3].toLowerCase() }

                orderedMap.each { uuid, rows ->
                  rows.each { row_items ->
                    writer.write(row_items.join('\t'))
                    writer.write('\n')
                  }
                }
              }

              log.debug("Rewritten lines for ${ctr} TIPPs")
            }
            tipps.close()
            writer.close()
          }

          if (!cancelled) {
            if (latestFileName) {
              log.debug("Deleting old file ${latestFileName} for ${pkg}")
              new File(path + latestFileName).delete()
            }

            FileUtils.moveFile(tmpFile, out)
          }
          else {
            result = 'CANCELLED'
          }
        }
      }
      catch (Exception e) {
        log.error("Problem with writing tsv export file", e)
        result = 'ERROR'
      }

      log.info("createTsvExport :: Finished caching for Package ${pkg}, rewrite: ${force_rewrite}")
    }
    else {
      log.debug("createTsvExport:: Waiting for active Jobs to finish!")
    }

    result
  }

  private String getLatestFile(pkg, path, old_pattern, type) {
    //log.debug("getLatestFile :: Old pattern to match is '${old_pattern.substring(0, old_pattern.length() - 21)}'")
    String result
    String type_string

    if (type == ExportType.KBART_TIPP) {
      type_string = 'Local'
    }
    else if (type == ExportType.KBART_TITLE) {
      type_string = 'Processed'
    }
    else if (type == ExportType.TSV) {
      type_string = 'GOKBPackage'
    }

    new File(path).list().each { someFileName ->
      if (someFileName.startsWith("${pkg.uuid}_${type_string}") || someFileName.startsWith(old_pattern.substring(0, old_pattern.length() - 21))) {
        result = someFileName
      }
    }

    result
  }

  public void sendFile(Package pkg, ExportType type, def response) {
    def path = exportFilePath()
    String oldCachedName = generateExportFileName(pkg, type, false)
    String newCacheName = generateExportFileName(pkg, type)
    String exportName = generateExportFileName(pkg, type, false, false, true, true)

    try {
      if (grailsApplication.config.getProperty('gokb.packageOaiCaching.enabled', Boolean, false) == false) {
        if (type in [ExportType.KBART_TIPP, ExportType.KBART_TITLE])
          createKbartExport(pkg, type, true)
        else
          createTsvExport(pkg, true)
      }

      def latest = getLatestFile(pkg, path, oldCachedName, type)
      File file

      if (latest) {
        file = new File(path + latest)
      }
      else {
        log.debug("No file found for '${oldCachedName}' / '${newCacheName}'")
        response.status = 404
      }

      if (file?.isFile()) {
        InputStream inFile = new FileInputStream(file)

        response.setContentType('text/tab-separated-values')
        response.setHeader("Content-Disposition", "attachment; filename=\"${exportName}\"")
        response.setHeader("Content-Encoding", "UTF-8")
        response.setContentLength(file.bytes.length)

        def out = response.outputStream
        IOUtils.copy(inFile, out)
        inFile.close()
        out.close()
      }
    }
    catch (Exception e) {
      log.error("Problem with sending export", e)
    }
  }

  private CSVReader initReader (filename) {
    def charset = 'UTF-8'

    final CSVParser parser = new CSVParserBuilder()
    .withSeparator('\t' as char)
    .withIgnoreQuotations(true)
    .build()

    CSVReader csv = new CSVReaderBuilder(
        new FileReader(filename)
    ).withCSVParser(parser)
    .build()

    return csv
  }

  public void sendZip(Collection packs, ExportType type, def response) {
    def pathPrefix = UUID.randomUUID().toString()
    String path = exportFilePath()
    File tempDir = new File(path + pathPrefix)
    boolean hasErrors = false
    tempDir.mkdir()
    // step one: collect data files in temp directory
    packs.each { pkg ->
      String oldCachedName = generateExportFileName(pkg, type, false)
      String exportName = generateExportFileName(pkg, type, false, false, true, true)
      boolean fileErrors = false

      try {
        if (grailsApplication.config.getProperty('gokb.packageOaiCaching.enabled', Boolean, false) == false) {
          if (type in [ExportType.KBART_TIPP, ExportType.KBART_TITLE])
            createKbartExport(pkg, type, true)
          else
            createTsvExport(pkg, true)
        }

        def latest = getLatestFile(pkg, path, oldCachedName, type)
        File src

        if (latest) {
          src = new File(path + latest)
        }
        else {
          fileErrors = true
        }

        if (!fileErrors) {
          File dest = new File("${path}${pathPrefix}/${exportName}")
          FileCopyUtils.copy(src, dest)
        }
      } catch (IOException iox) {
        log.error("Problem while collecting data", iox)
      }
    }

    // step two: zip data
    if (!hasErrors) {
      def zipFileName = exportFilePath() + "gokbExport_${pathPrefix}.zip"
      ZipOutputStream zipFile = new ZipOutputStream(new FileOutputStream(zipFileName))
      new File("${exportFilePath()}$pathPrefix").eachFile() { file ->
        //check if file
        if (file.isFile()) {
          zipFile.putNextEntry(new ZipEntry(file.name))
          def buffer = new byte[file.size()]
          file.withInputStream {
            zipFile.write(buffer, 0, it.read(buffer))
          }
          zipFile.closeEntry()
        }
      }
      zipFile.close()

      // step three: copy the zipfile into the response
      File file = new File(zipFileName)
      response.setContentType('application/octet-stream');
      response.setHeader("Content-Disposition", "attachment; filename=\"gokbExport.zip\"")
      response.setHeader("Content-Description", "File Transfer")
      response.setHeader("Content-Transfer-Encoding", "binary")
      response.setContentLength(file.length())

      InputStream input = new FileInputStream(file)
      OutputStream output = response.outputStream
      IOUtils.copy(input, output)
      output.close()
      input.close()
    }
    else {
      response.status = 404
    }
  }

  static String urlStringToFileString(String url){
    url.replace("://", "_").replace(".", "_").replace("/", "_")
  }

  private String generateExportFileName(Package pkg, ExportType type, boolean uuid_name = true, boolean full_timestamp = true, boolean date_stamp = false, boolean file_type = true) {
    StringBuilder name = new StringBuilder()

    if (uuid_name) {
      name.append(pkg.uuid)

      if (type == ExportType.KBART_TIPP && !date_stamp) {
        name.append('_Local')
      }
      else if (type == ExportType.KBART_TITLE) {
        name.append('_Processed')
      }
      else if (type == ExportType.TSV) {
        name.append('_GOKBPackage')
      }
    }
    else {
      if (type in [ExportType.KBART_TIPP, ExportType.KBART_TITLE] ) {
        name.append(toCamelCase(pkg.provider?.name ? pkg.provider.name : "Unknown Provider")).append('_')
            .append(toCamelCase(pkg.global?.value ?: 'Global')).append('_')
            .append(toCamelCase(pkg.name))
            .append(type == ExportType.KBART_TITLE ? '_Processed' : '')
      }
      else {
        name.append("GoKBPackage-").append(pkg.id)
      }
    }

    if (full_timestamp || date_stamp) {
      name.append('_')

      if (full_timestamp) {
        name.append(dateFormatService.formatTimestampMs(pkg.lastUpdated))
      }
      else {
        name.append(dateFormatService.formatDate(pkg.lastUpdated))
      }
    }

    if (file_type) {
      name.append('.txt')
    }

    return name.toString()
  }

  private String exportFilePath() {
    String exportPath = grailsApplication.config.getProperty('gokb.tsvExportTempDirectory') ?: "/tmp/gokb/export"
    Files.createDirectories(Paths.get(exportPath))
    exportPath.endsWith('/') ? exportPath : exportPath + '/'
  }

  private String toCamelCase(String before) {
    StringBuilder ret = new StringBuilder()
    before.split("\\W").each { word ->
      if (word.length() > 0)
        ret.append(word.substring(0, 1).toUpperCase())
            .append(word.substring(1, word.length()).toLowerCase())
    }
    ret.toString()
  }

  private static String sanitize(def what) {
    return (what && (what.toString().trim() != '')) ? what.toString().trim() : ''
  }

  private String pick(def tippPropValue, def titlePropValue, ExportType exportType) {
    if (tippPropValue && titlePropValue){
      return (exportType == ExportType.KBART_TIPP) ? tippPropValue : titlePropValue
    }
    else if (tippPropValue){
      return tippPropValue
    } else if (titlePropValue){
      return titlePropValue
    }
    return ''
  }

  private String selectDateField(tippPropValue, titlePropValue, ExportType exportType) {
    if (tippPropValue && titlePropValue){
      return (exportType == ExportType.KBART_TIPP) ? dateFormatService.formatDate(tippPropValue) : dateFormatService.formatDate(titlePropValue)
    }
    else if (tippPropValue){
      return dateFormatService.formatDate(tippPropValue)
    } else if (titlePropValue){
      return dateFormatService.formatDate(titlePropValue)
    }
    return ''
  }

  private List kbartRecordsFor (TitleInstancePackagePlatform tipp, ExportType exportType) {
    def recordList = []
    def record = [:]
    def ti = ClassUtils.deproxy(tipp.title)

    record.publication_title = pick(tipp.name, ti?.name, exportType)
    record.publication_type = pick(tipp.publicationType, ti?.niceName == 'Book' ? 'Monograph' : 'Serial', exportType)
    if (record.publication_type == 'Monograph') {
      record.print_identifier = pick(tipp.getIdentifierValue('pISBN'), ti?.getIdentifierValue('pISBN'), exportType)
      record.online_identifier = pick(tipp.getIdentifierValue('ISBN'), ti?.getIdentifierValue('ISBN'), exportType)
    }
    else{
      record.print_identifier = pick(tipp.getIdentifierValue('ISSN'), ti?.getIdentifierValue('ISSN'), exportType)
      record.online_identifier = pick(tipp.getIdentifierValue('eISSN'), ti?.getIdentifierValue('eISSN'), exportType)
    }
    record.title_url = tipp.url
    record.first_author = pick(tipp.firstAuthor, ti?.hasProperty('firstAuthor') ? ti.firstAuthor : null, exportType)
    record.first_editor = pick(tipp.firstEditor, ti?.hasProperty('firstEditor') ? ti.firstEditor : null, exportType)
    record.date_monograph_published_print = selectDateField(tipp.dateFirstInPrint, ti?.hasProperty('dateFirstInPrint') ? ti.dateFirstInPrint : null, exportType)
    record.date_monograph_published_online = selectDateField(tipp.dateFirstOnline, ti?.hasProperty('dateFirstOnline') ? ti.dateFirstOnline : null, exportType)
    record.monograph_volume = pick(tipp.volumeNumber, ti?.hasProperty('volumeNumber') ? ti.volumeNumber : null, exportType)
    record.monograph_edition = pick(tipp.editionStatement, ti?.hasProperty('editionStatement') ? ti.editionStatement : null, exportType)
    record.title_id = tipp.importId
    record.publisher_name = pick(tipp.publisherName, ti?.getCurrentPublisher()?.name, exportType)
    record.preceding_publication_title_id = tipp.precedingPublicationTitleId
    record.parent_publication_title_id = tipp.parentPublicationTitleId
    record.access_type = pick((tipp.paymentType && ['OA','Uncharged'].contains(tipp.paymentType.value) ? 'F' : 'P'), null, exportType)
    record.zdb_id = pick(tipp.getIdentifierValue('ZDB'), ti?.getIdentifierValue('ZDB'), exportType)
    record.gokb_tipp_uid = tipp.uuid
    record.gokb_title_uid = ti?.uuid

    if (tipp.coverageStatements.size() > 0 ){
      // several records
      tipp.coverageStatements.each { cst ->
        record.date_first_issue_online = cst.startDate ? dateFormatService.formatDate(cst.startDate) : null
        record.num_first_issue_online = cst.startIssue
        record.num_first_vol_online = cst.startVolume
        record.date_last_issue_online = cst.endDate ? dateFormatService.formatDate(cst.endDate) : null
        record.num_last_issue_online = cst.endIssue
        record.num_last_vol_online = cst.endVolume
        record.embargo_info = cst.embargo
        record.coverage_depth = cst.coverageDepth ? cst.coverageDepth.value.toLowerCase() : null
        record.coverage_notes = cst.coverageNote

        recordList << record.clone()
      }
    }
    else{
      // just one
      record.date_first_issue_online = tipp.startDate ? dateFormatService.formatDate(tipp.startDate) : null
      record.num_first_issue_online = tipp.startIssue
      record.num_first_vol_online = tipp.startVolume
      record.date_last_issue_online = tipp.endDate ? dateFormatService.formatDate(tipp.endDate) : null
      record.num_last_issue_online = tipp.endIssue
      record.num_last_vol_online = tipp.endVolume
      record.embargo_info = tipp.embargo
      record.coverage_depth = tipp.coverageDepth ? tipp.coverageDepth.value.toLowerCase() : null
      record.coverage_notes = tipp.coverageNote

      recordList << record
    }

    return recordList
  }

  private List tsvRecordsFor (TitleInstancePackagePlatform tipp) {
    def recordList = []
    def ti = ClassUtils.deproxy(tipp.title)

    if (tipp.coverageStatements?.size() > 0) {
      tipp.coverageStatements.each { tcs ->
        def record = [
          tipp.getId(),
          sanitize(tipp.url),
          sanitize(ti?.getId()),
          sanitize(tipp.name ?: ti?.name),
          sanitize(tipp.status.value),
          sanitize(ti?.getCurrentPublisher()?.name),
          sanitize(ti?.imprint?.name),
          sanitize(ti?.publishedFrom),
          sanitize(ti?.publishedTo),
          sanitize(ti?.medium?.value),
          sanitize(ti?.OAStatus?.value),
          sanitize(ti?.continuingSeries?.value),
          sanitize(tipp.getIdentifierValue('issn') ?: ti?.getIdentifierValue('issn')),
          sanitize(tipp.getIdentifierValue('eissn') ?: ti?.getIdentifierValue('eissn')),
          sanitize(tipp.getIdentifierValue('zdb') ?: ti?.getIdentifierValue('zdb')),
          sanitize(tipp.pkg.name),
          sanitize(tipp.pkg.id),
          "",
          sanitize(tipp.hostPlatform.name),
          sanitize(tipp.hostPlatform.primaryUrl),
          sanitize(tipp.hostPlatform.getId()),
          "",
          sanitize(tipp.editStatus?.value),
          sanitize(tipp.accessStartDate),
          sanitize(tipp.accessEndDate),
          sanitize(tcs.startDate),
          sanitize(tcs.startVolume),
          sanitize(tcs.startIssue),
          sanitize(tcs.endDate),
          sanitize(tcs.endVolume),
          sanitize(tcs.endIssue),
          sanitize(tcs.embargo),
          sanitize(tcs.coverageDepth),
          sanitize(tcs.coverageNote),
          sanitize(tipp.hostPlatform.primaryUrl),
          sanitize(tipp.format?.value),
          sanitize(tipp.paymentType?.value),
          sanitize(tipp.getIdentifierValue('doi') ?: ti?.getIdentifierValue('doi')),
          sanitize(tipp.getIdentifierValue('isbn') ?: ti?.getIdentifierValue('isbn')),
          sanitize(tipp.getIdentifierValue('pisbn') ?: ti?.getIdentifierValue('pisbn'))
        ]

        recordList << record
      }
    }
    else {
      def record = [
        tipp.getId(),
        sanitize(tipp.url),
        sanitize(ti?.getId()),
        sanitize(tipp.name ?: ti?.name),
        sanitize(tipp.status.value),
        sanitize(ti?.getCurrentPublisher()?.name),
        sanitize(ti?.imprint?.name),
        sanitize(ti?.publishedFrom),
        sanitize(ti?.publishedTo),
        sanitize(ti?.medium?.value),
        sanitize(ti?.OAStatus?.value),
        sanitize(ti?.continuingSeries?.value),
        sanitize(tipp.getIdentifierValue('issn') ?: ti?.getIdentifierValue('issn')),
        sanitize(tipp.getIdentifierValue('eissn') ?: ti?.getIdentifierValue('eissn')),
        sanitize(tipp.getIdentifierValue('zdb') ?: ti?.getIdentifierValue('zdb')),
        sanitize(tipp.pkg.name),
        sanitize(tipp.pkg.id),
        "",
        sanitize(tipp.hostPlatform.name),
        sanitize(tipp.hostPlatform.primaryUrl),
        sanitize(tipp.hostPlatform.getId()),
        "",
        sanitize(tipp.editStatus?.value),
        sanitize(tipp.accessStartDate),
        sanitize(tipp.accessEndDate),
        sanitize(tipp.startDate),
        sanitize(tipp.startVolume),
        sanitize(tipp.startIssue),
        sanitize(tipp.endDate),
        sanitize(tipp.endVolume),
        sanitize(tipp.endIssue),
        sanitize(tipp.embargo),
        sanitize(tipp.coverageDepth),
        sanitize(tipp.coverageNote),
        sanitize(tipp.hostPlatform.primaryUrl),
        sanitize(tipp.format?.value),
        sanitize(tipp.paymentType?.value),
        sanitize(tipp.getIdentifierValue('doi') ?: ti?.getIdentifierValue('doi')),
        sanitize(tipp.getIdentifierValue('isbn') ?: ti?.getIdentifierValue('isbn')),
        sanitize(tipp.getIdentifierValue('pisbn') ?: ti?.getIdentifierValue('pisbn'))
      ]

      recordList << record
    }

    return recordList
  }
}
