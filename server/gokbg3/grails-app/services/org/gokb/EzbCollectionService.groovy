package org.gokb

import groovy.util.logging.Slf4j
import com.k_int.ConcurrencyManagerService.Job

@Slf4j
class EzbCollectionService {

  def concurrencyManagerService
  def packageService

  def startUpdate(Job job) {
    if (!job) {
      job = concurrencyManagerService.startOrQueue { ljob ->
        fetchUpdatedLists(ljob)
      }
    } else {
      fetchUpdatedLists(job)
    }
  }

  def fetchUpdatedLists (job) {

  }
}