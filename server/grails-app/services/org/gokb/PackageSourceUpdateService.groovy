package org.gokb

import com.k_int.ConcurrencyManagerService.Job

import grails.gorm.transactions.*

import groovy.util.logging.Slf4j

import java.net.http.*
import java.net.http.HttpResponse.BodyHandlers
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.regex.Pattern

import org.gokb.cred.*
import org.apache.commons.io.FileUtils
import org.mozilla.universalchardet.UniversalDetector

@Slf4j
class PackageSourceUpdateService {
  def concurrencyManagerService
  def TSVIngestionService

  static Pattern DATE_PLACEHOLDER_PATTERN = ~/[0-9]{4}-[0-9]{2}-[0-9]{2}/
  static Pattern FIXED_DATE_ENDING_PLACEHOLDER_PATTERN = ~/\{YYYY-MM-DD\}\.(tsv|txt)$/
  static Pattern VARIABLE_DATE_ENDING_PLACEHOLDER_PATTERN = ~/([12][0-9]{3}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01]))\.(tsv|txt)$/

  @javax.annotation.PostConstruct
  def init() {
    log.info("Initialising source update service...")
  }

  def updateFromSource(Long pkgId, def user = null, Job job = null, Long activeGroupId = null, boolean dryRun = false) {
    log.debug("updateFromSource ${p.name}")
    def result = [result: 'OK']
    def activeJobs = concurrencyManagerService.getComponentJobs(pkgId)

    if (job || activeJobs?.data?.size() == 0) {
      log.debug("UpdateFromSource started")
      result = startSourceUpdate(pkgId, user, job, activeGroupId, dryRun)

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

  private def startSourceUpdate(pid, user, job, activeGroupId, dryRun) {
    log.debug("Source update start..")
    def result = [result: 'OK']
    def platform_url
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
      platform_url = pkg_plt.primaryUrl
      Source pkg_source = p.source
      preferred_group = activeGroupId ?: (p.curatoryGroups?.size() > 0 ? p.curatoryGroups[0].id : null)
      title_ns = pkg_source?.targetNamespace?.id ?: (pkg_prov?.titleNamespace?.id ?: null)

      if (job && !job.startTime) {
        job.startTime = new Date()
      }

      if (pkg_source?.url) {
        def src_url = null
        def dynamic_date = false
        LocalDate extracted_date
        skipInvalid = pkg_source.skipInvalid ?: false
        def file_info = [:]

        try {
          src_url = new URL(pkg_source.url)
        }
        catch (Exception e) {
          log.debug("Invalid source URL!")
          result.result = 'ERROR'
          result.messageCode = 'kbart.errors.url.invalid'
          result.message = "Provided URL is not valid!"

          return result
        }

        if (src_url) {
          def existing_string = src_url.toString()
          String local_date_string = LocalDate.now().toString()

          if (existing_string =~ FIXED_DATE_ENDING_PLACEHOLDER_PATTERN) {
            log.debug("URL contains date placeholder ..")
            src_url = new URL(existing_string.replace('{YYYY-MM-DD}', local_date_string))
            dynamic_date = true
          }
          else {
            def date_pattern_match = (existing_string =~ VARIABLE_DATE_ENDING_PLACEHOLDER_PATTERN)

            if (date_pattern_match && date_pattern_match[0].size() > 0) {
              String matched_date_string = date_pattern_match[0][1]
              log.debug("${matched_date_string}")
              extracted_date = LocalDate.parse(matched_date_string)
            }
          }
        }
        else {
          log.debug("No source URL!")
          result.result = 'ERROR'
          result.messageCode = 'kbart.errors.url.missing'
          result.message = "Package source does not have an URL!"

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
            file_info = fetchKbartFile(tmp_file, src_url)
          }

          if (file_info.accessError) {
            result.result = 'ERROR'
            result.messageCode = 'kbart.errors.url.html'
            result.message = "URL returned HTML, indicating provider configuration issues!"

            return result
          } else if (file_info.status == 403) {
            log.debug("URL request failed!")
            result.result = 'ERROR'
            result.messageCode = 'kbart.errors.url.denied'
            result.message = "URL request returned 403 ACCESS DENIED, skipping further tries!"

            return result
          }

          if (!file_info.file_name && (dynamic_date || extracted_date)) {
            LocalDate active_date = LocalDate.now()
            src_url = new URL(src_url.toString().replaceFirst(DATE_PLACEHOLDER_PATTERN, active_date.toString()))
            log.debug("Fetching dated URL for today..")
            file_info = fetchKbartFile(tmp_file, src_url)

            // Look at first of this month
            if (!file_info.file_name) {
              sleep(500)
              log.debug("Fetching first of the month..")
              def som_date_url = new URL(src_url.toString().replaceFirst(DATE_PLACEHOLDER_PATTERN, active_date.withDayOfMonth(1).toString()))
              file_info = fetchKbartFile(tmp_file, som_date_url)
            }

            // Check all days of this month
            while (active_date.isAfter(LocalDate.now().minusDays(30)) && !file_info.file_name) {
              active_date = active_date.minusDays(1)
              src_url = new URL(src_url.toString().replaceFirst(DATE_PLACEHOLDER_PATTERN, active_date.toString()))
              log.debug("Fetching dated URL for date ${active_date}")
              sleep(500)
              file_info = fetchKbartFile(tmp_file, src_url)
            }
          }

          log.debug("Got mime type ${file_info.content_mime_type} for file ${file_info.file_name}")

          if (file_info.file_name) {
            if ((file_info.file_name?.endsWith('.tsv') || file_info.file_name?.endsWith('.txt')) &&
                (file_info.content_mime_type?.startsWith("text/plain") ||
                file_info.content_mime_type?.startsWith("text/csv") ||
                file_info.content_mime_type?.startsWith("text/tab-separated-values") ||
                file_info.content_mime_type == 'application/octet-stream')
            ) {
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

                  return result
                }
              } catch (IOException e) {
                  // handle exception
                 log.error("Failed DataFile handling", e)
              }
            }
            else {
              result.result = 'ERROR'
              result.messageCode = 'kbart.errors.url.mimeType'
              result.message = "KBART URL returned a wrong content type!"
              log.error("KBART url ${src_url} returned MIME type ${file_info.content_mime_type} for file ${file_info.file_name}")

              return result
            }
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
          log.info("There were issues with the automated job (valid: ${result.validation?.valid}, reviews: ${result.report?.reviews}, matching reviews: ${result.matchingJob?.reviews}), keeping listStatus in progress..")
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
          log.info("There were issues with the automated job (valid: ${result.job_result?.validation?.valid}, reviews: ${result.job_result?.report?.reviews}, matching reviews: ${result.job_result?.matchingJob?.reviews}), keeping listStatus in progress..")
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

  def fetchKbartFile(File tmp_file, URL src_url) {
    def result = [content_mime_type: null, file_name: null]
    HttpClient client = HttpClient.newBuilder().build()
    HttpRequest request = HttpRequest.newBuilder()
      .uri(src_url.toURI())
      .header("User-Agent", "GOKb KBART Updater")
      .build()

    HttpResponse<InputStream> response = client.send(request, BodyHandlers.ofInputStream())
    HttpHeaders headers = response.headers()
    def file_name = headers.firstValue('Content-Disposition').isPresent() ? headers.firstValue('Content-Disposition').get() : null

    if (file_name) {
      file_name = file_name.split('filename=')[1]
    }

    result.content_mime_type = headers.firstValue('Content-Type').isPresent() ? headers.firstValue('Content-Type').get() : null

    if (response.statusCode() >= 400) {
      log.warn("KBART fetch status: ${result.status}")
    }
    else if (!file_name && result.content_mime_type == 'text/plain') {
      file_name = src_url.toString().split('/')[src_url.toString().split('/').size() - 1]
    }
    else if (!file_name && result.content_mime_type.startsWith('text/html')) {
      log.warn("Got HTML result at KBART URL ${src_url}!")
      result.accessError = true
    }

    if (file_name?.trim()) {
      log.debug("${result.content_mime_type} ${headers.map()}")
      result.file_name = file_name.replaceAll(/\"/, '')
      InputStream content = response.body()
      FileUtils.copyInputStreamToFile(content, tmp_file)
      content.close()
      log.debug("Wrote ${tmp_file.length()}")
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