package org.gokb

import com.k_int.ConcurrencyManagerService.Job

import grails.gorm.transactions.*

import groovy.util.logging.Slf4j

import java.net.http.*
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.HttpRequest.BodyPublishers
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.regex.Pattern

import org.gokb.cred.*
import org.apache.commons.io.FileUtils
import org.apache.commons.io.input.BoundedInputStream
import org.mozilla.universalchardet.UniversalDetector

@Slf4j
class PackageSourceUpdateService {
  def concurrencyManagerService
  def TSVIngestionService
  def validationService

  static Pattern DATE_PLACEHOLDER_PATTERN = ~/[0-9]{4}-[0-9]{2}-[0-9]{2}/
  static Pattern FIXED_DATE_ENDING_PLACEHOLDER_PATTERN = ~/\{YYYY-MM-DD\}\.(tsv|txt)$/
  static Pattern VARIABLE_DATE_ENDING_PLACEHOLDER_PATTERN = ~/([12][0-9]{3}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01]))\.(tsv|txt)$/

  @javax.annotation.PostConstruct
  def init() {
    log.info("Initialising source update service...")
  }

  def updateFromSource(Long pkgId, def user = null, Job job = null, Long activeGroupId = null, boolean dryRun = false, boolean restrictSize = true) {
    log.debug("updateFromSource ${pkgId}")
    def result = [result: 'OK']
    def activeJobs = concurrencyManagerService.getComponentJobs(pkgId)

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
    def result = [result: 'OK']
    Boolean async = (user ? true : false)
    def preferred_group
    def title_ns
    Long datafile_id
    def skipInvalid = false
    Boolean deleteMissing = false
    def pkgInfo = [:]

    Package.withNewSession {
      Package p = Package.get(pid)
      pkgInfo = [name: p.name, type: "Package", id: p.id, uuid: p.uuid]
      Platform pkg_plt = p.nominalPlatform ? Platform.get(p.nominalPlatform.id) : null
      Org pkg_prov = p.provider ? Org.get(p.provider.id) : null
      Source pkg_source = p.source
      preferred_group = activeGroupId ?: (p.curatoryGroups?.size() > 0 ? p.curatoryGroups[0].id : null)
      title_ns = pkg_source?.targetNamespace?.id ?: (pkg_prov?.titleNamespace?.id ?: null)

      if (job && !job.startTime) {
        job.startTime = new Date()
      }

      if (pkg_source?.url) {
        URL src_url = null
        Boolean dynamic_date = false
        def valid_url_string = validationService.checkUrl(pkg_source?.url)
        LocalDate extracted_date
        skipInvalid = pkg_source.skipInvalid ?: false
        def file_info = [:]

        if (valid_url_string) {
          String local_date_string = LocalDate.now().toString()

          if (valid_url_string =~ FIXED_DATE_ENDING_PLACEHOLDER_PATTERN) {
            log.debug("URL contains date placeholder ..")
            src_url = new URL(valid_url_string.replace('{YYYY-MM-DD}', local_date_string))
            dynamic_date = true
          }
          else {
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

          return result
        }

        if (src_url?.getProtocol() in ['http', 'https']) {
          def deposit_token = java.util.UUID.randomUUID().toString()
          File tmp_file = TSVIngestionService.handleTempFile(deposit_token)
          def lastRunLocal = pkg_source.lastRun ? pkg_source.lastRun.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : null

          pkg_source.lastRun = new Date()
          pkg_source.save(flush: true)

          if (!extracted_date || !lastRunLocal || extracted_date > lastRunLocal) {
            log.debug("Request initial URL..")
            file_info = fetchKbartFile(tmp_file, src_url, restrictSize)
          }

          if (file_info.connectError) {
            result.result = 'ERROR'
            result.messageCode = 'kbart.errors.url.connection'
            result.message = "There was an error trying to fetch KBART via URL!"

            return result
          }

          if (file_info.fileSizeError) {
            result.result = 'ERROR'
            result.messageCode = 'kbart.errors.url.fileSize'
            result.message = "The attached KBART file is too big! Files bigger than 20 MB have to be authorized manually by an administrator."

            return result
          }

          if (file_info.accessError) {
            result.result = 'ERROR'
            result.messageCode = 'kbart.errors.url.html'
            result.message = "URL returned HTML, indicating provider configuration issues!"

            return result
          }
          else if (file_info.mimeTypeError) {
            result.result = 'ERROR'
            result.messageCode = 'kbart.errors.url.mimeType'
            result.message = "KBART URL returned a wrong content type!"
            log.error("KBART url ${src_url} returned MIME type ${file_info.content_mime_type} for file ${file_info.file_name}")

            return result
          }
          else if (file_info.status == 403) {
            log.debug("URL request failed!")
            result.result = 'ERROR'
            result.messageCode = 'kbart.errors.url.denied'
            result.message = "URL request returned 403 ACCESS DENIED, skipping further tries!"

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

            return result
          }

          log.debug("Got mime type ${file_info.content_mime_type} for file ${file_info.file_name}")

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
                  datafile.save(failOnError:true,flush:true)
                  log.debug("Saved new datafile : ${datafile.id}")
                  datafile_id = datafile.id
                }
                else {
                  log.debug("Found existing datafile ${datafile}")

                  if (!hasFileChanged(pid, datafile.id)) {
                    log.debug("Datafile was already the last import for this package!")
                    result.result = 'SKIPPED'
                    result.message = 'Skipped repeated import of the same file for this package.'
                    result.messageCode = 'kbart.transmission.skipped.sameFile'

                    tmp_file.delete()

                    return result
                  }

                  datafile_id = datafile.id
                }
              }
              else {
                log.error("Illegal charset ${encoding} found..")
                result.result = 'ERROR'
                result.messageCode = 'kbart.errors.url.charset'
                result.message = "KBART is not UTF-8!"

                tmp_file.delete()

                return result
              }
            } catch (IOException e) {
                // handle exception
                log.error("Failed DataFile handling", e)
            }


            tmp_file.delete()
          }
          else {
            result.message = "No KBART found for provided URL!"
            result.messageCode = 'kbart.transmission.skipped.noFile'
            result.result = 'SKIPPED'
            log.debug("KBART url ${src_url} returned MIME type ${file_info.content_mime_type}")

            return result
          }
        }
        // else if (src_url.getProtocol() in ['ftp', 'sftp']) {
        else {
          result.result = 'ERROR'
          result.messageCode = 'kbart.errors.url.protocol'
          result.message = "KBART URL has an unsupported protocol!"
          log.debug("Unsupported protocol for URL ${src_url}")

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

    if (datafile_id) {
      if (job) {
        result = TSVIngestionService.updatePackage(pid,
                                                    datafile_id,
                                                    title_ns,
                                                    async,
                                                    false,
                                                    user,
                                                    preferred_group,
                                                    dryRun,
                                                    skipInvalid,
                                                    deleteMissing,
                                                    job)

        if (hasOpenIssues(pid, async, result)) {
          log.info("There were issues with the automated job (valid: ${result.validation?.valid}, reviews: ${result.report?.reviews}${!async ? ', matching reviews: '  + result.matchingJob?.reviews : ''}), keeping listStatus in progress..")
        }
        else if (!async && !dryRun) {
          log.debug("Setting new listStatus to checked ..")

          Package.withNewTransaction {
            def pack = Package.findById(pid)
            pack.refresh()
            pack.listStatus = RefdataCategory.lookup('Package.ListStatus', 'Checked')
            pack.save(flush: true)
          }

          log.debug("Set package list status to checked!")
        }
        else {
          log.debug("Skipping async job list status ..")
        }
      }
      else {
        Job update_job = concurrencyManagerService.createJob { Job j ->
          TSVIngestionService.updatePackage(pid,
                                            datafile_id,
                                            title_ns,
                                            async,
                                            false,
                                            user,
                                            preferred_group,
                                            dryRun,
                                            skipInvalid,
                                            deleteMissing,
                                            j)
        }

        if (preferred_group) {
          update_job.groupId = preferred_group
        }

        if (user) {
          update_job.ownerId = user.id
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

        if (hasOpenIssues(pid, async, result.job_result)) {
          log.info("There were issues with the automated job (valid: ${result.job_result?.validation?.valid}, reviews: ${result.job_result?.report?.reviews}${!async ? ', matching reviews: '  + result.matchingJob?.reviews : ''}), keeping listStatus in progress..")
        }
        else if (!async && !dryRun) {
          log.debug("Setting new listStatus to checked ..")

          try {
            Package.withNewSession {
              Package ptc = Package.findById(pid)
              ptc.listStatus = RefdataCategory.lookup('Package.ListStatus', 'Checked')
              ptc.save(flush: true)
            }
          }
          catch (Exception e) {
            log.error("Unable to check list status!", e)
          }

          log.debug("Set package list status to checked!")
        }
      }
    }
    else if (result.result != 'SKIPPED') {
      log.debug("Unable to reference DataFile")
      result.result = 'ERROR'
      result.messageCode = 'kbart.errors.url.unknown'
      result.message = "There were errors saving the KBART file!"
    }

    result
  }

  def fetchKbartFile(File tmp_file, URL src_url, boolean restrictSize = true) {
    def result = [content_mime_type: null, file_name: null]
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
    Long max_length = 20971520L // 1024 * 1024 * 20

    try {
      HttpRequest head_request = HttpRequest.newBuilder()
        .uri(src_url.toURI())
        .header("User-Agent", "GOKb KBART Updater")
        .method('HEAD', BodyPublishers.noBody())
        .build()

      def head_response = client.send(head_request, BodyHandlers.discarding())

      if (head_response?.statusCode() == 405) {
        log.debug("Unable to send HEAD request ..")
      }
      else if (head_response?.statusCode()) {
        HttpHeaders test_headers = head_response.headers()

        log.debug("Got HEAD result headers: ${test_headers}")

        // reject files bigger than 20 MB
        if (restrictSize && test_headers.firstValue('Content-Length').isPresent() && test_headers.firstValue('Content-Length').get() > max_length) {
          result.fileSizeError = true
          return result
        }
      }

      HttpRequest request = HttpRequest.newBuilder()
              .uri(src_url.toURI())
              .header("User-Agent", "GOKb KBART Updater")
              .build()

      HttpResponse<InputStream> response = client.send(request, BodyHandlers.ofInputStream())
      HttpHeaders headers = response.headers()

      log.debug("Got HEAD result headers: ${headers}")

      def file_name = headers.firstValue('Content-Disposition').isPresent() ? headers.firstValue('Content-Disposition').get() : null

      if (file_name) {
        file_name = file_name.split('filename=')[1]
      }

      result.content_mime_type = headers.firstValue('Content-Type').isPresent() ? headers.firstValue('Content-Type').get() : null

      if (response.statusCode() >= 400) {
        log.debug("KBART fetch status: ${response.statusCode()}")
      }
      else if (!file_name && result.content_mime_type?.startsWith('text/plain')) {
        file_name = src_url.toString().split('/')[src_url.toString().split('/').size() - 1]
      }
      else if (!file_name && result.content_mime_type?.startsWith('text/html')) {
        log.warn("Got HTML result at KBART URL ${src_url}!")
        result.accessError = true
        return result
      }

      if (file_name?.trim()) {
        file_name = file_name.replaceAll(/\"/, '')

        if ((file_name?.trim()?.endsWith('.tsv') || file_name?.trim()?.endsWith('.txt')) &&
            (result.content_mime_type?.startsWith("text/plain") ||
            result.content_mime_type?.startsWith("text/csv") ||
            result.content_mime_type?.startsWith("text/tab-separated-values") ||
            result.content_mime_type == 'application/octet-stream')) {
          log.debug("${result.content_mime_type} ${headers.map()}")
          result.file_name = file_name
          def content = restrictSize ? new BoundedInputStream(response.body(), max_length) : response.body()
          FileUtils.copyInputStreamToFile(content, tmp_file)
          log.debug("Wrote ${tmp_file.length()}")

          if (restrictSize && tmp_file.length() >= max_length) {
            result.fileSizeError = true
            tmp_file.delete()
          }
        }
        else {
          result.mimeTypeError = true
          response.body().close()
        }
      }
    }
    catch (Exception e) {
      result.connectError = true
      log.error("failed fetching file via ${src_url}", e)
    }

    result
  }

  public Boolean hasOpenIssues(pid, async, jobResult) {
    boolean result = false

    if (jobResult.validation?.valid == false || jobResult.report?.reviews > 0 || (!async && jobResult.matchingJob?.reviews > 0)) {
      result = true
    }
    else if (hasOpenTippReviews(pid)) {
      result = true
    }

    result
  }

  public Boolean hasOpenTippReviews(pid) {
    ReviewRequest.withNewSession {
      RefdataValue status_open = RefdataCategory.lookup("ReviewRequest.Status", "Open")
      RefdataValue combo_tipps = RefdataCategory.lookup("Combo.Type", "Package.Tipps")

      def qry = '''select count(*) from ReviewRequest as rr
                    where rr.componentToReview in (
                      select t from TitleInstancePackagePlatform as t
                      where exists (
                        select 1 from Combo
                        where fromComponent.id = :pid
                        and toComponent = t
                        and type = :ct
                      )
                    )
                    and rr.status = :so'''

      def total = ReviewRequest.executeQuery(qry, [pid: pid, ct: combo_tipps, so: status_open])[0]

      return total > 0
    }
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
}