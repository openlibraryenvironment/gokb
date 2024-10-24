package org.gokb

import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService.Job
import grails.gorm.transactions.Transactional

import groovy.util.logging.Slf4j
import groovy.xml.StreamingMarkupBuilder

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

import org.apache.commons.io.FileUtils
import org.gokb.DomainClassExtender
import org.gokb.cred.*
import org.springframework.util.FileCopyUtils

@Slf4j
class PackageCachingService {

  def concurrencyManagerService
  def dateFormatService
  def grailsApplication
  def packageService
  def jobManagerService
  def sessionFactory

  static boolean activeCaching = false

  def synchronized cachePackageXml(boolean force = false, Job job = null) {
    def result = null

    if (activeCaching == false) {
      log.debug("CachePackageXml started ..")
      activeCaching = true

      result = updatePackageCaches(force, job)

      activeCaching = false
    }
    else {
      log.debug("Caching already in Progress")
      result = 'ALREADY_RUNNING'
    }
    result
  }

  private def updatePackageCaches(force, job) {
    def result = 'OK'

    boolean cancelled = false

    job?.startTime = new Date()

    Package.withNewSession { session ->
      def ids = Package.executeQuery("select id from Package")

      for (id in ids) {

        cacheSinglePackage(id, force)
        session.flush()
        session.clear()

        if (Thread.currentThread().isInterrupted()) {
          log.debug("Job was cancelled..")
          cancelled = true
          result = 'CANCELLED'
          break
        }
      }
    }
    job?.endTime = new Date()

    result
  }

  public String cacheSinglePackage(id, boolean force = false, Job job = null) {
    String result = 'OK'
    File tempDir = new File(grailsApplication.config.getProperty('gokb.baseTempDirectory'))
    File dir = new File(grailsApplication.config.getProperty('gokb.packageXmlCacheDirectory'))
    Package item = Package.get(id)
    def session = sessionFactory.currentSession
    def activeComponentJobs = concurrencyManagerService.getComponentJobs(id)
    def activeScheduledJobs = jobManagerService.runningJobs?.findAll { it.jobDetail.key.name == 'org.gokb.AutoCachePackagesJob'} ?: null
    boolean cancelled = false

    if (item && (!force || !activeScheduledJobs) && activeComponentJobs?.data?.size() == 0) {
      try {
        if (!dir.exists()) {
          dir.mkdirs()
        }

        if (!tempDir.exists()) {
          tempDir.mkdirs()
        }

        def identifier_prefix = "uri://gokb/${grailsApplication.config.getProperty('sysid')}/title/"

        def fileName = "${item.uuid}_${dateFormatService.formatIsoMsTimestamp(item.lastUpdated)}.xml"
        File cachedRecord = new File("${dir}/${fileName}")
        def currentCacheFile = null
        Date currentCacheDate

        for (File file : dir.listFiles()) {
          if (file.name.contains(item.uuid)) {
            def datepart = file.name.split('_')[1]
            currentCacheFile = file
            currentCacheDate = dateFormatService.parseIsoMsTimestamp(datepart.substring(0, datepart.length() - 4))
          }
        }

        if (force || (Duration.between(item.lastUpdated.toInstant(), Instant.now()).getSeconds() > 30 && (!currentCacheFile || item.lastUpdated > currentCacheDate))) {
          File tmpFile = new File("${tempDir}/${fileName}.tmp")

          if (tmpFile.exists()) {
            tmpFile.delete()
          }

          BufferedWriter fileWriter = new BufferedWriter(new FileWriter(tmpFile, true))

          RefdataValue refdata_package_tipps = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
          RefdataValue refdata_hosted_tipps = RefdataCategory.lookup('Combo.Type', 'Platform.HostedTipps')
          RefdataValue refdata_ti_tipps = RefdataCategory.lookup('Combo.Type', 'TitleInstance.Tipps')
          RefdataValue refdata_ids = RefdataCategory.lookup('Combo.Type', 'KBComponent.Ids')
          RefdataValue refdata_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
          RefdataValue status_active = DomainClassExtender.comboStatusActive
          String tipp_hql = "from TitleInstancePackagePlatform as tipp where exists (select 1 from Combo where fromComponent = :pkg and toComponent = tipp and type = :ctype)"
          def tipp_hql_params = [pkg: item, ctype: refdata_package_tipps]
          Integer tipps_count = (item.status != refdata_deleted) ? TitleInstancePackagePlatform.executeQuery("select count(tipp.id) " + tipp_hql, tipp_hql_params, [readOnly: true])[0] : 0
          def pkg_ids = item.activeIdInfo
          String cName = item.class.name

          log.info("Starting package caching for ${item.name} with ${tipps_count} TIPPs..")

          fileWriter << new StreamingMarkupBuilder().bind {
            mkp.declareNamespace(xsd:'http://www.w3.org/2001/XMLSchema')
            'gokb'('xmlns:gokb': 'http://gokb.org/oai_metadata/') {
              'package'('id': (item.id), 'uuid': (item.uuid)) {

                // Single props.
                'name'(item.name)
                'status'(item.status?.value)
                'editStatus'(item.editStatus?.value)
                'language'(item.language?.value)
                'lastUpdated'(item.lastUpdated ? dateFormatService.formatIsoTimestamp(item.lastUpdated) : null)
                'descriptionURL'(item.descriptionURL)
                'description'(item.description)
                'shortcode'(item.shortcode)

                // Identifiers
                'identifiers' {
                  pkg_ids?.each { tid ->
                    'identifier'(tid)
                  }
                }

                // Variant Names
                'variantNames' {
                  item.variantNames.each { vn ->
                    'variantName'(vn.variantName)
                  }
                }

                // Subjects
                'subjects' {
                  item.activeSubjectsInfo.each { asi ->
                    'subject'(asi)
                  }
                }

                'scope'(item.scope?.value)
                'listStatus'(item.listStatus?.value)
                'breakable'(item.breakable?.value)
                'consistent'(item.consistent?.value)
                'fixed'(item.fixed?.value)
                'paymentType'(item.paymentType?.value)
                'global'(item.global?.value)
                'globalNote'(item.globalNote)
                'contentType'(item.contentType?.value)

                if (item.nominalPlatform) {
                  'nominalPlatform'(id: item.nominalPlatform.id, uuid: item.nominalPlatform.uuid) {
                    'primaryUrl'(item.nominalPlatform.primaryUrl)
                    'name'(item.nominalPlatform.name)
                  }
                }

                if (item.provider) {
                  'nominalProvider'(id: item.provider.id, uuid: item.provider.uuid) {
                    'name'(item.provider.name)
                  }
                }

                'listVerifiedDate'(item.listVerifiedDate ? dateFormatService.formatIsoTimestamp(item.listVerifiedDate) : null)

                'curatoryGroups' {
                  item.curatoryGroups.each { cg ->
                    'group' {
                      'name'(cg.name)
                    }
                  }
                }

                if (item.source) {
                  'source' {
                    'name'(item.source.name)
                    'url'(item.source.url)
                    'defaultAccessURL'(item.source.defaultAccessURL)
                    'explanationAtSource'(item.source.explanationAtSource)
                    'contextualNotes'(item.source.contextualNotes)
                    'frequency'(item.source.frequency?.value)
                  }
                }

                'dateCreated'(dateFormatService.formatIsoTimestamp(item.dateCreated))
                'TIPPs'(count: tipps_count) {
                  int offset = 0

                  while (offset < tipps_count) {
                    log.debug("Fetching TIPPs batch ${offset}/${tipps_count}")
                    def tipps = TitleInstancePackagePlatform.executeQuery(tipp_hql + " order by tipp.id", tipp_hql_params, [readOnly: true, max: 50, offset: offset])
                    log.debug("fetch complete ..")

                    offset += 50

                    tipps.each { tipp ->
                      'TIPP'(['id': tipp.id, 'uuid': tipp.uuid]) {
                        'status'(tipp.status?.value)
                        'name'(tipp.name)
                        'lastUpdated'(tipp.lastUpdated ? dateFormatService.formatIsoTimestamp(tipp.lastUpdated) : null)
                        'series'(tipp.series)
                        'subjectArea'(tipp.subjectArea)
                        'publisherName'(tipp.publisherName)
                        'dateFirstInPrint'(tipp.dateFirstInPrint ? dateFormatService.formatDate(tipp.dateFirstInPrint) : null)
                        'dateFirstOnline'(tipp.dateFirstOnline ? dateFormatService.formatDate(tipp.dateFirstOnline) : null)
                        'medium'(tipp.format?.value)
                        'format'(tipp.medium?.value)
                        'volumeNumber'(tipp.volumeNumber)
                        'editionStatement'(tipp.editionStatement)
                        'firstAuthor'(tipp.firstAuthor)
                        'firstEditor'(tipp.firstEditor)
                        'parentPublicationTitleId'(tipp.parentPublicationTitleId)
                        'precedingPublicationTitleId'(tipp.precedingPublicationTitleId)
                        'lastChangedExternal'(tipp.lastChangedExternal ? dateFormatService.formatDate(tipp.lastChangedExternal) : null)
                        'publicationType'(tipp.publicationType?.value)

                        if (tipp.title) {
                          def ti_obj = KBComponent.deproxy(tipp.title)
                          'title'('id': ti_obj.id, 'uuid': ti_obj.uuid) {
                            'name'(ti_obj.name?.trim())
                            'type'(getTitleClass(ti_obj.id))
                            'status'(ti_obj.status?.value)
                            if (getTitleClass(ti_obj.id) == 'BookInstance') {
                              'dateFirstInPrint'(ti_obj.dateFirstInPrint ? dateFormatService.formatDate(ti_obj.dateFirstInPrint) : null)
                              'dateFirstOnline'(ti_obj.dateFirstOnline ? dateFormatService.formatDate(ti_obj.dateFirstOnline) : null)
                              'editionStatement'(ti_obj.editionStatement)
                              'volumeNumber'(ti_obj.volumeNumber)
                              'firstAuthor'(ti_obj.firstAuthor)
                              'firstEditor'(ti_obj.firstEditor)
                            }
                            'identifiers' {
                              ti_obj.activeIdInfo.each { tid ->
                                'identifier'(tid)
                              }
                            }
                            'subjects' {
                              ti_obj.activeSubjectsInfo.each { asi ->
                                'subject'(asi)
                              }
                            }
                          }
                        }
                        else {
                          'title'()
                        }
                        'identifiers' {
                          tipp.activeIdInfo.each { tid ->
                            'identifier'(tid)
                          }
                        }
                        'platform'(id: tipp.hostPlatform.id, 'uuid': tipp.hostPlatform.uuid) {
                          'primaryUrl'(tipp.hostPlatform.primaryUrl?.trim())
                          'name'(tipp.hostPlatform.name?.trim())
                        }
                        'access'(
                          start: tipp.accessStartDate ? dateFormatService.formatDate(tipp.accessStartDate) : null,
                          end: tipp.accessEndDate ? dateFormatService.formatDate(tipp.accessEndDate) : null
                        )
                        def cov_statements = getCoverageStatements(tipp.id)

                        if (cov_statements?.size() > 0) {
                          cov_statements.each { tcs ->
                            'coverage'(
                              startDate: (tcs.startDate ? dateFormatService.formatDate(tcs.startDate) : null),
                              startVolume: (tcs.startVolume),
                              startIssue: (tcs.startIssue),
                              endDate: (tcs.endDate ? dateFormatService.formatDate(tcs.endDate) : null),
                              endVolume: (tcs.endVolume),
                              endIssue: (tcs.endIssue),
                              coverageDepth: (tcs.coverageDepth?.value ?: null),
                              coverageNote: (tcs.coverageNote),
                              embargo: (tcs.embargo)
                            )
                          }
                        }

                        'subjects' {
                          tipp.activeSubjectsInfo.each { asi ->
                            'subject'(asi)
                          }
                        }

                        'url'(tipp.url ?: "")
                      }
                    }
                    session.flush()
                    session.clear()

                    if (Thread.currentThread().isInterrupted()) {
                      cancelled = true
                      break
                    }

                    log.debug("Batch complete ..")
                  }
                }
              }
            }
          }
          log.info("Finished processing ${tipps_count} TIPPs ..")
          fileWriter.close()

          if (!cancelled) {
            def removal = removeCacheEntriesForItem(item.uuid)

            if (removal) {
              log.debug("Removed stale cache files ..")
            }

            FileUtils.moveFile(tmpFile, cachedRecord)

            if (!force || !currentCacheFile || item.lastUpdated > currentCacheDate) {
              Package.executeUpdate("update Package p set p.lastCachedDate = :lcd where p.id = :pid", [lcd: new Date(cachedRecord.lastModified()), pid: item.id])
            }

            log.info("Caching KBART ..")
            packageService.updateKbartExport(item)
            log.info("Finished caching KBART file")
          }
          else {
            result = 'CANCELLED'
          }
        }
        else if (currentCacheFile && item.lastUpdated <= currentCacheDate) {
          result = 'SKIPPED_NO_CHANGE'
        }
        else if (Duration.between(item.lastUpdated.toInstant(), Instant.now()).getSeconds() <= 30) {
          result = 'SKIPPED_CURRENTLY_CHANGING'
        }
        else {
          result = 'SKIPPED_DEFAULT'
        }
      }
      catch (Exception e) {
        log.error("Exception in Package Caching for ID ${id}!", e)
      }
    }
    else if (!item) {
      result = 'ERROR'
      log.debug("Unable to reference package by id!")
    }
    else if (activeScheduledJobs) {
      result = 'SKIPPED_ACTIVE_JOB'
    }
     else {
      result = 'SKIPPED_CURRENTLY_CHANGING'
    }

    result
  }

  private def getTitleClass(Long title_id) {
    def result = KBComponent.get(title_id)?.class.getSimpleName()

    result
  }

  private def getCoverageStatements(Long tipp_id) {
    def result = TIPPCoverageStatement.executeQuery("from TIPPCoverageStatement as tcs where tcs.owner.id = :tipp", ['tipp': tipp_id], [readOnly: true])
    result
  }

  private boolean removeCacheEntriesForItem(uuid) {
    boolean result = true
    File dir = new File(grailsApplication.config.getProperty('gokb.packageXmlCacheDirectory'))
    File[] files = dir.listFiles()

    for (def file : files) {
      if (file.name.contains(uuid)) {
        result &= file.delete()
      }
    }

    result
  }
}