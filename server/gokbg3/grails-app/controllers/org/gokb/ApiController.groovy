package org.gokb

import com.k_int.ConcurrencyManagerService
import com.k_int.ExtendedHibernateDetachedCriteria
import com.k_int.TsvSuperlifterService
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

  TsvSuperlifterService tsvSuperlifterService
  SecureRandom rand = new SecureRandom()
  UploadAnalysisService uploadAnalysisService
  def ESWrapperService
  def ESSearchService
  def zdbAPIService

  static def reversemap = ['subject':'subjectKw','componentType':'componentType','identifier':'identifiers.value']
  static def non_analyzed_fields = ['componentType','identifiers.value']

  private static final Closure TRANSFORMER_USER = {User u ->
    [
      "id"      : "${u.id}",
      "email"     : "${u.email}",
      "username"    : "${u.username}",
      "displayName"   : "${u.displayName ?: u.username}"
    ]
  }

  private static final Closure TRANSFORMER_PROJECT = {

    // Treat as refine project.
    RefineProject proj = it as RefineProject

    // Populate the map manually instead of excluding more and more.
    TreeMap props = [
      "id"                : proj.id,
      "localProjectID"    : proj.localProjectID,
      "name"              : proj.name,
      "description"       : proj.description,
      "projectStatus"     : proj.projectStatus,
      "lastCheckedOutBy"  : ApiController.TRANSFORMER_USER (proj.lastCheckedOutBy),
      "progress"          : proj.progress,
      "modified"          : proj.modified,
      "createdBy"         : ApiController.TRANSFORMER_USER (proj.createdBy),
    ]

    return props
  }

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

    def json = data as JSON
    //  log.debug (json)
    render json
    //    render (text: "${params.callback}(${json})", contentType: "application/javascript", encoding: "UTF-8")
  }

  def index() {
  }

  @Secured(['IS_AUTHENTICATED_FULLY'])
  def checkLogin() {
    apiReturn(["login": true])
  }

  def userData() {
    if (!springSecurityService.currentUser) {
      return
    }
    apiReturn ( TRANSFORMER_USER( springSecurityService.currentUser ) )
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
      result.add([value: ns.value, namespaceName:ns.name, category: ns.family ?: ""])
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

  @Secured(['ROLE_SUPERUSER', 'ROLE_REFINEUSER', 'IS_AUTHENTICATED_FULLY'])
  def quickCreate() {
    // Get the type of component we are going to attempt to create.
    def type = params.qq_type

    try {
      Class<? extends KBComponent> c = grailsApplication.getClassLoader().loadClass(
        "org.gokb.cred.${GrailsNameUtils.getClassNameRepresentation(type)}"
      )

      // Try and create a new instance passing in the supplied parameters.
      def comp = c.newInstance()

      // Set all the parameters passed in.
      params.each { prop, value ->
        // Only set the property if we have a value.
        if (value != null && value != "") {
          try {

            // We may get a component ID here now. Just run it through the component
            // lookup service. If it isn't the correct format it will return quickly.
            KBComponent com = componentLookupService.lookupComponent(value)
            if (com) {
              // Set to the component value.
              comp."${prop}" = com
            } else {
              comp."${prop}" = value
            }

          } catch (Throwable t) {
            /* Suppress the error */
          }
        }
      }

      switch (c) {

        case Package :

          // We may also need to create a review request against Packages created here.
          if ( !comp.provider ) {
            ReviewRequest.raise (
              comp,
              "Review and set provider of this package.",
              "Package created in refine without a provider.",
              springSecurityService.currentUser
            )
          }
          break;
      }

      // Save.
      comp.save(failOnError: true)

      // Now that the object has been saved we need to return the string.
      apiReturn("${comp.name}::{${c.getSimpleName()}:${comp.id}}")

    } catch (Throwable t) {
      apiReturn (t, "There was an error creating a new Component of ${type}")
    }
  }

  // this is used as an entrypoint for single page apps based on frameworks like angular.
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def search() {
    def result = [result: 'OK']

    User user = springSecurityService.currentUser

    log.debug("Entering SearchController:index");

    result.max = params.max ? Integer.parseInt(params.max) : ( user.defaultPageSize ?: 10 );
    result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

    if ( request.JSON ) {

        result.qbetemplate = request.JSON.cfg

        // Looked up a template from somewhere, see if we can execute a search
        if ( result.qbetemplate ) {

          Class target_class = Class.forName(result.qbetemplate.baseclass);
          def read_perm = target_class?.isTypeReadable()

          if (read_perm) {
            log.debug("Execute query");
            def qresult = [max:result.max, offset:result.offset]
            result.rows = doQuery(result.qbetemplate, params, qresult)
            log.debug("Query complete");
            result.lasthit = result.offset + result.max > qresult.reccount ? qresult.reccount : ( result.offset + result.max )

            // Add the page information.
            result.page_current = (result.offset / result.max) + 1
            result.page_total = (qresult.reccount / result.max).toInteger() + (qresult.reccount % result.max > 0 ? 1 : 0)
          }
          else {
            result.result = 'ERROR'
            result.code = 403
            response.setStatus(403)
            result.message = "Insufficient permissions to view this resource."

            log.debug("No permission to view this resource!")
          }
        }
        else {
          log.debug("no template ${result?.qbetemplate}");
        }
    }
    else {
      log.debug("No request json");
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
    def result = [:]
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
        result.result = "ERROR"
      }

    }finally {
      if (result.errors) {
        response.setStatus(400)
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
    def result = [:]
    try {
      result = ESSearchService.scroll(params)
    }
    catch(Exception e){
      result.result = "ERROR"
      result.message = e.message
      result.cause = e.cause
      log.error("Could not process scroll request. Exception was: ${e.message}")
      response.setStatus(400)
    }
    render result as JSON
  }


  private def buildQuery(params) {

    StringWriter sw = new StringWriter()

    if ( params?.q != null )
      if(params.q.equals("*")){
        sw.write(params.q)
      }
      else{
        sw.write("(${params.q})")
      }
    else
      sw.write("*:*")

    // For each reverse mapping
    reversemap.each { mapping ->

      // log.debug("testing ${mapping.key}");

      // If the query string supplies a value for that mapped parameter
      if ( params[mapping.key] != null ) {

        // If we have a list of values, rather than a scalar
        if ( params[mapping.key].class == java.util.ArrayList) {
          params[mapping.key].each { p ->
            sw.write(" AND ")
            sw.write(mapping.value)
            sw.write(":")

            if(non_analyzed_fields.contains(mapping.value)) {
              sw.write("${p}")
            }
            else {
              sw.write("\"${p}\"")
            }
          }
        }
        else {
          // We are dealing with a single value, this is "a good thing" (TM)
          // Only add the param if it's length is > 0 or we end up with really ugly URLs
          // II : Changed to only do this if the value is NOT an *
          if ( params[mapping.key].length() > 0 && ! ( params[mapping.key].equalsIgnoreCase('*') ) ) {
            sw.write(" AND ")
            // Write out the mapped field name, not the name from the source
            sw.write(mapping.value)
            sw.write(":")

            if(non_analyzed_fields.contains(mapping.value)) {
              sw.write("${params[mapping.key]}")
            }
            else {
              sw.write("\"${params[mapping.key]}\"")
            }
          }
        }
      }
    }

    def result = sw.toString();
    result;
  }

  /**
   * show : Returns a simplified JSON serialization of a domain class object
   * @param oid : The OID ("<FullyQualifiedClassName>:<PrimaryKey>") of the object
   * @param withCombos : Also return all combos directly linked to the object
  **/

  def show() {
    def result = ['result':'OK', 'params': params]
    if (params.oid || params.id) {
      def obj = genericOIDService.resolveOID(params.oid ?: params.id)

      if ( obj?.isReadable() || (obj?.class?.simpleName == 'User' && obj?.equals(springSecurityService.currentUser)) ) {

        if(obj.class in KBComponent) {

          result.resource = obj.getAllPropertiesWithLinks(params.withCombos ? true : false)

          result.resource.combo_props = obj.allComboPropertyNames
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


  def private doQuery (qbetemplate, params, result) {
    log.debug("doQuery ${result}");
    def target_class = grailsApplication.getArtefact("Domain",qbetemplate.baseclass);
    com.k_int.HQLBuilder.build(grailsApplication, qbetemplate, params, result, target_class, genericOIDService)
    def resultrows = []

    log.debug("process recset..");
    int seq = result.offset
    result.recset.each { rec ->
      // log.debug("process rec..");
      def response_row = [:]
      response_row['__oid'] = rec.class.name+':'+rec.id
      response_row['__seq'] = seq++
      qbetemplate.qbeConfig.qbeResults.each { r ->
        def ppath = r.property.split(/\./)
        def cobj = rec
        def final_oid = "${cobj.class.name}:${cobj.id}"

        ppath.eachWithIndex { prop, idx ->
          def sp = prop.minus('?')

          if( cobj?.class?.name == 'org.gokb.cred.RefdataValue' ) {
            cobj = cobj.value
          }
          else {
            if ( cobj && KBComponent.has(cobj, sp)) {
              if (sp == 'password' || sp == 'email') {
                cobj = null
              }
              else {
                cobj = cobj[sp]
              }

              if (ppath.size() > 1 && idx == ppath.size()-2) {
                if (cobj && sp != 'class') {
                  final_oid = "${cobj.class.name}:${cobj.id}"
                }
                else {
                  final_oid = null
                }
              }
            }
            else {
              cobj = null
            }
          }
        }
        response_row["${r.property}"] = [heading: r.heading, link: (r.link ? (final_oid ?: response_row.__oid ) : null), value: (cobj ?: '-Empty-')]
      }
      resultrows.add(response_row);
    }
    resultrows
  }

  private static final def CAPABILITIES = [
    "core"                : true,
    "project-mamangement" : true,
    "cell-level-edits"    : true,
    "es-recon"            : true,
    "macros"              : true,
  ]

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  def esconfig () {

    // If etag matches then we can just return the 304 to denote that the resource is unchanged.
    render grailsApplication.config.searchApi as JSON
  }

  private static final Closure SERVER_VERSION_ETAG_DSL = {
    def capabilities = getCapabilities()

    // ETag DSL must return a String and not a GString due to GStringImpl.equals(String) failing even if their character sequences are equal.
    // See: https://jira.grails.org/browse/GPCACHEHEADERS-14
    "${capabilities.app.version}${capabilities.app.buildNumber}".toString()
  }

  @Secured(['ROLE_ADMIN', 'IS_AUTHENTICATED_FULLY'])
  def bulkLoadUsers() {

    log.debug("bulkLoadUsers");

    def result = [:]

    if ( request.method=='POST') {
      log.debug("Handling post")
      User.withNewSession() {

        if ( request instanceof MultipartHttpServletRequest ) {
          def users_stream = request.getFile("users")?.inputStream
          result.loaderResult = tsvSuperlifterService.load(users_stream,
                                                           User.tsv_dataload_config,
                                                           params.dryRun=='Y'?true:false)
        }
      }
    }

    render result as JSON
  }

  private final def checkAlias = { def criteria, Map aliasStack, String dotNotationString, int joint_type = CriteriaSpecification.INNER_JOIN ->
    def str = aliasStack[dotNotationString]
    if (!str) {

      // No alias found for exact match.
      // Start from the front and build up aliases.
      String[] props = dotNotationString.split("\\.")
      String propStr = "${props[0]}"
      String alias = aliasStack[propStr];
      int counter = 1
      while (alias && counter < props.length) {
        str = "${alias}"
        String test = propStr + ".${props[counter]}"

        alias = aliasStack[test]
        if (alias) {
          propStr += test
        }
        counter ++
      }

      // At this point we should have a dot notated alias string, where the aliases already been created for this query.
      // Any none existent aliases will need creating but also adding to the map for traversing.
      if (counter <= props.length) {
        // The counter denotes how many aliases were present, so we should start at the counter and create the missing
        // aliases.
        propStr = null
        for (int i=(counter-1); i<props.length; i++) {
          String aliasVal = alias ? "${alias}.${props[i]}" : "${props[i]}"
          alias = "alias${aliasStack.size()}"

          // Create the alias.
          log.debug ("Creating alias: ${aliasVal} ->  ${alias}")
          criteria.createAlias(aliasVal, alias, joint_type)

          // Add to the map.
          propStr = propStr ? "${propStr}.${props[i]}" : "${props[i]}"
          aliasStack[propStr] = alias
          log.debug ("Added quick string: ${propStr} -> ${alias}")
        }
      }

      // Set the string to the alias we ended on.
      str = alias
    }

    str
  }

  private Closure theQueryCriteria = {  String term, match_in, filters, boolean unique, crit = null ->
    final Map<String, String> aliasStack = [:]

    and {
      if (term && match_in) {
        // Add a condition for each parameter we wish to search.

        or {
          match_in.each { String propname ->

            // Split at the dot.
            String[] levels = propname.split("\\.")

            String propName
            if (levels.length > 1) {

              // Optional joins use LEFT_JOIN
              String aliasName = checkAlias ( delegate, aliasStack, levels[0..(levels.size() - 2)].join('.'), CriteriaSpecification.LEFT_JOIN)
              String finalPropName = levels[levels.size()-1]
              String op = finalPropName == 'id' ? 'eq' : 'ilike'
              String toFind = finalPropName == 'id' ? "${term}".toLong() : "%${term}%"

              log.debug ("Testing  ${aliasName}.${finalPropName} ${op} ${toFind}")
              "${op}" "${aliasName}.${finalPropName}", toFind
            } else {
              String op = propname == 'id' ? 'eq' : 'ilike'
              String toFind = propname == 'id' ? "${term}".toLong() : "%${term}%"

              log.debug ("Testing  ${propname} ${op} ${toFind}")
              "${op}" "${propname}", toFind
            }
          }
        }
      }

      // Filters...
      if (filters) {
        filters.eachWithIndex { String filter, idx ->
          String[] parts =  filter.split("\\=")

          if ( parts.length == 2 && parts[0].length() > 0 && parts[1].length() > 0 ) {

            // The prop name.
            String propname = parts[0]
            String op = "eq"

            if (propname.startsWith("!")) {
              propname = propname.substring(1)
              op = "ne"
            }

            // Split at the dot.
            String[] levels = propname.split("\\.")

            String propName
            if (levels.length > 1) {
              String aliasName = checkAlias ( delegate, aliasStack, levels[0..(levels.size() - 2)].join('.') )
              String finalPropName = levels[levels.size()-1]

              log.debug ("Testing  ${aliasName}.${finalPropName} ${op == 'eq' ? '=' : '!='} ${parts[1]}")
              "${op}" "${aliasName}.${finalPropName}", finalPropName == 'id' ? parts[1].toLong() : parts[1]
            } else {
              log.debug ("Testing  ${propname} ${op == 'eq' ? '=' : '!='} ${parts[1]}")
              "${op}" propname, parts[1] == 'id' ? parts[1].toLong() : parts[1]
            }
          }
        }
      }
    }
    if (unique) {
      projections {
        distinct('id')
      }
    }
  }

  private Closure lookupCriteria = { String term, match_in, filters, attr = [], boolean unique = true ->
    final Map<String, String> aliasStack = [:]

    if (unique) {

      // Use the closure as a subquery so we can return unique ids.
      // We need to deal directly with Hibernate here.
      ExtendedHibernateDetachedCriteria subQ = new ExtendedHibernateDetachedCriteria(targetClass.createCriteria().buildCriteria (theQueryCriteria.curry(term, match_in, filters, unique)))

      criteria.add(Subqueries.propertyIn('id', subQ));
    } else {

      // Execute the queryCriteria in this context.
      (theQueryCriteria.rehydrate(delegate, owner, thisObject))(term, match_in, filters, unique)
    }

    // If we have a list of return attributes then we should add projections here.
    if (attr) {
      resultTransformer CriteriaSpecification.ALIAS_TO_ENTITY_MAP
      projections {
        attr.each { String propname ->

          // Split at the dot.
          String[] levels = propname.split("\\.")

          String propName
          if (levels.length > 1) {
            String aliasName = checkAlias (delegate, aliasStack, levels[0..(levels.size() - 2)].join('.'), CriteriaSpecification.LEFT_JOIN )
            String finalPropName = levels[levels.size()-1]

            String[] propAliasParts = finalPropName.split("\\:")
            finalPropName = propAliasParts[0]
            String propAlias = propAliasParts.length > 1 ? propAliasParts[1] : propAliasParts[0]

            log.debug ("Returning ${aliasName}.${finalPropName} as ${propAlias}")
            property "${aliasName}.${finalPropName}", "${propAlias}"
          } else {
            String[] propAliasParts = propname.split("\\:")
            String finalPropName = propAliasParts[0]
            String propAlias = propAliasParts.length > 1 ? propAliasParts[1] : propAliasParts[0]

            log.debug ("Returning ${finalPropName} as ${propAlias}")
            property "${finalPropName}", "${propAlias}"
          }
        }
      }
    }
  }

  @Secured(['ROLE_SUPERUSER', 'ROLE_REFINEUSER', 'IS_AUTHENTICATED_FULLY'])
  synchronized def lookup () {
    long start = System.currentTimeMillis()
    String classType = GrailsNameUtils.getClassNameRepresentation(params.type)
    Class<? extends KBComponent> c = grailsApplication.getClassLoader().loadClass(
      "org.gokb.cred.${params.type}"
    )

    // Get the "term" parameter for performing a search.
    def term = params.term

    // Results per page.
    def perPage = Math.min(params.int('perPage') ?: 10, 10)

    // Object attributes to search.
    def match_in = ["name"]

    // Lists from jQuery come through with brackets...
    match_in += params.list("match")
    match_in += params.list("match[]")

    // Ensure we only include the none label part.
    match_in = match_in.collect { "${it}".split("\\:")[0] }

    def filters = [
      "!status.id=${RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_RETIRED).id}",
      "!status.id=${RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_DELETED).id}"
    ]
    filters += params.list("filters")

    // Attributes to return.
    List attr = ["name:label", 'id']
    attr += params.list("attr")
    attr += params.list("attr[]")

    // Page number.
    def page = params.int("page")

    // If we have a page then we should add a max and offset.
    def query_params = ["max": (perPage)]
    if (page) {
      query_params["offset"] = ((page - 1) * perPage)
    }

    // Build the detached criteria.
    def results = c.createCriteria().list (query_params, lookupCriteria.curry(term, match_in, filters, attr))

    def resp
    if (page) {
      // Return the page of results with a total.
      resp = [
        "total" : results.totalCount,
        "list"  : results.collect {
          it.value = "${it.label}::{${classType}:${it.id}}"
          it
        } as LinkedHashSet
      ]
    } else {
      // Just return the formatted results.
      resp = results.collect {
        it.value = "${it.label}::{${classType}:${it.id}}"
        it
      } as LinkedHashSet
    }

    // Return the response.
    apiReturn (resp)
    log.debug "lookup took ${System.currentTimeMillis() - start} milliseconds"
  }


  /**
   * See the service method {@link com.k_int.ESSearchService#getApiTunnel(def params)} for usage instructions.
   */
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

  def retrieveZdbCandidates() {
    def result = [result: 'OK']
    def title = TitleInstance.get(genericOIDService.oidToId(params.id))

    if (title) {
      result.candidates = zdbAPIService.lookup(title.name, title.ids)
    }
    else {
      result.result = 'ERROR'
      result.message = "Title not found!"
    }

    render result as JSON
  }
}
