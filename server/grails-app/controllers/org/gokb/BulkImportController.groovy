package org.gokb

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.annotation.Secured

import org.gokb.cred.BulkImportListConfig
import org.gokb.cred.User
import org.grails.web.json.JSONObject

import groovy.util.logging.*

@Slf4j
class BulkImportController {
  def springSecurityService
  def bulkPackageImportService

  def assertBulkConfig () {
    Map result = [result: 'OK']
    JSONObject rjson = request.JSON

    if (rjson && BulkImportListConfig.isTypeEditable()) {
      def upsertResult = bulkPackageImportService.upsertConfig(rjson, springSecurityService.currentUser)

      if (upsertResult.result == 'ERROR') {
        result.result = 'ERROR'
        result.errors = upsertResult.errors
        result.message = "There have been issues with the import config!"
        response.status = 400
      }
      else {
        result.message = 'Successfully asserted bulk import config!'
      }
    }
    else if (!rjson) {
      result.result = 'ERROR'
      result.message = 'Unable to parse request JSON body!'
      response.status = 400
    } else {
      result.result = 'ERROR'
      result.message = 'Insufficient permissions to edit bulk configs!'
      reponse.status = 403
    }

    render result as JSON
  }

  def runBulkUpdate() {
    Map result = [result: 'OK']
    User user = springSecurityService.currentUser
    Boolean dryRun = params.boolean('dryRun') ?: false
    Boolean async = params.boolean('async') ?: false
    BulkImportListConfig config = BulkImportListConfig.findByCode(params.code)

    if (config && (user.superUserStatus || user == config.owner)) {
      log.debug("Trigger bulk update (${config?.code} - Dry Run: ${dryRun} - Async: ${async})")
      result = bulkPackageImportService.startUpdate(config, dryRun, async, springSecurityService.currentUser)
    }
    else if (!config) {
      log.debug("Unable to reference config with code '${params.code}'!")
      result.result = 'ERROR'
      response.status = 404
      result.message = "Unable to reference config with code '${params.code}'!"
    }
    else {
      log.debug("No permission to edit this config!")
      result.result = 'ERROR'
      response.status = 403
      result.message = "No permission to edit this config!"
    }

    render result as JSON
  }
}
