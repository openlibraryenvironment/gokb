package org.gokb

import com.k_int.ConcurrencyManagerService.Job

import grails.converters.JSON
import grails.util.Environment
import groovy.util.logging.Slf4j
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPFile
import org.apache.http.HttpEntity
import org.apache.http.HttpHeaders
import org.apache.http.util.EntityUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.HttpClients
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal
import java.time.temporal.TemporalUnit
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern

import org.gokb.cred.*
import org.mozilla.universalchardet.UniversalDetector
import org.apache.commons.net.*

import java.util.stream.Collectors

@Slf4j
class PackageSourceUpdateService {
  def concurrencyManagerService
  def TSVIngestionService
  def validationService
  WekbIngestionService wekbIngestionService
  boolean isExternalSourceImportOrUpdate
  WebEndpointService webEndpointService

  static Pattern DATE_PLACEHOLDER_PATTERN = ~/[0-9]{4}-[0-9]{2}-[0-9]{2}/
  static Pattern FIXED_DATE_ENDING_PLACEHOLDER_PATTERN = ~/\{YYYY-MM-DD\}\.(tsv|txt)(\?.*)?$/
  static Pattern VARIABLE_DATE_ENDING_PLACEHOLDER_PATTERN = ~/([12][0-9]{3}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01]))\.(tsv|txt)(\?.*)?$/


  @javax.annotation.PostConstruct
  def init() {
    log.info("Initialising source update service...")
  }

  public Map updateFromSource(Long pkgId, Long userId = null, Job job = null, Long activeGroupId = null, boolean dryRun = false, boolean restrictSize = true) {
    log.debug("updateFromSource ${pkgId}")
    Map result = [result: 'OK']
    Map activeJobs = concurrencyManagerService?.getComponentJobs(pkgId)

    if (job || activeJobs?.data?.size() == 0) {
      log.debug("UpdateFromSource started")
      result = startSourceUpdate(pkgId, userId, job, activeGroupId, dryRun, restrictSize)

      if (job && !job.endTime) {
        job.endTime = new Date()
      }
    }
    else {
      log.error("update skipped - already running")
      result.result = 'ALREADY_RUNNING'
    }
    result
  }

  private Map startSourceUpdate(pid, userId, job, activeGroupId, dryRun, restrictSize) {
    log.debug("Source update start..")
    Map result = [result: 'OK', dryRun: dryRun]
    Boolean async = (userId ? true : false)
    Long preferred_group_id
    Long title_ns_id
    Long title_ns_serial_id
    Long title_ns_mono_id
    Long datafile_id
    boolean skipInvalid = false
    boolean deleteMissing = false
    Map pkgInfo = [:]
    Date startTime = new Date()
    Map ftpUrlParts = [:]
    List<URL> urls

    Package.withNewSession {
      Package p = Package.get(pid)
      pkgInfo = [name: p.name, type: "Package", id: p.id, uuid: p.uuid]
      Platform pkg_plt = p.nominalPlatform ? Platform.get(p.nominalPlatform.id) : null
      Org pkg_prov = p.provider ? Org.get(p.provider.id) : null
      Source pkg_source = p.source
      preferred_group_id = activeGroupId ?: (p.curatoryGroups?.size() > 0 ? p.curatoryGroups[0].id : null)
      title_ns_id = pkg_source?.targetNamespace?.id ?: null
      title_ns_serial_id = pkg_source?.titleIdSerial?.id ?: null
      title_ns_mono_id = pkg_source?.titleIdMonograph?.id ?: null

      if ( restrictSize ) {
        boolean ignoreSizeLimit = pkg_source?.getIgnoreSizeLimit()
        restrictSize = !ignoreSizeLimit
      }

      if (job && !job.startTime) {
        job.startTime = startTime
      }

      isExternalSourceImportOrUpdate = (pkg_source?.importConfig?.value == "WEKB")
      if ( isExternalSourceImportOrUpdate ) {
        result = wekbIngestionService.startTitleImport(pkgInfo, pkg_source, pkg_plt, pkg_prov, p, job, async, restrictSize)

      } else {
        RefdataValue transferMethod = pkg_source?.getTransferMethod()
        RefdataValue rdv_FTP = RefdataCategory.lookup('Source.TransferMethod', 'FTP')
        boolean isFtpTransfer = (transferMethod == rdv_FTP)

        if (pkg_source?.url || (isFtpTransfer && pkg_source?.ftpPath)) {
          URL src_url = null
          String completeFtpUrl = null

          if (isFtpTransfer){
            ftpUrlParts = webEndpointService.extractFtpUrlParts(pkg_source.getWebEndpoint()?.getUrl(), pkg_source.getFtpPath())
            completeFtpUrl = ftpUrlParts.complete
          }

          String valid_url_string = validationService.checkUrl(isFtpTransfer ? completeFtpUrl : pkg_source?.url, true)

          skipInvalid = pkg_source.skipInvalid ?: false
          Map file_info = [:]

          if (valid_url_string) {

            urls = findUrlsToCall(valid_url_string, pkg_source, isFtpTransfer)
            src_url = urls.get(0)

          }
          else {
            log.debug("No source URL!")
            result.result = 'ERROR'
            result.messageCode = 'kbart.errors.url.invalid'
            result.message = "Package source URL is invalid!"

            result.jobInfo = createJobResult(p, job, startTime, dryRun, userId, preferred_group_id, result)

            return result
          }
          if (src_url?.getProtocol() in ['http', 'https'] || isFtpTransfer) {
            def deposit_token = java.util.UUID.randomUUID().toString()
            File tmp_file = TSVIngestionService.handleTempFile(deposit_token)

            pkg_source.lastRun = new Date()
            pkg_source.save(flush: true)

            if ( isFtpTransfer ) {
                log.debug("Start FTP Update from Source " + pkg_source )
                ftpUrlParts["complete"] = src_url.toString()
                file_info = fetchKbartFileFromFTPServer(tmp_file, pkg_source, ftpUrlParts, restrictSize)
            }
            else { // start not-FTP

              for(int i = 0; i < urls.size(); i++) {
                log.debug("Fetching URL " + urls.get(i))
                file_info = fetchKbartFile(tmp_file, urls.get(i), restrictSize)

                processErrorState(result, pkg_source, file_info)

                if (result.result == 'ERROR') {
                  result.jobInfo = createJobResult(p, job, startTime, dryRun, userId, preferred_group_id, result)
                  return result
                }

                if (file_info.file_name) {
                  // set lastFoundFile property
                  if (extractDateFromUrl(file_info.file_name) != null) {
                    pkg_source.lastImportFileDate = extractDateFromUrl(file_info.file_name)
                  }
                  else {
                    pkg_source.lastImportFileDate = LocalDate.now()
                  }

                  pkg_source.save(flush: true)
                  break
                }
                else {
                  sleep(500)
                }

              }

              log.debug("Got mime type ${file_info.content_mime_type} for file ${file_info.file_name}")

            } // end not-FTP

            if (file_info.file_name) {
              try {
                MessageDigest md5_digest = MessageDigest.getInstance("MD5")
                UniversalDetector detector = new UniversalDetector()
                FileInputStream fis = new FileInputStream(tmp_file)
                BufferedInputStream inputStream = new BufferedInputStream(fis)
                int total_size = 0
                byte[] dataBuffer = new byte[4096]
                int bytesRead

                while ((bytesRead = inputStream.read(dataBuffer, 0, 4096)) != -1) {
                  md5_digest.update(dataBuffer, 0, bytesRead)
                  detector.handleData(dataBuffer, 0, bytesRead)
                  total_size += bytesRead
                }

                log.debug("Read $total_size bytes..")

                detector.dataEnd()
                byte[] md5sum = md5_digest.digest()
                file_info.md5sumHex = new BigInteger(1, md5sum).toString(16)

                String encoding = detector.getDetectedCharset()

                if (encoding in ['UTF-8', 'US-ASCII']) {
                  DataFile datafile = DataFile.findByMd5(file_info.md5sumHex)

                  if (!datafile) {
                    log.debug("Create new datafile")
                    datafile = new DataFile(
                            guid: deposit_token,
                            md5: file_info.md5sumHex,
                            uploadName: file_info.file_name,
                            name: file_info.file_name,
                            filesize: total_size,
                            encoding: encoding,
                            uploadMimeType: file_info.content_mime_type).save()
                    datafile.fileData = tmp_file.getBytes()
                    datafile.save(failOnError: true, flush: true)
                    log.debug("Saved new datafile : ${datafile.id}")
                    datafile_id = datafile.id
                  } else {
                    log.debug("Found existing datafile ${datafile}")
                    // userId != null means execution from ui, the same file can be forced to be imported twice
                    if (!userId && !hasFileChanged(pid, datafile.md5)) {
                      log.debug("Datafile was already the last import for this package!")
                      result.result = 'SKIPPED'
                      result.message = 'Skipped repeated import of the same file for this package.'
                      result.messageCode = 'kbart.transmission.skipped.sameFile'

                      tmp_file.delete()

                      result.jobInfo = createJobResult(p, job, startTime, dryRun, userId, preferred_group_id, result)

                      return result
                    }

                    datafile_id = datafile.id
                  }
                } else {
                  log.error("Illegal charset ${encoding} found..")
                  result.result = 'ERROR'
                  result.messageCode = 'kbart.errors.url.charset'
                  result.message = "KBART is not UTF-8!"

                  tmp_file.delete()

                  result.jobInfo = createJobResult(p, job, startTime, dryRun, userId, preferred_group_id, result)

                  return result
                }
              } catch (IOException e) {
                // handle exception
                log.error("Failed DataFile handling", e)
              }


              tmp_file.delete()
            } else {
              result.message = "No KBART found for provided URL!"
              result.messageCode = "Yearly".equals(pkg_source.frequency?.value) ? 'kbart.errors.skipped.noFileForAYear' : 'kbart.transmission.skipped.noFile'
              result.result = 'SKIPPED'

              result.jobInfo = createJobResult(p, job, startTime, dryRun, userId, preferred_group_id, result)

              return result
            }
          }
          else {
            result.result = 'ERROR'
            result.messageCode = 'kbart.errors.url.protocol'
            result.message = "KBART URL has an unsupported protocol!"
            log.debug("Unsupported protocol for URL ${src_url}")

            result.jobInfo = createJobResult(p, job, startTime, dryRun, userId, preferred_group_id, result)

            return result
          }
        }
        else {
          log.debug("No source URL!")
          result.result = 'ERROR'
          result.messageCode = 'kbart.errors.url.missing'
          result.message = "Package source does not have an URL!"

          return result
        }
      }
    }

    if (datafile_id) {
      if (job) {
        result = TSVIngestionService.updatePackage(pid,
                                                    datafile_id,
                                                    title_ns_id,
                                                    async,
                                                    false,
                                                    userId,
                                                    preferred_group_id,
                                                    dryRun,
                                                    skipInvalid,
                                                    deleteMissing,
                                                    job,
                                                    title_ns_serial_id,
                                                    title_ns_mono_id)
      }
      else {
        Job update_job = concurrencyManagerService.createJob { Job j ->
          TSVIngestionService.updatePackage(pid,
                                            datafile_id,
                                            title_ns_id,
                                            async,
                                            false,
                                            userId,
                                            preferred_group_id,
                                            dryRun,
                                            skipInvalid,
                                            deleteMissing,
                                            j,
                                            title_ns_serial_id,
                                            title_ns_mono_id)
        }

        if (preferred_group_id) {
          update_job.groupId = preferred_group_id
        }

        if (userId) {
          update_job.ownerId = userId
        }

        update_job.description = "KBART Source ingest (${pkgInfo.name})".toString()
        update_job.type = dryRun ? RefdataCategory.lookup('Job.Type', 'KBARTSourceIngestDryRun') : RefdataCategory.lookup('Job.Type', 'KBARTSourceIngest')
        update_job.linkedItem = pkgInfo
        update_job.message("Starting upsert for Package ${pkgInfo.name}".toString())
        update_job.startOrQueue()

        try {
          result.job_result = update_job.get()
        }
        catch (Exception e) {
          log.error("Package import threw an exception!", e)
        }
      }
    }
    else if (!isExternalSourceImportOrUpdate && result.result != 'SKIPPED') {
      log.debug("Unable to reference DataFile")
      result.result = 'ERROR'
      result.messageCode = 'kbart.errors.url.unknown'
      result.message = "There were errors saving the KBART file!"
    }

    result
  }

  LocalDate extractDateFromUrl(String url) {
    LocalDate extractedDate = null
    Matcher date_pattern_match = (url =~ VARIABLE_DATE_ENDING_PLACEHOLDER_PATTERN)

    if (date_pattern_match && date_pattern_match[0].size() > 0) {
      String matched_date_string = date_pattern_match[0][1]
      extractedDate = LocalDate.parse(matched_date_string)
    }
    return extractedDate
  }

  List<URL> findUrlsToCall (String givenUrl, Source source, boolean isFtpTransfer) {
    List<URL> urls = new ArrayList<>()
    boolean dynamic_date = false
    boolean fixed_date = false
    LocalDate active_date = LocalDate.now()
    LocalDate lastImportFileDate = source.lastImportFileDate ? source.lastImportFileDate : null

    String local_date_string = LocalDate.now().toString()

    if (givenUrl =~ FIXED_DATE_ENDING_PLACEHOLDER_PATTERN) {
      log.debug("URL contains date Mask ..")
      givenUrl = givenUrl.replace('{YYYY-MM-DD}', local_date_string)
      dynamic_date = true
    }
    else {
      if (extractDateFromUrl(givenUrl)) {
        log.debug("URL contains fix date ..")
        fixed_date = true
      }
    }

    urls.add(new URL(givenUrl))

    if (!isFtpTransfer && (dynamic_date || fixed_date) && source.frequency) {

      // search for the file in most likely order
      Map<String, Integer> maxCallsPerFrequency = [
              "Weekly"   : 7,
              "Monthly"  : 31,
              "Quarterly": 92,
              "Yearly"   : 366,
      ]

      // set lastFoundFile + n * updateInterval as anchor date to search for the new file
      LocalDate anchorDate
      TemporalUnit temporalUnit = ChronoUnit.WEEKS
      boolean isQuarterly = false

      long specificTimeUnitsSinceLastFound
      switch (source.frequency) {
        case RefdataCategory.lookup("Source.Frequency", "Weekly"):
          break
        case RefdataCategory.lookup("Source.Frequency", "Monthly"):
          temporalUnit = ChronoUnit.MONTHS
          break
        case RefdataCategory.lookup("Source.Frequency", "Quarterly"):
          temporalUnit = ChronoUnit.MONTHS
          isQuarterly = true
          break
        case RefdataCategory.lookup("Source.Frequency", "Yearly"):
          temporalUnit = ChronoUnit.YEARS
          break
        default:
          break
      }

      LocalDate minDate = isQuarterly ? active_date.minus(3, temporalUnit) : active_date.minus(1, temporalUnit)

      if (lastImportFileDate) {
        // Division for quarterly update results in 'lower Gaussian Number' (i.e. Abrundung)
        specificTimeUnitsSinceLastFound = isQuarterly ? temporalUnit.between(lastImportFileDate, active_date) / 3 : temporalUnit.between(lastImportFileDate, active_date)
        anchorDate = isQuarterly ? lastImportFileDate.plus(specificTimeUnitsSinceLastFound * 3, temporalUnit) : lastImportFileDate.plus(specificTimeUnitsSinceLastFound, temporalUnit)
        //we just go back to the last found date
        if (lastImportFileDate.isAfter(minDate)) {
          minDate = lastImportFileDate
        }
      } else {
        anchorDate = active_date
      }

      URL firstCall = new URL(givenUrl.replaceFirst(DATE_PLACEHOLDER_PATTERN, anchorDate.toString()))
      if (!urls.contains(firstCall)) {
        urls.add(firstCall)
      }

      int added = urls.size()
      int diff = 1
      int maxToAdd = maxCallsPerFrequency.get(source.frequency?.value)
      boolean upperAvailable = true
      boolean lowerAvailable = true

      while (added <= maxToAdd && (upperAvailable || lowerAvailable)) {
        if (!active_date.isBefore(anchorDate.plusDays(diff))) {
          URL urlCandidate = new URL(givenUrl.replaceFirst(DATE_PLACEHOLDER_PATTERN, (anchorDate.plusDays(diff).toString())))
          if (!urls.contains(urlCandidate)) {
            urls.add(urlCandidate)
            added++
          }
        } else {
          upperAvailable = false
        }
        if (!minDate.isAfter(anchorDate.minusDays(diff))) {
          URL urlCandidate = new URL(givenUrl.replaceFirst(DATE_PLACEHOLDER_PATTERN, (anchorDate.minusDays(diff).toString())))
          if (!urls.contains(urlCandidate)) {
            urls.add(urlCandidate)
            added++
          }
        } else {
          lowerAvailable = false
        }

        diff++
      }

    }

    return urls
  }

  private void processErrorState(result, pkg_source, file_info) {
    if (file_info.connectError) {
      result.result = 'ERROR'
      result.messageCode = 'kbart.errors.url.connection'
      result.message = "There was an error trying to fetch KBART via URL!"
      result.exceptionMsg = file_info.errorMsg
    }

    if (file_info.fileSizeError) {
      result.result = 'ERROR'
      result.messageCode = 'kbart.errors.url.fileSize'
      result.message = "The attached KBART file is too big! Files bigger than 20 MB have to be authorized manually by an administrator."
    }

    if (file_info.accessError) {
      result.result = 'ERROR'
      result.messageCode = 'kbart.errors.url.html'
      result.message = "URL returned HTML, indicating provider configuration issues!"
    }
    else if (file_info.mimeTypeError) {
      result.result = 'ERROR'
      result.messageCode = 'kbart.errors.url.mimeType'
      result.message = "KBART URL returned a wrong content type!"
    }
    else if (file_info.status && file_info.status != 404) {
      log.debug("URL request failed (status ${file_info.status})!")

      if (file_info.status == 403 || file_info.status == 401) {
        result.result = 'ERROR'
        result.messageCode = 'kbart.errors.url.denied'
        result.message = "URL request was denied (status ${file_info.status}), skipping further tries!"

        if (pkg_source.automaticUpdates) {
          log.debug("Deactivate automated updating ..")
          pkg_source.automaticUpdates = false
          pkg_source.save(flush: true)
        }
      }
      else if (file_info.status >= 500) {
        result.result = 'ERROR'
        result.messageCode = 'kbart.errors.url.serverError'
        result.message = "URL request returned status ${file_info.status}, skipping further tries!"
      }
    }
  }

  def fetchKbartFile(File tmp_file, URL src_url, boolean restrictSize = true) {
    def result = [content_mime_type: null, file_name: null]
    Long max_length = 20971520L // 1024 * 1024 * 20
    Long content_length

    RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(10000)
            .setSocketTimeout(30000)
            .build()

    HttpClientBuilder builder = HttpClients.custom()
            .setDefaultRequestConfig(requestConfig)

    try (CloseableHttpClient httpClient = builder.build()) {
      HttpHead httpHead = new HttpHead(src_url.toURI())
      HttpGet httpGet = new HttpGet(src_url.toURI())

      httpHead.setHeader(HttpHeaders.USER_AGENT, "GOKB KBART Updater")
      httpGet.setHeader(HttpHeaders.USER_AGENT, "GOKB KBART Updater")

      httpClient.execute(httpHead, classicHttpResponse -> {
        int code = classicHttpResponse.getStatusLine().getStatusCode()

        if (code == 405) {
          log.debug("Unable to send HEAD request ..")
        }
        else if (code) {
          content_length = classicHttpResponse.containsHeader('Content-Length') ? Long.valueOf(classicHttpResponse.getFirstHeader('Content-Length').getValue()) : null
        }

        // reject files bigger than 20 MB
        if (restrictSize && content_length && content_length > max_length) {
          result.fileSizeError = true
          return result
        }
      })

      httpClient.execute(httpGet, classicHttpResponse -> {
        int code = classicHttpResponse.getStatusLine().getStatusCode()

        String file_name = classicHttpResponse.containsHeader('Content-Disposition') ? classicHttpResponse.getFirstHeader('Content-Disposition').getValue() : null

        if (file_name?.contains('filename=')) {
          file_name = file_name.split('filename=')[1]
        }
        else if (file_name?.contains('filename*=')) {
          file_name = file_name.split('filename*=')[1].split("'")[2]
        }

        result.content_mime_type = classicHttpResponse.getFirstHeader('Content-Type')?.getValue() ?: 'application/octet-stream'

        if (code >= 400) {
          log.debug("KBART fetch status: ${code}")

          result.status = code
          return result
        }
        else if (!file_name && result.content_mime_type?.startsWith('text/plain')) {
          file_name = src_url.toString().split('/')[src_url.toString().split('/').size() - 1]
        }
        else if (!file_name && result.content_mime_type?.startsWith('text/html')) {
          log.warn("Got HTML result at KBART URL ${src_url}!")
          result.accessError = true
          return result
        }

        content_length = classicHttpResponse.containsHeader('Content-Length') ? Long.valueOf(classicHttpResponse.getFirstHeader('Content-Length').getValue()) : null

        if (restrictSize && content_length && content_length > max_length) {
          result.fileSizeError = true
        }
        else if (file_name?.trim()) {
          file_name = file_name.replaceAll(/\"/, '')

          if ((file_name?.trim()?.endsWith('.tsv') || file_name?.trim()?.endsWith('.txt') || file_name?.trim()?.endsWith('.kbart')) &&
                  (result.content_mime_type?.startsWith("text/plain") ||
                          result.content_mime_type?.startsWith("text/csv") ||
                          result.content_mime_type?.startsWith("text/tab-separated-values") ||
                          result.content_mime_type == 'application/octet-stream')) {
            HttpEntity entity = classicHttpResponse.getEntity();
            result.file_name = file_name

            if (entity != null) {
              try (InputStream inputStream = entity.getContent(); FileOutputStream fileOutputStream = new FileOutputStream(tmp_file)) {
                byte[] dataBuffer = new byte[1024];
                int bytesRead;
                Long current_total = 0;

                while((bytesRead = inputStream.read(dataBuffer)) != -1) {
                  fileOutputStream.write(dataBuffer, 0, bytesRead);

                  current_total += bytesRead

                  if (restrictSize && !content_length && current_total > max_length) {
                    result.fileSizeError = true
                    break
                  }
                }

                if (!result.fileSizeError) {
                  EntityUtils.consume(entity)
                }
              }

              if (result.fileSizeError) {
                tmp_file.delete()
              }
            }
          }
          else {
            result.mimeTypeError = true
          }
        }
      })
    }
    catch (Exception e) {
      result.connectError = true
      result.errorMsg = e.message
      log.error("failed fetching file via ${src_url}", e)
    }

    result
  }

  public Boolean hasFileChanged(pkgId, checksum) {
    List ordered_links = ComponentAttachment.executeQuery('''select c.file.md5 from ComponentAttachment as c
                                                              where c.component.id = :pkg
                                                              order by c.dateCreated desc''',
                                                              [ct: type_fa, pkg: pkgId], [max: 1])

    return (ordered_links.size() == 0 || ordered_links[0] != checksum)
  }

  private def createJobResult(pkg, job, startTime, dryRun, ownerId, groupId, result) {
    def job_map = [:]
    def job_uuid = job?.uuid ?: UUID.randomUUID().toString()

    if (job) {
      job_map = [
        uuid        : (job_uuid),
        description : (job.description),
        resultObject: (result as JSON).toString(),
        type        : (job.type),
        statusText  : (result.result),
        ownerId     : (job.ownerId),
        groupId     : (job.groupId),
        startTime   : (job.startTime),
        endTime     : (new Date()),
        linkedItemId: (job.linkedItem?.id)
      ]
    }
    else {
      job_map = [
        uuid        : (job_uuid),
        description : ("KBART Source ingest (${pkg.name})".toString()),
        resultObject: (result as JSON).toString(),
        type        : (dryRun ? RefdataCategory.lookup('Job.Type', 'KBARTSourceIngestDryRun') : RefdataCategory.lookup('Job.Type', 'KBARTSourceIngest')),
        statusText  : (result.result),
        ownerId     : (ownerId),
        groupId     : (groupId),
        startTime   : (startTime),
        endTime     : (new Date()),
        linkedItemId: (pkg.id)
      ]
    }

    def result_object = JobResult.findByUuid(job_uuid)

    if (!result_object) {
      def jr = new JobResult(job_map).save(flush: true, failOnError: true)
    }
    else {
      job_map.each { k, v ->
        result_object[k] = v
        result_object.save(flush: true)
      }
    }

    def info_map = [
      uuid: job_map.uuid,
      groupId: job_map.groupId,
      linkedItemId: pkg.id,
      linkedItemName: pkg.name,
      startTime: job_map.startTime,
      endTime: job_map.endTime,
      messageCode: result.messageCode
    ]

    return info_map
  }

  def fetchKbartFileFromFTPServer (File tmp_file, Source source, def urlParts, boolean restrictSize = true) {

      def result = [content_mime_type: null, file_name: null]
      Long max_length = 20971520L // 1024 * 1024 * 20

      FTPClient ftp = new FTPClient()
      FTPClientConfig config = new FTPClientConfig()

      String username = source.getWebEndpoint().getEpUsername()
      String password = source.getWebEndpoint().getEpPassword()

      String hostname = urlParts.hostname
      String directory = urlParts.directory
      String filename = urlParts.filename
      String foundFileName = null
      FTPFile foundFile = null
      boolean isUrlWithDate = false

      if (extractDateFromUrl(urlParts.complete)) {
        // in case of date mask, the pattern in the filename is already replaced by the actual date
        String[] parts = urlParts.complete.split("/")
        filename = parts[parts.length - 1]
        isUrlWithDate = true
      }

      try {
        // for integration test purpose
        if (Environment.current == Environment.TEST) {
          ftp.connect(hostname, 12345)
        }
        else {
          ftp.connect(hostname)
        }
        ftp.enterLocalPassiveMode()
        def loggedIn = ftp.login(username, password)

        if (ftp.isConnected()) {

          ftp.changeWorkingDirectory(directory)
          List files

          if(isUrlWithDate) {

            def dateMaskMatch = (filename =~ VARIABLE_DATE_ENDING_PLACEHOLDER_PATTERN)
            String matchedDate = dateMaskMatch[0][1]
            String fixFilenamePart = filename.substring(0, filename.indexOf(matchedDate))

            files = ftp.listFiles().toList()
            List unorderedRes = files.stream().filter(f ->
                    f.name =~ VARIABLE_DATE_ENDING_PLACEHOLDER_PATTERN && f.name.startsWith(fixFilenamePart))
                    .collect(Collectors.toList())
            List res = unorderedRes.sort((f1, f2) -> f1.getName().compareTo(f2.getName()))

            // file with latest date is the last in list res
            if(res.size() > 0) {
              foundFile = res.get(res.size() - 1)
              foundFileName = foundFile.name
            }
          }
          else {
            files = ftp.listFiles(filename).toList()
            if(files.size() > 0) {
              foundFile = files.get(0)
              foundFileName = filename
            }
          }


          if(foundFile){

            Long foundFileSize = foundFile.getSize()
            if(foundFileSize > max_length && restrictSize){
              result.fileSizeError = true
              result.result = 'ERROR'
              result.messageCode = 'kbart.errors.url.fileSize'
              result.message = "The attached KBART file is too big! Files bigger than 20 MB have to be authorized manually by an administrator."

              tmp_file.delete()

              return result
            }

            result.file_name = foundFileName

            InputStream is = ftp.retrieveFileStream(foundFileName)
            OutputStream outStream = new FileOutputStream(tmp_file)

            byte[] buffer = new byte[1024];
            for (int length; (length = is.read(buffer)) != -1; ) {
              outStream.write(buffer, 0, length);
            }

            outStream.close()

          }
          else {
            // no filename --> handled in calling method

          }

          ftp.logout()
          ftp.disconnect()
        }

      } catch (Exception e) {
          log.error("Fehler bei FTP-Verbindung ", e)

      }

      return result

  }





}