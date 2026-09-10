package org.gokb

import com.k_int.ConcurrencyManagerService
import com.k_int.ExtendedHibernateDetachedCriteria
import grails.converters.JSON
import grails.util.GrailsNameUtils
import groovy.util.logging.*
import org.gokb.cred.*
import org.gokb.refine.RefineProject
import org.hibernate.criterion.CriteriaSpecification
import org.hibernate.criterion.Subqueries
import org.springframework.security.access.annotation.Secured
import org.springframework.web.multipart.MultipartHttpServletRequest

import java.security.SecureRandom

/**
 * TODO: Change methods to abide by the RESTful API, and implement GET, POST, PUT and DELETE with proper response codes.
 *
 * @author Steve Osguthorpe
 */
@Slf4j
class ApiController {
  SecureRandom rand = new SecureRandom()
  def ESWrapperService
  def ESSearchService
  def zdbAPIService

  def springSecurityService
  def componentLookupService
  def genericOIDService
  ConcurrencyManagerService concurrencyManagerService

  /**
   * Check if the api is up. Just return true.
   */
  def isUp() {
    apiReturn(["isUp" : true])
  }

  // Internal API return object that ensures consistent formatting of API return objects
  private def apiReturn = {result, String message = "", String status = (result instanceof Throwable) ? "error" : "success" ->

    // If the status is error then we should log an entry.
    if (status == 'error') {

      // Generate 6bytes of random data to be base64 encoded which can be returned to the user to help with tracking issues in the logs.
      byte[] randomBytes = new byte[6]
      rand.nextBytes(randomBytes)
      def ticket = Base64.encodeBase64String(randomBytes);

      // Let's see if we have a throwable.
      if (result && result instanceof Throwable) {

        // Log the error with the stack...
        log.error("[[${ticket}]] - ${message == "" ? result.getLocalizedMessage() : message}", result)
      } else {
        log.error("[[${ticket}]] - ${message == "" ? 'An error occured, but no message or exception was supplied. Check preceding log entries.' : message}")
      }

      // Ensure we have something to send back to the user.
      if (message == "") {
        message = "An unknow error occurred."
      } else {

        // We should now send the message along with the ticket.
        message = "${message}".replaceFirst("\\.\\s*\$", ". The error has been logged with the reference '${ticket}'")
      }
    }

    def data = [
      code    : (status),
      result    : (result),
      message    : (message),
    ]

    render data as JSON
  }

  def index() {
  }

  @Secured(['IS_AUTHENTICATED_FULLY'])
  def checkLogin() {
    apiReturn(["login": true])
  }

  def refdata() {
    def result = [:];

    // Should take a type parameter and do the right thing. Initially only do one type
    switch ( params.type ) {
      case 'cp' :
        def oq = Org.createCriteria()
        def orgs = oq.listDistinct {
          roles {
            "owner" {
              eq('desc','Org.Role');
            }
            eq('value','Content Provider');
          }
          order("name", "asc")
        }
        result.datalist=new java.util.ArrayList()
        orgs.each { o ->
          result.datalist.add([ "value" : "${o.id}", "name" : (o.name) ])
        }
        break;

      case 'org' :
        def oq = Org.createCriteria()
        def orgs = oq.listDistinct {
          order("name", "asc")
        }
        result.datalist=new java.util.ArrayList()
        orgs.each { o ->
          result.datalist.add([ "value" : "${o.id}", "name" : (o.name) ])
        }
        break;
      default:
        break;
    }
    apiReturn(result)
  }

  def namespaces() {

    def result = []
    def all_ns = null

    if (params.category && params.category?.trim().size() > 0) {
      all_ns = IdentifierNamespace.findAllByFamily(params.category)
    }
    else {
      all_ns = IdentifierNamespace.findAll()
    }

    all_ns.each { ns ->
      result.add([value: ns.value, namespaceName: ns.name, category: ns.family ?: ""])
    }

    apiReturn(result)
  }

  def groups() {

    def result = []

    CuratoryGroup.list().each {
      result << [
        'id':  it.id,
        'name': it.name,
        'editStatus': it.editStatus?.value ?: null,
        'status': it.status?.value ?: null,
        'uuid': it.uuid
      ]
    }

    apiReturn(result)
  }

  /**
   * suggest : Get a list of autocomplete suggestions from ES
   *
   * @param max : Define result size
   * @param offset : Define offset
   * @param from : Define offset
   * @param q : Search term
   * @param componentType : Restrict search to specific component type (Package, Org, Platform, BookInstance, JournalInstance, TIPP)
   * @param role : Filter by Org role (only in context of componentType=Org)
   * @return JSON Object
  **/

  def suggest() {
    Map result = [:]
    def searchParams = params

    try {

      if ( params.q?.length() > 0 ) {
        searchParams.suggest = params.q
        searchParams.remove("q")

        if (!searchParams.mapRecords) {
          searchParams.skipDomainMapping = true
        }
        else {
          searchParams.remove("mapRecords")
        }

        result = ESSearchService.find(searchParams)
      }
      else{
        result.errors = ['fatal': "No query parameter 'q=' provided"]
        result.status = 400
        result.result = "ERROR"
      }

    } finally {
      if (result.status) {
        response.setStatus(result.status)
      }
    }

    render result as JSON
  }

  /**
   * find : Query the Elasticsearch index via ESSearchService
  **/
  def find() {
    def result = [:]
    def searchParams = params

    if (!searchParams.mapRecords) {
      searchParams.skipDomainMapping = true
    }
    else {
      searchParams.remove("mapRecords")
    }

    try {
      result = ESSearchService.find(searchParams)
    }
    finally {
      if (result.errors) {
        response.setStatus(400)
      }
    }
    render result as JSON
  }


  /**
    * scroll : Deliver huge amounts of Elasticsearch data
    **/
  def scroll() {
    Map result = [:]
    try {
      result = ESSearchService.scroll(params)
    }
    catch (Exception e) {
      result.result = "ERROR"
      result.message = e.message
      result.cause = e.cause
      log.error("Could not process scroll request. Exception was: ${e.message}", e)
      response.setStatus(400)
    }


    render result as JSON
  }

  /**
   * show : Returns a simplified JSON serialization of a domain class object
   * @param oid : The OID ("<FullyQualifiedClassName>:<PrimaryKey>") of the object
  **/

  @Secured("hasRole('ROLE_SUPERUSER') and isFullyAuthenticated()")
  def show() {
    def result = ['result':'OK', 'params': params]
    if (params.oid || params.id) {
      def obj = genericOIDService.resolveOID(params.oid ?: params.id)

      if ( obj?.isReadable() || (obj?.class?.simpleName == 'User' && obj?.equals(springSecurityService.currentUser)) ) {

        if(obj.class in KBComponent) {

          result.resource = obj.getAllPropertiesWithLinks()
        }
        else if (obj.class.name == 'org.gokb.cred.User'){

          def cur_groups = []

          obj.curatoryGroups?.each { cg ->
            cur_groups.add([name: cg.name, id: cg.id])
          }

          result.resource = ['id': obj.id, 'username': obj.username, 'displayName': obj.displayName, 'curatoryGroups': cur_groups]
        }
        else {
          result.resource = obj
        }
      }
      else if (!obj) {
        result.error = "Object ID could not be resolved!"
        response.setStatus(404)
        result.code = 404
        result.result = 'ERROR'
      }
      else {
        result.error = "Access to object was denied!"
        response.setStatus(403)
        result.code = 403
        result.result = 'ERROR'
      }
    }
    else {
      result.result = 'ERROR'
      response.setStatus(400)
      result.code = 400
      result.error = 'No object id supplied!'
    }

    render result as JSON
  }

  @Secured("hasRole('ROLE_ADMIN') and isFullyAuthenticated()")
  def esconfig () {

    // If etag matches then we can just return the 304 to denote that the resource is unchanged.
    render grailsApplication.config.getProperty('searchApi', Map, [:]) as JSON
  }

  /**
   * See the service method {@link com.k_int.ESSearchService#getApiTunnel(def params)} for usage instructions.
   */

  @Secured("hasRole('ROLE_SUPERUSER') and isFullyAuthenticated()")
  def elasticsearchTunnel() {
    def result = [:]
    try {
      result = ESSearchService.getApiTunnel(params)
    }
    catch(Exception e){
      result.result = "ERROR"
      result.message = e.message
      result.cause = e.cause
      log.error("Could not process Elasticsearch API request. Exception was: ${e.message}")
      response.setStatus(400)
    }
    render result as JSON
  }

  @Secured("hasRole('ROLE_API') and isFullyAuthenticated()")
  def retrieveZdbCandidates() {
    def result = [result: 'OK']
    def title = TitleInstance.get(genericOIDService.oidToId(params.id))

    if (title) {
      result = zdbAPIService.lookup(title.name, title.ids)
    }
    else {
      result.result = 'ERROR'
      result.message = "Title not found!"
    }

    render result as JSON
  }
}
