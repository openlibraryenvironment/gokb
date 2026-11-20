package org.gokb

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.annotation.Secured
import groovy.json.JsonSlurper
import org.springframework.web.servlet.support.RequestContextUtils
import org.gokb.cred.*
import com.opencsv.*
import com.k_int.ClassUtils
import com.k_int.ConcurrencyManagerService.Job
import java.util.concurrent.CancellationException
import org.grails.web.json.JSONObject
import org.grails.web.json.JSONArray

import groovy.util.logging.*

@Slf4j
class IntegrationController {

  def springSecurityService
  def concurrencyManagerService
  def componentUpdateService
  def sessionFactory
  def platformService
  def titleUpsertService
  def orgService

  @Secured(value = ["hasRole('ROLE_API')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def index() {
  }

  @Transactional
  @Secured(value = ["hasRole('ROLE_ADMIN')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def assertGroup() {
    def result = [result: 'OK']
    def name = request.JSON.name
    def normname = CuratoryGroup.generateNormname(name)
    def user = springSecurityService.currentUser
    def uuid = request.JSON.uuid
    def group = uuid ? CuratoryGroup.findByUuid(uuid) : null
    def status = request.JSON.status

    if (!group) {
      group = CuratoryGroup.findByNormname(normname)
    }
    else {
      result.message = "Group ${group.name} matched by uuid!"
    }

    if (!group) {
      group = new CuratoryGroup(name: name, uuid: uuid)

      if (group.validate()) {
        group.save(flush: true)
        result.message = "Created new group ${name}!"
      }
      else {
        result.message = "Could not reference group ${name}"
        result.errors = group.errors
        result.result = 'ERROR'

        render result as JSON
        return
      }
    }
    else {
      result.message = "Group ${group.name} matched by name!"
    }

    // Defaults first.
    componentUpdateService.ensureCoreData(group, request.JSON, false, user)

    // Find by username but do not create missing entries.
    def owner = request.JSON.owner
    if (owner) {
      group.owner = User.findByUsername(owner)
    }

    // Need to add all users to the group.
    def memberNames = request.JSON.users
    if (memberNames) {
      def members = User.createCriteria().list {
        'in'('username', memberNames)
      }

      members.each {
        group.addToUsers(it)
      }
    }

    if (group) {
      group.save(flush: true)
      result.groupId = group.id
    }
    else {
      result.message = "Could not reference group ${name}"
      result.result = 'ERROR'
    }

    render result as JSON
  }

  @Secured(value = ["hasRole('ROLE_API')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def assertJsonldOrg() {
    // log.debug("assertOrg, request.json = ${request.JSON}");
    def result = [:]
    def user = springSecurityService.currentUser
    result.status = true;

    try {

      def name = request.JSON.'skos:prefLabel'

      if ((name != null) && (name.trim().length() > 0)) {

        log.debug("Trying to locate component with ID ${request.JSON.'@id'} name is \"${name}\"");

        // Try and match on primary ID
        def located_entries = KBComponent.lookupByIdentifierValue([request.JSON.'@id'.toString()] as String[]);

        if (located_entries?.size() == 1) {
          log.debug("Identified record..");
          enrichJsonLDOrg(located_entries[0], request.JSON)
        }
        else if (located_entries?.size() == 0) {

          log.debug("Not identified - try sameAs relations");

          if (request.JSON.'owl:sameAs' != null) {
            log.debug("Attempt lookup by sameAs : ${request.JSON.'owl:sameAs' as String[]} ");
            located_entries = KBComponent.lookupByIdentifierValue((request.JSON.'owl:sameAs') as String[])
          }
          else {
            log.debug("No owl:sameAs entries found");
          }

          if (located_entries?.size() == 0) {
            log.debug("Failed to match on same-as. Attempting primary name match");
            def normname = GOKbTextUtils.norm2(name)
            located_entries = KBComponent.findAllByNormname(normname)
            if (located_entries?.size() == 0) {
              log.debug("No match on normalised name ${normname}.. Trying variant names");
              def variant_normname = GOKbTextUtils.normaliseString(name)
              def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
              located_entries = Org.executeQuery("select distinct o from Org as o join o.variantNames as v where v.normVariantName = :nn and o.status <> :sd", [nn: variant_normname, sd: status_deleted])

              if (located_entries?.size() == 0) {

                createJsonLDOrg(request.JSON);
              }
              else if (located_entries?.size() == 1) {
                log.debug("Exact match on normalised variantname ${variant_normname} - good enough");
                enrichJsonLDOrg(located_entries[0], request.JSON)
              }
              else {
                log.error("Multiple matches on normalised variant name... abandon all hope");
              }
            }
            else if (located_entries?.size() == 1) {
              log.debug("Exact match on normalised name ${normname} - good enough");
              enrichJsonLDOrg(located_entries[0], request.JSON)
            }
            else {
              log.error("Multiple matches on normalised name... abandon all hope");
            }

          }
          else if (located_entries?.size() == 1) {
            log.debug("Located identifier");
          }
          else {
            log.error("set of SameAs identifiers locate more that one component");
          }
        }
        else {
          log.error("Unique identifier finds multiple components.");
        }

        result.status = 'OK'
      }
      else {
        log.error("skipping org [ ${request.JSON.'@id'}] due to null name");
      }
    }
    catch (Exception e) {
      log.error("Problem", e)
      result.status = 'ERROR'
    }
    finally {
    }

    render result as JSON
  }

  /**
   *  assertOrg()
   *  allow an authorized external component to send in a JSON structure following this template:
   *      [
   *         name:National Association of Corrosion Engineers,
   *         description:National Association of Corrosion Engineers,
   *         parent:
   *         customIdentifiers:[[identifierType:"idtype", identifierValue:"value"]],
   *         combos:[[linkTo:[identifierType:"ncsu-internal", identifierValue:"ncsu:61929"], linkType:"HasParent"]],
   *         flags:[[flagType:"Org Role", flagValue:"Content Provider"],
   *                [flagType:"Org Role", flagValue:"Publisher"],
   *                [flagType:"Authorized", flagValue:"N"]]
   *      ]
   *
   */
  @Secured(value = ["hasRole('ROLE_API')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def assertOrg() {
    log.debug("assertOrg, request.json = ${request.JSON}")
    Map result = [result: 'OK']
    User user = springSecurityService.currentUser
    boolean assert_errors = false
    def jsonOrg = request.JSON
    boolean fullsync = false

    if (params.fullsync == "true" && user.adminStatus) {
      fullsync = true
    }

    try {
      result = orgService.upsert(jsonOrg, fullsync)

      if (result.error) {
        response.status = 400
      }
    }
    catch (Exception e) {
      log.error("Unexpected error importing org", e)
      result.result = 'ERROR'
      result.message = "Unexpected error importing the Org!"
      result.baddata = jsonOrg
      response.status = 500
    }
    render result as JSON
  }

  @Secured(value = ["hasRole('ROLE_API')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def crossReferencePlatform() {
    Map result = ['result': 'OK']
    boolean created = false
    def platformJson = request.JSON
    boolean fullsync = false
    User user = springSecurityService.currentUser

    if (params.fullsync == "true" && user.adminStatus) {
      fullsync = true
    }

    log.debug("crossReferencePlatform - ${platformJson}")
    if (platformJson?.platformUrl?.trim() && (platformJson?.name?.trim() || platformJson?.platformName?.trim())) {
      if (!platformJson.name?.trim() && platformJson.platformName) {
        platformJson.name = platformJson.platformName
      }

      try {
        Platform p = platformService.upsertDTO(platformJson)

        if (p) {
          log.debug("created or looked up platform ${p}!")

          componentUpdateService.setAllRefdata([
              'software', 'service'
          ], platformJson, p)
          ClassUtils.setRefdataIfPresent(platformJson.authentication, p, 'authentication', 'Platform.AuthMethod')

          if (platformJson.provider) {
            Org prov = null

            if (platformJson.provider instanceof String) {
              prov = Org.findByNormname(Org.generateNormname(platformJson.provider))
            }
            else {
              if (platformJson.provider.uuid) {
                prov = Org.findByUuid(platformJson.provider.uuid)
              }

              if (!prov && platformJson.provider.name) {
                prov = Org.findByNormname(Org.generateNormname(platformJson.provider.name))
              }
            }

            if (prov) {
              log.debug("Adding Provider ${prov} to platform ${p}!")
              p.provider = prov
            }
            else {
              log.debug("No provider found for ${platformJson.provider}!")
            }
          }

          p.save(flush: true)

          // Add the core data.
          componentUpdateService.ensureCoreData(p, platformJson, fullsync, user)

          result.message = "Created/Updated platform ${p}"
          result.platformId = p.id
        }
        else {
          log.debug("No platform matched for ${platformJson}")
          result.message = "Could not crossreference platform ${platformJson}"
          response.setStatus(500)
          result.result = 'ERROR'
        }
      }
      catch (Exception e) {
        log.debug("Exception while looking up platform!", e)
        result.message = "There was an error looking up platform ${platformJson}"
        response.setStatus(500)
        result.result = "ERROR"
      }
    }
    else {
      log.debug("Missing Platform info for ${platformJson}")
      result.message = "Platform ${platformJson} is missing required information ('name' or 'platformUrl')!"
      response.setStatus(400)
      result.result = "ERROR"
    }
    render result as JSON
  }

/**
 *  Cross reference an incoming title with the database
 *
 *  Cross reference record::
 *
 *  {
 *    'title':'the_title',
 *    'publisher':'the_publisher',
 *    'identifiers':[
 *       {type:'namespace',value:'value'},
 *       {type:'isbn', value:'1234-5678'}
 *    ],
 *    'type':<'Serial'|'Monograph'>,
 *    'variantNames':[
 *      'Array Of Strings - one for each variant name'
 *    ],
 *    'imprint':'the_publisher',
 *    'publishedFrom':'yyyy-MM-dd' 'HH:mm:ss.SSS',
 *    'publishedTo':'yyyy-MM-dd' 'HH:mm:ss.SSS',
 *    'editStatus':'edit_status_value',
 *    'status':'status_value',
 *    'historyEvents':[
 *    ],
 *    'series':'series_name',
 *    'subjectArea':'subject_area_name',
 *    'prices':[
 *       {
 *         'type':'list',
 *         'currency':'EUR',
 *         'amount':12.89
 *       }
 *    ]
 *  }
 */
  @Secured(value = ["hasRole('ROLE_API')", 'IS_AUTHENTICATED_FULLY'], httpMethod = 'POST')
  def crossReferenceTitle() {
    User user = springSecurityService.currentUser
    def rjson = request.JSON
    boolean cancelled = false
    boolean async = params.async ? params.boolean('async') : false
    Locale request_locale = RequestContextUtils.getLocale(request)
    boolean fullsync = false
    Map result = [result: 'OK']

    if (params.fullsync == "true" && user.adminStatus) {
      fullsync = true
    }

    if (JSONArray != rjson.getClass()) {
      result = titleUpsertService.upsert(rjson, user, request_locale, fullsync)
    }
    else {
      log.debug("Starting crossReferenceTitle Job")
      Job background_job = concurrencyManagerService.createJob { Job job ->
        titleUpsertService.upsertList(rjson, user, request_locale, job, fullsync)
      }
      log.debug("Starting job ${background_job}..")

      background_job.startOrQueue()
      background_job.description = "Title CrossRef"
      background_job.type = RefdataCategory.lookup('Job.Type', 'TitleCrossRef')
      background_job.startTime = new Date()

      if (async == false) {
        result = background_job.get()
      }
      else {
        result = [job_id: background_job.uuid]
      }
    }

    render result as JSON
  }

  def getJobInfo() {
    def result = ['result': 'OK', 'params': params]
    User user = null
    String uuid = params.id
    log.info("getJobInfo($uuid)")
    if (uuid == null) {
      result.result = "ERROR"
      response.setStatus(400)
      result.message = "Request has no id parameter."
    }
    else {
      if (springSecurityService.isLoggedIn()) {
        user = springSecurityService.currentUser
      }
      else if (params.updateToken?.trim()) {
        def updateToken = UpdateToken.findByValue(params.updateToken)

        if (updateToken) {
          user = updateToken.updateUser
        }
        else {
          log.error("Unable to reference update token!")
          result.message = "Unable to reference update token!"
          response.setStatus(400)
          result.result = "ERROR"
        }
      }
      else {
        response.setStatus(401)
        response.setHeader('WWW-Authenticate', 'Basic realm="gokb"')
      }

      if (user) {
        Job job = concurrencyManagerService.getJob(uuid)

        if (job) {
          log.debug("${job}")

          if (user.superUserStatus || (job.ownerId && job.ownerId == user.id)) {
            result.description = job.description
            result.type = job.type ? [value: job.type.value, id: job.type.id] : null
            result.linkedItem = job.linkedItem
            result.startTime = job.startTime

            if (job.endTime || job.isCancelled()) {
              result.finished = true
              result.endTime = job.endTime
              try {
                result.job_result = job.get()
              }
              catch (CancellationException ce) {
                result.cancelled = true
              }
            }
            else {
              result.finished = false
              result.progress = job.progress
            }
          }
          else {
            result.result = "ERROR"
            response.setStatus(403)
            result.message = "No permission to view job with ID ${uuid}."
          }
        }
        else {
          def persistedResult = JobResult.findByUuid(uuid)

          if (persistedResult) {
            def linkedItemMap = null

            if (persistedResult.linkedItemId) {
              def linkedItem = KBComponent.get(persistedResult.linkedItemId)

              if (linkedItem) {
                linkedItemMap = [id: linkedItem.id, name: linkedItem.name, uuid: linkedItem.uuid, type: linkedItem.niceName]
              }
            }

            result.description = persistedResult.description
            result.type = persistedResult.type ? [value: persistedResult.type.value, id: persistedResult.type.id] : null
            result.linkedItem = linkedItemMap
            result.startTime = persistedResult.startTime
            result.endTime = persistedResult.endTime
            result.job_result = new JsonSlurper().parseText(persistedResult.resultObject)
            result.finished = true

            if (result.job_result?.result == 'CANCELLED') {
              result.cancelled = true
            }
          }
          else {
            result.result = "ERROR"
            response.setStatus(404)
            result.message = "Could not find job with ID ${uuid}."
          }
        }
      }
    }
    render result as JSON
  }

  private def cleanUpGorm() {
    log.debug("Clean up GORM");

    // Get the current session.
    KBComponent.withTransaction {
      def session = sessionFactory.currentSession

      // flush and clear the session.
      session.flush()
      session.clear()
    }
  }
}
