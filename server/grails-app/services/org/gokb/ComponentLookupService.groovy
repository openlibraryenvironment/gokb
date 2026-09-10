package org.gokb

import com.github.ladutsko.isbn.*

import grails.util.GrailsNameUtils
import grails.util.Holders
import grails.validation.ValidationException

import groovy.transform.Synchronized
import groovy.util.logging.*

import io.micronaut.http.uri.UriBuilder

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.regex.Matcher

import org.gokb.cred.*
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*

@Slf4j
class ComponentLookupService {
  def grailsApplication
  def restMappingService

  public static final List<String> ID_REGEX_TEMPLATE = ["^gokb::\\{", "\\:(\\d+)\\}\$"]

  static final Map<String,String> MAPPED_PROPS = [
      'linkedIds': 'ids',
      'publisherLinks': 'publisher'
  ]


  public <T extends KBComponent> Map<String, T> lookupComponents(String... comp_name_strings) {
    Map<String, T> results = [:]
    for (String comp_name_string : comp_name_strings) {
      T comp = lookupComponent (comp_name_string)
      if (comp) {
        // Add the result.
        results["${comp_name_string}"] = comp
      }
    }

    results
  }

  public <T extends KBComponent> Map<String, T> lookupComponents(Collection<String> comp_name_strings) {
    Map<String, T> results = [:]
    for (String comp_name_string : comp_name_strings) {
      T comp = lookupComponent (comp_name_string)
      if (comp) {
        // Add the result.
        results["${comp_name_string}"] = comp
      }
    }

    results
  }

//  private Map<String, ?> vals = [:].withDefault { String key ->
//    lookupComponentDB (key)
//  }
//
//  public <T extends KBComponent> T lookupComponent(String comp_name_string) {
//
//    // Merge this object into the current session if needed.
//    T object = (T)vals.get(comp_name_string)
//    if (object != null && !object.isAttached()) {
//      object = object.merge()
//      vals.put(comp_name_string, object)
//    }
//    return object
//  }

  private <T extends KBComponent> T lookupComponent (String comp_name_string) {
    return lookupComponent(comp_name_string,false)
  }

  private <T extends KBComponent> T lookupComponent (String comp_name_string, boolean lock) {

    // The Component
    T comp = null
    if (comp_name_string) {
      Matcher component_match

      if ((component_match = comp_name_string =~ "${ID_REGEX_TEMPLATE[0]}([^\\:]+)${ID_REGEX_TEMPLATE[1]}\$") ||
        (component_match = comp_name_string =~ "${REGEX_TEMPLATE[0]}([^\\:]+)${REGEX_TEMPLATE[1]}\$")) {

        log.debug ("Matched the component syntax \"Display Text::{ComponentType:ID}\".")

        try {

          // Partial or complete class name.
          String cls_name = component_match[0][1]
          if (!cls_name.contains('.')) {
            cls_name = "org.gokb.cred.${GrailsNameUtils.getClassNameRepresentation(cls_name)}"
          }

          log.debug("Try and lookup ${cls_name} with ID ${component_match[0][2]}")

          // We have a match.
          Class<? extends KBComponent> c = Holders.grailsApplication.getClassLoader().loadClass("${cls_name}")

          // Parse the long.
          long the_id = Long.parseLong( component_match[0][2] )

          if (the_id > 0) {

            // Try and get the component.
            if ( lock ) {
              comp = c.lock(the_id)
            }
            else {
              comp = c.get(the_id)
            }

            if (!c) log.debug ("No component with that ID. Return null.")
          } else {
            log.debug ("Attempting to create a new component.")
            comp = c.newInstance()
          }

        } catch (Throwable t) {
          // Suppress errors here. Just return null.
          log.debug("Unable to parse component string.", t)
        }
      }
    }

    comp
  }

  @Synchronized
  static Identifier lookupOrCreateCanonicalIdentifier(String ns, String value, boolean ns_create = false) {
    return findOrCreateId(ns, value, ns_create)
  }

  private static Identifier findOrCreateId(String ns, String value, boolean ns_create = false) {
    log.debug("lookupOrCreateCanonicalIdentifier(${ns},${value})");
    IdentifierNamespace namespace = null
    Identifier identifier = null
    List namespaces = IdentifierNamespace.findAllByValueIlike(ns)

    switch ( namespaces.size() ) {
      case 0:
        if (ns_create) {
          IdentifierNamespace.withTransaction {
            namespace = new IdentifierNamespace(value:ns.toLowerCase()).save(flush: true, failOnError:true)
          }
        }
        break
      case 1:
        namespace = namespaces[0]
        break
      default:
        throw new RuntimeException("Multiple Namespaces with value ${ns}");
        break
    }

    if (namespace) {
      String validation_result = grails.util.Holders.applicationContext.getBean('validationService').checkIdForNamespace(value, namespace)

      if (validation_result) {
        String final_val = value

        if (namespace.family == 'isxn') {
          final_val = final_val.replaceAll("x","X")
        }

        if (namespace.value in ['isbn', 'pisbn']) {
          final_val = ISBN.parseIsbn(final_val).getIsbn13()
        }

        String norm_id = Identifier.normalizeIdentifier(final_val)
        List existing = Identifier.findAllByNamespaceAndNormname(namespace, norm_id)
        log.debug("Found ID: ${existing}")

        if ( existing?.size() == 1 ) {
          identifier = existing[0]
        }
        else if ( existing?.size() > 1 ) {
          log.error("Conflicting identifiers found: ${existing}")
          throw new RuntimeException("Found duplicates for Identifier: ${existing}")
        }
        else {
          log.debug("No matches: ${existing}")

          if (!identifier) {
            log.debug("Creating new Identifier ${namespace}:${value} ..")

            try {
              identifier = new Identifier(namespace: namespace, value: final_val, normname: norm_id).save(flush:true, failOnError:true)
            }
            catch (org.springframework.orm.hibernate5.HibernateOptimisticLockingFailureException lfe) {
              log.error("Locking failure", lfe)
              List ex = Identifier.findAllByNamespaceAndNormname(namespace, norm_id)
              log.debug("After LFE: ${ex}")
            }
            catch (ValidationException ve) {
              log.debug("Caught validation exception: ${ve.message}")

              if (ve.message.contains('already exists')) {
                List dupes = Identifier.executeQuery("from Identifier where normname = :nid and namespace = :ns",[nid: norm_id, ns: namespace])

                if (dupes.size() == 1) {
                  identifier = dupes[0]
                }

                log.error("Thread synchronization failed for ID ${dupes} ...")
              }
              else {
                throw new ValidationException(ve.message, ve.errors)
              }
            }
          }
        }
      }
      else {
        log.debug("Validation failed for ${namespace.value}:${value}!")
      }
    }

    identifier
  }

  /**
   *  restLookup : Look up components via HQL query.
   * @param cls : The Class of objects to be searched for
   * @param params : The map of request parameters
   * @param context : Possible override of the self link path
   */

  public Map restLookup (User user, cls, params, def context = null, boolean idOnly = false) {
    log.debug("restLookup: ${params}")
    Map result = [:]
    String hqlQry = "from ${cls.simpleName} as p".toString()
    Map qryParams = new HashMap()
    int max = params.limit ? params.long('limit') : 10
    int offset = params.offset ? params.long('offset') : 0
    boolean first = true
    String sort = null
    String sortField = null
    String order = params['_order']?.toLowerCase() == 'desc' ? 'desc' : 'asc'

    if ( KBComponent.isAssignableFrom(cls) ) {
      if (params.q?.trim()) {
        log.debug("Using generic term search with '${params.q}'..")

        Long validLong = null

        try {
          validLong = Long.valueOf(params.q)
        }
        catch (java.lang.NumberFormatException nfe) {
        }

        if (first) {
          hqlQry += " WHERE "
          first = false
        }
        else {
          hqlQry += " AND "
        }

        if (params.q ==~ /^[a-z0-9]{8}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{12}$/) {
          hqlQry += "p.uuid = :idqval"
        }
        else {
          hqlQry += "("
          hqlQry += '''lower(p.name) like lower(:qname)
                        OR EXISTS (
                          select ci from ComponentIdentifier as ci
                          where ci.component = p
                          and lower(ci.identifier.value) like lower(:idqval)
                          and ci.status = :idqstatus
                        )
                        OR EXISTS (
                          select an from KBComponentVariantName as an
                          where lower(an.variantName) like lower(:qname)
                          and an.owner = p
                        )'''

          if (validLong) {
            qryParams["qid"] = validLong
            hqlQry += " OR p.id = :qid"
          }

          hqlQry += ")"

          qryParams['idqstatus'] = RefdataCategory.lookup(ComponentIdentifier.RD_STATUS, ComponentIdentifier.STATUS_ACTIVE)
          qryParams['qname'] = "%${params.q}%"
        }

        qryParams['idqval'] = params.q
      }

      log.debug("Params: ${qryParams}")
    }

    // Check params for persistent properties
    PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(cls.name)

    pent.getPersistentProperties().each { p ->
      String mapped_field = MAPPED_PROPS[p.name] ?: p.name

      if (params[p.name] || params[mapped_field]) {
        log.debug("Handling persistent param prop: ${p.name}")
        String paramStr = ""
        boolean addParam = true

        if (first) {
          paramStr += " WHERE "
          first = false
        }
        else {
          paramStr += " AND "
        }
        List alts = params[p.name] ? params.list(p.name) : params.list(mapped_field)

        if ( p instanceof Association ) {
          List<Long> validLong = []
          List<String> validStr = []

          alts.each { a ->
            boolean addedLong = false

            try {
              validLong.add(Long.valueOf(a))
              addedLong = true
            }
            catch (java.lang.NumberFormatException nfe) {
            }

            if (a instanceof String && a?.trim() ) {
              if (mapped_field == 'ids') {
                validStr.add(Identifier.normalizeIdentifier(a))
              }
              else if (!addedLong) {
                validStr.add(a.toLowerCase())
              }
            }
          }

          if (p instanceof ManyToOne || p instanceof OneToOne) {
            boolean pkg_qry = false

            if (validLong.size() == 1 && p.name == 'componentToReview') {
              KBComponent ctr = KBComponent.get(validLong[0])
              List ctr_ids = [ctr.id]

              if (ctr?.class == Package) {
                String linked_select = '''select tipp.id from TitleInstancePackagePlatform as tipp
                    where tipp.pkg = :ctr
                    and exists (
                      select 1 from ReviewRequest
                      where id = p.id
                      and componentToReview = tipp
                    )'''

                qryParams['ctr'] = ctr

                if (params.titlereviews) {
                  linked_select = '''select ti.id from TitleInstance as ti
                      where exists (
                        select tipp.id from TitleInstancePackagePlatform as tipp
                        where tipp.pkg = :ctr
                        and tipp.title = ti
                      )
                      and exists (
                        select 1 from ReviewRequest
                        where id = p.id
                        and componentToReview = ti
                      )'''
                }

                paramStr += "(p.componentToReview = :ctr OR EXISTS (${linked_select}))"
                pkg_qry = true
              }
            }

            if (!pkg_qry && (validLong.size() > 0 || validStr.size() > 0)) {
              paramStr += "("
              if (validLong.size() > 0) {
                paramStr += "p.${p.name}.id IN :${p.name}"
                qryParams[p.name] = validLong
              }
              if (validStr.size() > 0) {
                if (validLong.size() > 0) {
                  paramStr += " OR "
                }

                paramStr += "p.${p.name}.${selectPreferredLabelProp(p.type)} IN :${p.name}_str"

                if (p.type.hasProperty('uuid')) {
                  paramStr += " OR p.${p.name}.uuid IN :${p.name}_str"
                }
                qryParams["${p.name}_str"] = validStr
              }
              paramStr += ")"
            }
            else if (!pkg_qry) {
              addParam = false
            }
          }
          else if (p.name == 'subjects') {
            log.debug("Handling Subjects ..")
            int idx = 0
            List subject_pars = validLong + validStr

            subject_pars.each {
              RefdataValue scheme
              def sub_obj = null

              if (it instanceof String && it.contains(';')) {
                scheme = RefdataCategory.lookup('Subject.Scheme', it.split(';')[0])

                if (scheme) {
                  sub_obj = Subject.findBySchemeAndHeading(scheme, it.split(';')[1])
                }
              }
              else {
                try {
                  sub_obj = Subject.get(it)
                }
                catch (java.lang.NumberFormatException nfe) {
                  log.debug("Received illegal value '${it}' for subjects filter!")
                }
              }


              if (sub_obj) {
                if (idx > 0) {
                  paramStr += " AND "
                }

                paramStr += "EXISTS (SELECT 1 FROM ComponentSubject where component = p AND subject = :subject${idx})"
                qryParams["subject${idx}"] = sub_obj
              }
              else if (scheme) {
                qryParams["subjectScheme${idx}"] = scheme
                qryParams["subjectHeading${idx}"] = it.split(';')[1]
                paramStr += "EXISTS (SELECT 1 FROM ComponentSubject where component = p AND subject.scheme = :subjectScheme${idx} AND subject.heading = :subjectHeading${idx})"
              }


              idx++
            }
          }
          else if (mapped_field == 'ids') {
            int idx = 0
            List id_pars = validLong + validStr

            paramStr += "("

            id_pars.each { idp ->
              if (idx > 0) {
                paramStr += " OR "
              }

              if (idp instanceof Long)  {
                paramStr += "EXISTS (SELECT 1 FROM ComponentIdentifier where component = p AND identifier.id = :idval${idx}) OR "
              }

              paramStr += "EXISTS (SELECT 1 FROM ComponentIdentifier where component = p AND ("
              paramStr += "identifier.value = :idval${idx} "
              paramStr += "OR identifier.uuid = :idval${idx} "
              paramStr += "OR identifier.normname = :idval${idx})"

              qryParams["idval${idx}"] = idp

              idx++
            }

            paramStr += ")"

          }
          else if (mapped_field == 'publisher') {
            int idx = 0
            List pub_pars = validLong + validStr

            pub_pars.each { pubp ->

              if (idx > 0) {
                paramStr += " AND "
              }

              if (idp instanceof Long)  {
                paramStr += "EXISTS (SELECT 1 FROM TitlePublisher where title = p AND publisher.id = :idval${idx})"
              }
              else {
                paramStr += "EXISTS (SELECT 1 FROM TitlePublisher where title = p AND ("

                if (idp ==~ /^[a-z0-9]{8}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{12}$/) {
                  paramStr += "publisher.uuid = :idval${idx})"
                }
                else {
                  paramStr += "lower(publisher.name) = :idval${idx} OR publisher.normname = :idval${idx})"
                }
              }

              qryParams["idval${idx}"] = idp

              idx++
            }

            paramStr += ")"
          }
          else if (p instanceof ManyToMany) {
            if (validLong.size() > 0) {
              boolean failed_lookup = false

              qryParams[p.name] = p.type.get(validLong[0])

              if (qryParams[p.name]) {
                paramStr += ":${p.name} member of p.${p.name}"
              }
              else {
                failed_lookup = true
              }

              if (validLong.size() > 1) {
                for (int i = 1; i < validLong.size(); i++) {
                  qryParams["${p.name}${i}"] = p.type.get(validLong[i])
                  paramStr += " OR :${p.name}${i} member of p.${p.name}"
                }
              }
            }
          }
        }
        else if (p.type == Long) {
          qryParams[p.name] = alts.collect { Long.valueOf(it) }
          paramStr += "p.${p.name} IN :${p.name}"
        }
        else if (p.type == Integer) {
          if (alts.size() == 1 && p.name.startsWith('start')) {
            qryParams[p.name] = Integer.parseInt(alts[0])
            paramStr += "p.${p.name} >= :${p.name}"
          }
          else if (alts.size() == 1 && p.name.startsWith('end')) {
            qryParams[p.name] = Integer.parseInt(alts[0])
            paramStr += "p.${p.name} <= :${p.name}"
          }
          else {
            qryParams[p.name] = alts.collect { Integer.parseInt(it) }
            paramStr += "p.${p.name} IN :${p.name}"
          }
        }
        else if (p.name == 'name'){
          paramStr += "lower(p.${p.name}) like lower(:${p.name})"
          qryParams[p.name] = "%${params[p.name]}%"
        }
        else if (p.name == 'url' || p.name == 'primaryUrl') {
          paramStr += "lower(p.${p.name}) like lower(:${p.name})"
          def uri_param = null

          try {
            def url = new URL(params[p.name])

            if (url.getProtocol()) {
              uri_param = url_as_name.getHost()

              if (uri_param .startsWith("www.")) {
                uri_param = uri_param.substring(4)
              }

              log.debug("New platform name is ${platformDTO.name}.")
            }
          } catch (MalformedURLException) {
            log.debug("Platform name is no valid URL")
          }

          qryParams[p.name] = "%${params[p.name]}%"
        }
        else {
          paramStr += "p.${p.name} = :${p.name}"
          qryParams[p.name] = params[p.name]
        }

        if (addParam) {
          hqlQry += paramStr
        }
      }

      if (params['_sort'] == mapped_field) {
        sortField = "p.${p.name}"
        sort = " order by p.${p.name} ${order ?: ''}"
      }
    }

    // Filter out deleted records by default.

    if (KBComponent.isAssignableFrom(cls) && !params['status']) {
      if (first) {
        hqlQry += " WHERE "
        first = false
      }
      else {
        hqlQry += " AND "
      }
      hqlQry += "p.status != :status"
      qryParams['status'] = RefdataCategory.lookup("KBComponent.Status", "Deleted")
    }

    if (cls == ReviewRequest && !params['status']) {
      if (first) {
        hqlQry += " WHERE "
        first = false
      }
      else {
        hqlQry += " AND "
      }
      hqlQry += "p.status != :status"
      qryParams['status'] = RefdataCategory.lookup("ReviewRequest.Status", "Deleted")
    }

    if (params['changedSince']) {
      LocalDateTime csdate

      try {
        csdate = GOKbTextUtils.completeDateString(params['changedSince'])
      }
      catch (Exception e) {}

      if (csdate) {
        if (first) {
          hqlQry += " WHERE "
          first = false
        }
        else {
          hqlQry += " AND "
        }
        hqlQry += "p.lastUpdated >= :changedSince"
        qryParams['changedSince'] = Date.from(csdate.atZone(ZoneOffset.UTC).toInstant())
      }
    }

    if (params['changedBefore']) {
      LocalDateTime csdate

      try {
        csdate = GOKbTextUtils.completeDateString(params['changedBefore'])
      }
      catch (Exception e) {}

      if (first) {
        hqlQry += " WHERE "
        first = false
      }
      else {
        hqlQry += " AND "
      }
      hqlQry += "p.lastUpdated < :changedBefore"
      qryParams['changedBefore'] = Date.from(csdate.atZone(ZoneOffset.UTC).toInstant())
    }

    if (params['createdSince']) {
      LocalDateTime csdate

      try {
        csdate = GOKbTextUtils.completeDateString(params['createdSince'])
      }
      catch (Exception e) {}

      if (csdate) {
        if (first) {
          hqlQry += " WHERE "
          first = false
        }
        else {
          hqlQry += " AND "
        }
        hqlQry += "p.dateCreated >= :createdSince"
        qryParams['createdSince'] = Date.from(csdate.atZone(ZoneOffset.UTC).toInstant())
      }
    }

    if (params['createdBefore']) {
      LocalDateTime csdate

      try {
        csdate = GOKbTextUtils.completeDateString(params['createdBefore'])
      }
      catch (Exception e) {}

      if (csdate) {
        if (first) {
          hqlQry += " WHERE "
          first = false
        }
        else {
          hqlQry += " AND "
        }
        hqlQry += "p.dateCreated < :createdBefore"
        qryParams['createdBefore'] = Date.from(csdate.atZone(ZoneOffset.UTC).toInstant())
      }
    }

    if (params['id']) {
      Long idval = params.long('id')

      if (first) {
        hqlQry += " WHERE "
        first = false
      }
      else {
        hqlQry += " AND "
      }
      hqlQry += "p.id = :idval"
      qryParams['idval'] = idval
    }

    if (cls == ReviewRequest && params['allocatedGroups']) {
      def cgs = params.list('allocatedGroups')
      def inactive = RefdataCategory.lookupOrCreate('AllocatedReviewGroup.Status', 'Inactive')
      def validCgs = []

      cgs.each { cg ->
        try {
          validCgs.add(CuratoryGroup.get(Long.valueOf(cg)))
        }
        catch (java.lang.NumberFormatException nfe) {
          log.debug("Received illegal value ${cg}' for curatoryGroups filter!")
        }
      }

      if (validCgs.size() > 0 ) {
        log.debug("Filtering for CGs: ${validCgs}")
        if (first) {
          hqlQry += " WHERE "
          first = false
        }
        else {
          hqlQry += " AND "
        }

        if (params['escalatedOnly']) {
          hqlQry += '''exists (
                        select 1 from AllocatedReviewGroup as ag
                        where ag.review = p
                        and ag.group IN :alg
                        and ag.status != :inactive
                        and ag.escalatedFrom is not null
                      )'''
        }
        else {
          hqlQry += '''exists (
                        select 1 from AllocatedReviewGroup as ag
                        where ag.review = p
                        and ag.group IN :alg
                        and ag.status != :inactive
                      )'''
        }

        qryParams['alg'] = validCgs
        qryParams['inactive'] = inactive
      }
    }

    if (cls == ReviewRequest && params['linkedComponentType']) {
      def lct = params['linkedComponentType']

      if (['Package', 'ReferenceTitle', 'PackageTitle', 'Journal', 'Monograph', 'Database'].contains(lct)) {
        if (first) {
          hqlQry += " WHERE "
          first = false
        }
        else {
          hqlQry += " AND "
        }

        if (lct == 'Package') {
          hqlQry += "exists (select 1 from Package where id = p.componentToReview.id)"
        }
        else if (lct == 'ReferenceTitle') {
          hqlQry += "exists (select 1 from TitleInstance where id = p.componentToReview.id)"
        }
        else if (lct == 'PackageTitle') {
          hqlQry += "exists (select 1 from TitleInstancePackagePlatform where id = p.componentToReview.id)"
        }
        else if (lct == 'Journal') {
          hqlQry += '''(
                        exists (
                          select 1 from JournalInstance
                          where id = p.componentToReview.id
                        )
                        or exists (
                          select 1 from TitleInstancePackagePlatform
                          where id = p.componentToReview.id
                          and publicationType = :ctrpubtype
                        )
                      )'''

          qryParams['ctrpubtype'] = RefdataCategory.lookup('TitleInstancePackagePlatform.PublicationType', 'Serial')
        }
        else if (lct == 'Monograph') {
          hqlQry += '''(
                        exists (
                          select 1 from BookInstance
                          where id = p.componentToReview.id
                        )
                        or exists (
                          select 1 from TitleInstancePackagePlatform
                          where id = p.componentToReview.id
                          and publicationType = :ctrpubtype
                        )
                      )'''

          qryParams['ctrpubtype'] = RefdataCategory.lookup('TitleInstancePackagePlatform.PublicationType', 'Monograph')
        }
        else if (lct == 'Database') {
          hqlQry += '''(
                        exists (
                          select 1 from DatabaseInstance
                          where id = p.componentToReview.id
                        )
                        or exists (
                          select 1 from TitleInstancePackagePlatform
                          where id = p.componentToReview.id
                          and publicationType = :ctrpubtype
                        )
                      )'''

          qryParams['ctrpubtype'] = RefdataCategory.lookup('TitleInstancePackagePlatform.PublicationType', 'Database')
        }
      }
      else {
        log.debug("Skipping linkedCOmponentType ${lct}!")
      }
    }

    String hqlCount = "select count(p.id) ${hqlQry}".toString()
    String hqlFinal = "select p ${sortField ? ', ' + sortField : ''} ${hqlQry} ${sort ?: ''}".toString()

    log.debug("Final qry: ${hqlFinal}")

    int hqlTotal = cls.executeQuery(hqlCount, qryParams,[:])[0]
    List hqlResult = cls.executeQuery(hqlFinal, qryParams, [max: max, offset: offset, readOnly: true])

    result.data = []

    hqlResult.each { r ->
      log.debug("Handling ${r} (${r.class.name}) -- Total: ${hqlTotal}")
      Object obj = null

      if (r instanceof Object[]) {
        obj = r[0]
      }
      else {
        obj = r
      }

      log.debug("${obj}")
      result.data << (idOnly ? obj.id : restMappingService.mapObjectToJson(obj, params, user))
    }

    result['_pagination'] = [
      offset: offset,
      limit: max,
      total: hqlTotal
    ]

    generateLinks(result, cls, context, params, max, offset, hqlTotal)

    result
  }

  /**
   *  selectPreferredLabelProp : Determines the correct label property for a specific class.
   * @param cls : The class to be examined
   */

  private String selectPreferredLabelProp(cls) {
    def obj_label = null
    def cls_inst = cls.newInstance()

    if (cls_inst.hasProperty('username')) {
      obj_label = "username"
    }
    else if (cls_inst.hasProperty('value')) {
      obj_label = "value"
    }
    else if (cls_inst.hasProperty('name')) {
      obj_label = "name"
    }
    else if (cls_inst.hasProperty('variantName')) {
      obj_label = "variantName"
    }

    return obj_label
  }

  /**
   *  generateLinks : Generates pagination links for the JSON response.
   * @param result : The result object
   * @param cls : The class of returned objects
   * @param context : Possible override of the generated link path
   * @param max : Maximum items per page
   * @param offset : Result offset
   * @param total : Number of total results
   */

  public void generateLinks(Map result, Class cls, String context, def params, int max, int offset, int total) {
    String endpoint = ""

    if (context) {
      endpoint = context
    }
    else if (cls == KBComponent) {
      endpoint = "/entities"
    }
    else if (cls.newInstance().hasProperty('restPath')) {
      endpoint = cls.newInstance().restPath
    }

    log.debug("Identified endpoint: ${endpoint}")

    result['_links'] = [:]
    result['_links']['self'] = [href: restMappingService.buildUrlString(endpoint, null, offset, max, params)]


    if (total > offset + max) {
      result['_links']['next'] = [href: restMappingService.buildUrlString(endpoint, 'next', offset, max, params)]
    }
    if (offset > 0) {
      result['_links']['prev'] = [href: restMappingService.buildUrlString(endpoint, 'prev', offset, max, params)]
    }
  }

  public CuratoryGroup findCuratoryGroupOfInterest(KBComponent component, User user = null, def activeGroup = null) {
    CuratoryGroup activeCuratoryGroup = null

    if (activeGroup instanceof CuratoryGroup) {
      activeCuratoryGroup = activeGroup
    }
    else if (activeGroup instanceof String) {
      activeCuratoryGroup = CuratoryGroup.findByName(activeGroup)
    }
    else if (activeGroup instanceof Integer || activeGroup instanceof Long) {
      activeCuratoryGroup = CuratoryGroup.findById(activeGroup)
    }
    else if (activeGroup instanceof Map) {
      if (activeGroup?.uuid){
        activeCuratoryGroup = CuratoryGroup.findByUuid(activeGroup.uuid)
      }
      else if (activeGroup?.id){
        activeCuratoryGroup = CuratoryGroup.findById(activeGroup.id)
      }
      else if (activeGroup?.name){
        activeCuratoryGroup = CuratoryGroup.findByName(activeGroup.name)
      }
    }

    KBComponent curated_component = KBComponent.has(component, 'curatoryGroups') ? component : (component.class == TitleInstancePackagePlatform ? component.pkg : null)

    if (!curated_component) {
      String component_classname = component.class.getSimpleName()
      Map centralGroups = grailsApplication.config.getProperty("gokb.centralGroups", Map)
      String central_group_name = centralGroups[component_classname]

      // log.debug("findCuratoryGroupOfInterest :: Central groups: ${grailsApplication.config.getProperty("gokb.centralGroups", Map)}")

      // log.debug("findCuratoryGroupOfInterest :: Central group for $component_classname : $central_group_name ..")

      if (activeCuratoryGroup?.superordinatedGroup && component_classname in ['JournalInstance', 'BookInstance', 'DatabaseInstance', 'OtherInstance']) {
        return activeCuratoryGroup.superordinatedGroup
      }
      if (central_group_name) {
        CuratoryGroup cg = CuratoryGroup.findByNameIlike(central_group_name)
        return cg
      }
      else if (activeCuratoryGroup) {
        return activeCuratoryGroup
      }
      else {
        return null
      }
    }

    if (curated_component.curatoryGroups?.size() == 1) {
      CuratoryGroup cg = CuratoryGroup.get(curated_component.curatoryGroups[0].id)
      return cg
    }
    else if (curated_component.curatoryGroups.size() > 1) {
      if (curated_component.curatoryGroups.contains(activeCuratoryGroup)) {
        return activeCuratoryGroup
      }
      else if (component.curatoryGroups.intersect(user?.curatoryGroups)) {
        CuratoryGroup cg = CuratoryGroup.get(intersection[0].id)
        return cg
      }
    }
    else if (curated_component.curatoryGroups?.size() == 0) {
      if (activeCuratoryGroup) {
        return activeCuratoryGroup
      }
      else if (user?.curatoryGroups?.size() == 1){
        CuratoryGroup cg = CuratoryGroup.get(user.curatoryGroups[0].id)
        return cg
      }
    }

    return null
  }
}
