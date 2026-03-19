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
import java.util.concurrent.TimeUnit
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

  def updateFromSource(Long pkgId, def user = null, Job job = null, Long activeGroupId = null, boolean dryRun = false, boolean restrictSize = true) {
    log.debug("updateFromSource ${pkgId}")
    def result = [result: 'OK']
    def activeJobs = concurrencyManagerService?.getComponentJobs(pkgId)

    if (job || activeJobs?.data?.size() == 0) {
      log.debug("UpdateFromSource started")
      result = startSourceUpdate(pkgId, user, job, activeGroupId, dryRun, restrictSize)

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

  private def startSourceUpdate(pid, user, job, activeGroupId, dryRun, restrictSize) {
    log.debug("Source update start..")
    def result = [result: 'OK', dryRun: dryRun]
    Boolean async = (user ? true : false)
    def preferred_group
    Long title_ns_id
    Long title_ns_serial_id
    Long title_ns_mono_id
    Long datafile_id
    def skipInvalid = false
    Boolean deleteMissing = false
    def pkgInfo = [:]
    def startTime = new Date()
    def ftpUrlParts

    Package.withNewSession {
      Package p = Package.get(pid)
      pkgInfo = [name: p.name, type: "Package", id: p.id, uuid: p.uuid]
      Platform pkg_plt = p.nominalPlatform ? Platform.get(p.nominalPlatform.id) : null
      Org pkg_prov = p.provider ? Org.get(p.provider.id) : null
      Source pkg_source = p.source
      preferred_group = activeGroupId ?: (p.curatoryGroups?.size() > 0 ? p.curatoryGroups[0].id : null)
      title_ns_id = pkg_source?.targetNamespace?.id ?: null
      title_ns_serial_id = pkg_source?.titleIdSerial?.id ?: null
      title_ns_mono_id = pkg_source?.titleIdMonograph?.id ?: null

      if ( restrictSize ) {
        def ignoreSizeLimit = pkg_source?.getIgnoreSizeLimit()
        restrictSize = !ignoreSizeLimit
      }

      if (job && !job.startTime) {
        job.startTime = startTime
      }

      isExternalSourceImportOrUpdate = (pkg_source?.importConfig?.value == "WEKB")
      if ( isExternalSourceImportOrUpdate ) {
        result = wekbIngestionService.startTitleImport(pkgInfo, pkg_source, pkg_plt, pkg_prov, p, job, async, restrictSize)

      } else {
        def transferMethod = pkg_source?.getTransferMethod()
        def rdv_FTP = RefdataCategory.lookup('Source.TransferMethod', 'FTP')
        boolean isFtpTransfer = (transferMethod == rdv_FTP)

        if (pkg_source?.url || (isFtpTransfer && pkg_source?.ftpPath)) {
          URL src_url = null
          Boolean dynamic_date = false
          String completeFtpUrl = null

          if(isFtpTransfer){
            ftpUrlParts = webEndpointService.extractFtpUrlParts(pkg_source.getWebEndpoint()?.getUrl(), pkg_source.getFtpPath())
            completeFtpUrl = ftpUrlParts.complete
          }

          def valid_url_string = validationService.checkUrl(isFtpTransfer ? completeFtpUrl : pkg_source?.url, true)
          LocalDate extracted_date
          skipInvalid = pkg_source.skipInvalid ?: false
          def file_info = [:]

          if (valid_url_string) {
            String local_date_string = LocalDate.now().toString()

            if (valid_url_string =~ FIXED_DATE_ENDING_PLACEHOLDER_PATTERN) {
              log.debug("URL contains date placeholder ..")
              src_url = new URL(valid_url_string.replace('{YYYY-MM-DD}', local_date_string))
              dynamic_date = true
            } else {
              def date_pattern_match = (valid_url_string =~ VARIABLE_DATE_ENDING_PLACEHOLDER_PATTERN)

              if (date_pattern_match && date_pattern_match[0].size() > 0) {
                String matched_date_string = date_pattern_match[0][1]
                log.debug("${matched_date_string}")
                extracted_date = LocalDate.parse(matched_date_string)
              }

              src_url = new URL(valid_url_string)
            }

          }
          else {
            log.debug("No source URL!")
            result.result = 'ERROR'
            result.messageCode = 'kbart.errors.url.invalid'
            result.message = "Package source URL is invalid!"

            result.jobInfo = createJobResult(p, job, startTime, dryRun, user, preferred_group, result)

            return result
          }
          if (src_url?.getProtocol() in ['http', 'https'] || isFtpTransfer) {
            def deposit_token = java.util.UUID.randomUUID().toString()
            File tmp_file = TSVIngestionService.handleTempFile(deposit_token)
            def lastRunLocal = pkg_source.lastRun ? pkg_source.lastRun.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : null

            pkg_source.lastRun = new Date()
            pkg_source.save(flush: true)

            if ( isFtpTransfer ) {
                log.debug("Start FTP Update from Source " + pkg_source )
                ftpUrlParts["complete"] = src_url.toString()
                file_info = fetchKbartFileFromFTPServer(tmp_file, pkg_source, ftpUrlParts, dynamic_date, extracted_date, lastRunLocal, restrictSize)
            }
            else { // start not-FTP

              if (!extracted_date || !lastRunLocal || extracted_date > lastRunLocal) {
                log.debug("Request initial URL..")
                file_info = fetchKbartFile(tmp_file, src_url, restrictSize)
              }

              if (file_info.connectError) {
                result.result = 'ERROR'
                result.messageCode = 'kbart.errors.url.connection'
                result.message = "There was an error trying to fetch KBART via URL!"
                result.exceptionMsg = file_info.errorMsg

                result.jobInfo = createJobResult(p, job, startTime, dryRun, user, preferred_group, result)

                return result
              }

              if (file_info.fileSizeError) {
                result.result = 'ERROR'
                result.messageCode = 'kbart.errors.url.fileSize'
                result.message = "The attached KBART file is too big! Files bigger than 20 MB have to be authorized manually by an administrator."

                result.jobInfo = createJobResult(p, job, startTime, dryRun, user, preferred_group, result)

                return result
              }

              if (file_info.accessError) {
                result.result = 'ERROR'
                result.messageCode = 'kbart.errors.url.html'
                result.message = "URL returned HTML, indicating provider configuration issues!"

                result.jobInfo = createJobResult(p, job, startTime, dryRun, user, preferred_group, result)

                return result
              } else if (file_info.mimeTypeError) {
                result.result = 'ERROR'
                result.messageCode = 'kbart.errors.url.mimeType'
                result.message = "KBART URL returned a wrong content type!"
                log.error("KBART url ${src_url} returned MIME type ${file_info.content_mime_type} for file ${file_info.file_name}")

                result.jobInfo = createJobResult(p, job, startTime, dryRun, user, preferred_group, result)

                return result
              } else if (file_info.status && file_info.status != 404) {
                log.debug("URL request failed (status ${file_info.status})!")

                if (file_info.status == 403 || file_info.status == 401) {
                  result.result = 'ERROR'
                  result.messageCode = 'kbart.errors.url.denied'
                  result.message = "URL request returned status ${file_info.status}, skipping further tries!"
                }
                else if (file_info.status >= 500) {
                  result.result = 'ERROR'
                  result.messageCode = 'kbart.errors.url.serverError'
                  result.message = "URL request returned status ${file_info.status}, skipping further tries!"
                }

                result.jobInfo = createJobResult(p, job, startTime, dryRun, user, preferred_group, result)

                return result
              }

              if (!file_info.file_name && (dynamic_date || extracted_date)) {
                LocalDate active_date = LocalDate.now()
                boolean skipLookupByDate = false
                src_url = new URL(src_url.toString().replaceFirst(DATE_PLACEHOLDER_PATTERN, active_date.toString()))
                log.debug("Fetching dated URL for today..")
                file_info = fetchKbartFile(tmp_file, src_url, restrictSize)

                // Look at first of this month
                if (!file_info.file_name) {
                  sleep(500)
                  log.debug("Fetching first of the month..")
                  def som_date_url = new URL(src_url.toString().replaceFirst(DATE_PLACEHOLDER_PATTERN, active_date.withDayOfMonth(1).toString()))
                  file_info = fetchKbartFile(tmp_file, som_date_url, restrictSize)
                }

                // Check all days of this month
                while (!skipLookupByDate && active_date.isAfter(LocalDate.now().minusDays(30)) && !file_info.file_name) {
                  active_date = active_date.minusDays(1)
                  src_url = new URL(src_url.toString().replaceFirst(DATE_PLACEHOLDER_PATTERN, active_date.toString()))
                  log.debug("Fetching dated URL for date ${active_date}")
                  sleep(500)
                  file_info = fetchKbartFile(tmp_file, src_url, restrictSize)

                  if (file_info.mimeTypeError) {
                    skipLookupByDate = true
                  }
                }
              }

              if (file_info.mimeTypeError) {
                result.result = 'ERROR'
                result.messageCode = 'kbart.errors.url.mimeType'
                result.message = "KBART URL returned a wrong content type!"
                log.error("KBART url ${src_url} returned MIME type ${file_info.content_mime_type} for file ${file_info.file_name}")

                result.jobInfo = createJobResult(p, job, startTime, dryRun, user, preferred_group, result)

                return result
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

                    if (!hasFileChanged(pid, datafile.id)) {
                      log.debug("Datafile was already the last import for this package!")
                      result.result = 'SKIPPED'
                      result.message = 'Skipped repeated import of the same file for this package.'
                      result.messageCode = 'kbart.transmission.skipped.sameFile'

                      tmp_file.delete()

                      result.jobInfo = createJobResult(p, job, startTime, dryRun, user, preferred_group, result)

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

                  result.jobInfo = createJobResult(p, job, startTime, dryRun, user, preferred_group, result)

                  return result
                }
              } catch (IOException e) {
                // handle exception
                log.error("Failed DataFile handling", e)
              }


              tmp_file.delete()
            } else {
              result.message = "No KBART found for provided URL!"
              result.messageCode = 'kbart.transmission.skipped.noFile'
              result.result = 'SKIPPED'
              log.debug("KBART url ${src_url} returned MIME type ${file_info.content_mime_type}")

              result.jobInfo = createJobResult(p, job, startTime, dryRun, user, preferred_group, result)

              return result
            }
          }
          else {
            result.result = 'ERROR'
            result.messageCode = 'kbart.errors.url.protocol'
            result.message = "KBART URL has an unsupported protocol!"
            log.debug("Unsupported protocol for URL ${src_url}")

            result.jobInfo = createJobResult(p, job, startTime, dryRun, user, preferred_group, result)

            return result
          }
        } else {
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
                                                    user,
                                                    preferred_group,
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
                                            user,
                                            preferred_group,
                                            dryRun,
                                            skipInvalid,
                                            deleteMissing,
                                            j,
                                            title_ns_serial_id,
                                            title_ns_mono_id)
        }

        if (preferred_group) {
          update_job.groupId = preferred_group
        }

        if (user) {
          update_job.ownerId = user
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

        result.content_mime_type = classicHttpResponse.getFirstHeader('Content-Type').getValue()

        if (code >= 400) {
          log.debug("KBART fetch status: ${code}")

          if (code != 404) {
            result.status = code
            return result
          }
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

  public Boolean hasFileChanged(pkgId, datafileId) {
    RefdataValue type_fa = RefdataCategory.lookup('Combo.Type', 'KBComponent.FileAttachments')

    def ordered_combos = Combo.executeQuery('''select c.toComponent.id from Combo as c
                                              where c.type = :ct
                                              and c.fromComponent.id = :pkg
                                              order by c.dateCreated desc''',
                                              [ct: type_fa, pkg: pkgId])

    return (ordered_combos.size() == 0 || ordered_combos[0] != datafileId)
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

  def fetchKbartFileFromFTPServer (File tmp_file, Source source, def urlParts, boolean dynamic_date, LocalDate extracted_date, LocalDate lastRunLocal, boolean restrictSize = true) {

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

      if(dynamic_date){
        // in case of dynamic_date, in the filename the pattern is already replaced by the actual date
        String[] parts = urlParts.complete.split("/")
        filename = parts[parts.length - 1]
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

          if(dynamic_date || extracted_date) {

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