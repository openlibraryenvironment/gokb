import com.k_int.ConcurrencyManagerService.Job

import groovyx.net.http.*

import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId

import org.gokb.cred.*
import org.apache.commons.io.FileUtils
import org.mozilla.universalchardet.UniversalDetector

class PackageSourceUpdateService {
  def concurrencyManagerService
  def TSVIngestionService

  def updateFromSource(Package p, def user = null, Job job = null, CuratoryGroup activeGroup = null, boolean dryRun = false, boolean skipInvalid = false) {
    log.debug("updateFromSource ${p.name}")
    def result = [result: 'OK']
    def activeJobs = concurrencyManagerService.getComponentJobs(p.id)

    if (job || activeJobs?.data?.size() == 0) {
      log.debug("UpdateFromSource started")
      result = startSourceUpdate(p, user, job, activeGroup, dryRun, skipInvalid)
    }
    else {
      log.error("update skipped - already running")
      result.result = 'ALREADY_RUNNING'
    }
    result
  }

  private def startSourceUpdate(pkg, user, job, activeGroup, dryRun, skipInvalid) {
    log.debug("Source update start..")
    def result = [result: 'OK']

    Package.withNewSession {
      Package p = Package.get(pkg.id)
      DataFile datafile = null
      def platform_url = p.nominalPlatform.primaryUrl
      def pkg_source = p.source
      def preferred_group = activeGroup ?: (p.curatoryGroups?.size() > 0 ? CuratoryGroup.deproxy(p.curatoryGroups[0]) : null)
      def title_ns = pkg_source?.targetNamespace ?: (p.provider?.titleNamespace ?: null)

      if (job && !job.startTime) {
        job.startTime = new Date()
      }

      if (pkg_source?.url) {
        def src_url = null
        def dynamic_date = false
        LocalDate extracted_date
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

          if (existing_string =~ /\{YYYY-MM-DD\}\.(tsv|txt)$/) {
            log.debug("URL contains date placeholder ..")
            src_url = new URL(existing_string.replace('{YYYY-MM-DD}', local_date_string))
            dynamic_date = true
          }
          else {
            def date_pattern_match = (existing_string =~ /([12][0-9]{3}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01]))\.(tsv|txt)$/)

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
          File tmp_file = TSVIngestionService.createTempFile(deposit_token)
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
            src_url = new URL(src_url.toString().replaceFirst(/[0-9]{4}-[0-9]{2}-[0-9]{2}/, active_date.toString()))
            log.debug("Fetching dated URL for today..")
            file_info = fetchKbartFile(tmp_file, src_url)

            // Look at first of this month
            if (!file_info.file_name) {
              sleep(500)
              log.debug("Fetching first of the month..")
              def som_date_url = new URL(src_url.toString().replaceFirst(/[0-9]{4}-[0-9]{2}-[0-9]{2}/, active_date.withDayOfMonth(1).toString()))
              file_info = fetchKbartFile(tmp_file, som_date_url)
            }

            // Check all days of this month
            while (active_date.isAfter(LocalDate.now().minusDays(30)) && !file_info.file_name) {
              active_date = active_date.minusDays(1)
              src_url = new URL(src_url.toString().replaceFirst(/[0-9]{4}-[0-9]{2}-[0-9]{2}/, active_date.toString()))
              log.debug("Fetching dated URL for date ${active_date}")
              sleep(500)
              file_info = fetchKbartFile(tmp_file, src_url)
            }
          }

          log.debug("Got mime type ${file_info.content_mime_type} for file ${file_info.file_name}")

          if (file_info.file_name) {
            if ((file_info.file_name?.endsWith('.tsv') || file_info.file_name?.endsWith('.txt')) &&
                (file_info.content_mime_type?.startsWith("text/plain") ||
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
                  datafile = DataFile.findByMd5(file_info.md5sumHex)

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
                  }
                  else {
                    log.debug("Found existing datafile ${datafile}")

                    Boolean noChange = hasFileChanged(p.id, datafile.id)

                    if (noChange) {
                      log.debug("Datafile was already the last import for this package!")
                      result.result = 'SKIPPED'
                      result.message = 'Skipped repeated import of the same file for this package.'
                      result.messageCode = 'kbart.transmission.skipped.sameFile'
                      return result
                    }
                    else {
                      p.fileAttachments.add(datafile)
                      p.save(flush: true)
                    }
                  }
                }
                else {
                  log.debug("Illegal charset ${encoding} found..")
                  result.result = 'ERROR'
                  result.messageCode = 'kbart.errors.url.charset'
                  result.message = "KBART is not UTF-8!"
                  return result
                }
              } catch (IOException e) {
                  // handle exception
                  e.printStackTrace()
              }

              if (datafile) {
                if (job) {
                  result = TSVIngestionService.updatePackage(p,
                                                             datafile,
                                                             title_ns,
                                                             (user ? true : false),
                                                             false,
                                                             user,
                                                             preferred_group,
                                                             dryRun,
                                                             skipInvalid,
                                                             job)
                }
                else {
                  Job update_job = concurrencyManagerService.createJob { Job j ->
                    TSVIngestionService.updatePackage(p,
                                                      datafile,
                                                      title_ns,
                                                      (user ? true : false),
                                                      false,
                                                      user,
                                                      preferred_group,
                                                      dryRun,
                                                      skipInvalid,
                                                      j)
                  }

                  if (preferred_group) {
                    update_job.groupId = preferred_group.id
                  }

                  if (user) {
                    update_job.ownerId = user.id
                  }

                  update_job.description = "KBART REST ingest (${p.name})".toString()
                  update_job.type = RefdataCategory.lookup('Job.Type', 'KBARTIngest')
                  update_job.linkedItem = [name: p.name, type: "Package", id: p.id, uuid: p.uuid]
                  update_job.message("Starting upsert for Package ${p.name}".toString())
                  update_job.startOrQueue()
                  result.job_result = update_job.get()

                  if (result.job_result?.badrows || result.job_result?.report?.reviews > 0 || result.job_result?.matchingJob?.reviews > 0) {
                    log.debug("There were issues with the automated job, keeping listStatus in progress..")
                  }
                  else {
                    p.listStatus = RefdataCategory.lookup('Package.ListStatus', 'Checked')
                    p.save(flush: true)
                  }
                }
              }
              else {
                log.debug("Unable to reference DataFile")
                result.result = 'ERROR'
                result.messageCode = 'kbart.errors.url.unknown'
                result.message = "There were errors saving the KBART file!"
              }
            }
            else {
              result.result = 'ERROR'
              result.messageCode = 'kbart.errors.url.mimeType'
              result.message = "KBART URL returned a wrong content type!"
              log.debug("KBART url ${src_url} returned MIME type ${file_info.content_mime_type}")
            }
          }
          else {
            result.message = "No KBART found for provided URL!"
            result.messageCode = 'kbart.transmission.skipped.noFile'
            result.result = 'SKIPPED'
            log.debug("KBART url ${src_url} returned MIME type ${file_info.content_mime_type}")
          }
        }
        // else if (src_url.getProtocol() in ['ftp', 'sftp']) {
        else {
          result.result = 'ERROR'
          result.messageCode = 'kbart.errors.url.protocol'
          result.message = "KBART URL has an unsupported protocol!"
        }
      }
    }

    result
  }

  def fetchKbartFile(File tmp_file, URL src_url) {
    def result = [content_mime_type: null, file_name: null]
    HttpURLConnection connection

    try {
      connection = (HttpURLConnection) src_url.openConnection()
      connection.addRequestProperty("User-Agent", "GOKb KBART Update")
      connection.setInstanceFollowRedirects(true)
    }
    catch (IOException e) {
        e.printStackTrace()
    }
    connection.connect()
    result.status = connection.getResponseCode()
    log.debug("Fetching ${src_url}")
    log.debug("HEADERS: ${connection.getHeaderFields()}")

    if (result.status == HttpURLConnection.HTTP_OK) {
      result.content_mime_type = connection.getContentType()

      if (result.content_mime_type?.startsWith('text/html')) {
        log.warn("Got HTML result at KBART URL ${src_url}!")
        result.accessError = true
      }
      else {
        log.debug("${result.content_mime_type} ${connection.getHeaderFields()}")
        def file_name = connection.getHeaderField("Content-Disposition")

        if (file_name) {
          file_name = file_name.split('filename=')[1]
        } else if (result.content_mime_type == 'text/plain') (
          file_name = src_url.toString().split('/')[src_url.toString().split('/').size() - 1]
        )

        if (file_name?.trim()) {
          result.file_name = file_name.replaceAll(/\"/, '')
          InputStream content = connection.getInputStream()
          FileUtils.copyInputStreamToFile(content, tmp_file)
          content.close()
          log.debug("Wrote ${tmp_file.length()}")
        }
      }
    }
    else if (result.status != HttpURLConnection.HTTP_NOT_FOUND) {
      log.debug("KBART fetch status: ${result.status}")
      log.debug("${connection.getContent()}")
    }
    else {
      log.debug("KBART fetch status: ${result.status}")
    }

    result
  }

  public Boolean hasFileChanged(pkgId, datafileId) {
    RefdataValue type_fa = RefdataCategory.lookup('Combo.Type', 'KBComponent.FileAttachments')
    def ordered_combos = Combo.executeQuery('''select c.toComponent.id from Combo as c
                                              where c.type = :ct
                                              and c.fromComponent.id = :pkg
                                              order by c.dateCreated desc''', [ct: type_fa, pkg: pkgId])

    return (ordered_combos.size() > 0 && ordered_combos[0] == datafileId)
  }
}