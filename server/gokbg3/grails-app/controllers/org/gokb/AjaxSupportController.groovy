package org.gokb

import grails.converters.JSON
import java.text.SimpleDateFormat

import com.k_int.ClassUtils

import org.gokb.cred.*

import org.springframework.security.access.annotation.Secured;
import grails.util.GrailsNameUtils
import grails.core.GrailsClass
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*
import grails.validation.ValidationException

class AjaxSupportController {

  def genericOIDService
  def aclUtilService
  def springSecurityService
  def messageSource


  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def edit() {
    // edit [name:name, value:project:12, pk:org.gokb.cred.Package:2950, action:edit, controller:ajaxSupport]
    log.debug("edit ${params}");
    def result = [:]

    try {
      if ( params.pk ) {
        def target = genericOIDService.resolveOID(params.pk)
        if ( target ) {
          target[params.name] = params.value
          target.save(flush:true)
        }

        pk_components = pk.split(':')
        if ( pk_components.length == 2 ) {
        }
      }
    }
    catch ( Exception e ) {
      log.error(e)
    }

    render result as JSON
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def setRef() {
    def result = [:]
    render result as JSON
  }


  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def getRefdata() {
    log.debug("AjaxController::getRefdata ${params}")

    def result = []

    def config = refdata_config[params.id]

    if (!config) {
      log.debug("Use generic config.")

      config = [
      domain:'RefdataValue',
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?",
      rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=? order by rdv.sortKey asc, rdv.description asc",
      rdvCat: "${params.id}",
      qryParams:[],
      cols:['value'],
      format:'simple'
      ]
    }

    if ( config ) {
      def query_params = [config.rdvCat]

      config.qryParams.each { qp ->
        if ( qp.clos ) {
          query_params.add(qp.clos(params[qp.param]?:''));
        }
        else {
          query_params.add(params[qp.param] ?: qp.cat);
        }
      }

      log.debug("Params: ${query_params}");
      log.debug("Count qry: ${config.countQry}");
      log.debug("Row qry: ${config.rowQry}");
      log.debug("DOMAIN: ${config.domain}");

      GrailsClass dc = grailsApplication.getArtefact("Domain", 'org.gokb.cred.'+ config.domain)

      def cq = dc.getClazz().executeQuery(config.countQry,query_params);
      def rq = dc.getClazz().executeQuery(config.rowQry,
                                query_params,
                                [max:params.iDisplayLength?:400,offset:params.iDisplayStart?:0]);

      rq.each { it ->
        def o = ClassUtils.deproxy(it)
        result.add([id:"${o.class.name}:${o.id}", text: o[config.cols[0]], value:o[config.cols[0]]]);
      }
    }

    render result as JSON
  }


  def refdata_config = [
    'ContentProvider' : [
      domain:'Org',
      countQry:'select count(o) from Org as o where lower(o.name) like ?',
      rowQry:'select o from Org as o where lower(o.name) like ? order by o.name asc',
      qryParams:[
    [
      param:'sSearch',
      clos:{ value ->
      def result = '%'
      if ( value && ( value.length() > 0 ) )
        result = "%${value.trim().toLowerCase()}%"
      result
      }
    ]
    ],
      cols:['name'],
      format:'map'
    ],
    'PackageType' : [
      domain:'RefdataValue',
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?",
      rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?",
      qryParams:[['cat': "Package Type"]],
      rdvCat: "${params.id}",
      cols:['value'],
      format:'simple'
    ],
    'KBComponent.Status' : [
      domain:'RefdataValue',
      // countQry:"select count(rdv) from RefdataValue as rdv where rdv.owner.desc='KBComponent.Status' and rdv.value !='${KBComponent.STATUS_DELETED}'",
      // rowQry:"select rdv from RefdataValue as rdv where rdv.owner.desc='KBComponent.Status' and rdv.value !='${KBComponent.STATUS_DELETED}'",
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?",
      rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?",
      qryParams:[],
      rdvCat: "KBComponent.Status",
      cols:['value'],
      format:'simple'
    ],
    'VariantNameType' : [
      domain:'RefdataValue',
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?",
      rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?",
      qryParams:[],
      rdvCat: "KBComponentVariantName.VariantType",
      cols:['value'],
      format:'simple'
    ],
    'KBComponentVariantName.VariantType' : [
      domain:'RefdataValue',
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?",
      rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?",
      qryParams:[],
      rdvCat: "KBComponentVariantName.VariantType",
      cols:['value'],
      format:'simple'
    ],
    'Locale' : [
      domain:'RefdataValue',
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?",
      rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?",
      qryParams:[],
      rdvCat: "KBComponentVariantName.Locale",
      cols:['value'],
      format:'simple'
    ],
    'ReviewRequest.Status' : [
      domain:'RefdataValue',
      // countQry:"select count(rdv) from RefdataValue as rdv where rdv.owner.desc='KBComponent.Status' and rdv.value !='${KBComponent.STATUS_DELETED}'",
      // rowQry:"select rdv from RefdataValue as rdv where rdv.owner.desc='KBComponent.Status' and rdv.value !='${KBComponent.STATUS_DELETED}'",
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?",
      rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?",
      qryParams:[],
      rdvCat: "ReviewRequest.Status",
      cols:['value'],
      format:'simple'
    ]
  ]



  /**
   *  addToCollection : Used to create a form which will add a new object to a named collection within the target object.
   * @param __context : the OID ("<FullyQualifiedClassName>:<PrimaryKey>") Of the context object
   * @param __newObjectClass : The fully qualified class name of the instance to create
   * @param __recip : Optional - If set, then new_object.recip will point to __context
   * @param __addToColl : The name of the local set to which the new object should be added
   * @param All other parameters are taken to be property names on newObjectClass and used to init the new instance.
   */
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def addToCollection() {
    log.debug("AjaxController::addToCollection ${params}");
    User user = springSecurityService.currentUser
    def contextObj = resolveOID2(params.__context)
    def errors = false
    GrailsClass domain_class = grailsApplication.getArtefact('Domain',params.__newObjectClass)

    if ( domain_class && (domain_class.getClazz().isTypeCreatable() || domain_class.getClazz().isTypeAdministerable()) ) {

      if ( contextObj && contextObj.isEditable() ) {
        log.debug("Create a new instance of ${params.__newObjectClass}");

        if(params.__newObjectClass == "org.gokb.cred.KBComponentVariantName"){

          def norm_variant = GOKbTextUtils.normaliseString(params.variantName)
          def existing_variants = KBComponentVariantName.findByNormVariantNameAndOwner(norm_variant, contextObj)

          if(existing_variants){
            log.debug("found dupes!")
            flash.message = message(code:'variantName.value.notUnique')
            errors = true
          }else{
            log.debug("create new variantName")
          }
        }

        if(params.__newObjectClass == "org.gokb.cred.TitleInstancePackagePlatform") {
          flash.message = []

          if (!params.title || params.title.size() == 0) {
            log.debug("missing title for TIPP creation")
            flash.message.add(message(code:'tipp.title.nullable'))
            errors = true
          }

          if (!params.hostPlatform || params.hostPlatform.size() == 0) {
            flash.message.add(message(code:'tipp.hostPlatform.nullable'))
            log.debug("missing platform for TIPP creation")
            errors = true
          }

          if(!params.url || params.url.size() == 0) {
            flash.message.add(message(code:'tipp.url.nullable'))
            log.debug("missing url for TIPP creation")
            errors = true
          }
        }

        if(!errors) {
          def new_obj = domain_class.getClazz().newInstance();
          PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(domain_class.fullName)

          pent.getPersistentProperties().each { p -> // list of PersistentProperties
            log.debug("${p.name} (assoc=${p instanceof Association}) (oneToMany=${p instanceof OneToMany}) (ManyToOne=${p instanceof ManyToOne}) (OneToOne=${p instanceof OneToOne})");
            if ( params[p.name] ) {
              if ( p instanceof Association ) {
                if ( p instanceof ManyToOne || p instanceof OneToOne ) {
                  // Set ref property
                  log.debug("set assoc ${p.name} to lookup of OID ${params[p.name]}");
                  // if ( key == __new__ then we need to create a new instance )
                  new_obj[p.name] = resolveOID2(params[p.name])
                }
                else {
                  // Add to collection
                  log.debug("add to collection ${p.name} for OID ${params[p.name]}");
                  new_obj[p.name].add(resolveOID2(params[p.name]))
                }
              }
              else {
                log.debug("checking for type of property -> ${p.type}")
                switch ( p.type ) {
                  case Long.class:
                    log.debug("Set simple prop ${p.name} = ${params[p.name]} (as long=${Long.parseLong(params[p.name])})");
                    new_obj[p.name] = Long.parseLong(params[p.name]);
                    break;

                  case Date.class:
                    def dateObj = params.date(p.name, 'yyyy-MM-dd')
                    new_obj[p.name] = dateObj
                    log.debug("Set simple prop ${p.name} = ${params[p.name]} (as date ${dateObj}))");
                    break;
                  default:
                    log.debug("Default for type ${p.type}")
                    log.debug("Set simple prop ${p.name} = ${params[p.name]}");
                    new_obj[p.name] = params[p.name]
                    break;
                }
              }
            }
          }

          if (params.__refdataName && params.__refdataValue) {
            log.debug("set refdata "+ params.__refdataName +" for component ${contextObj}")
            def refdata = resolveOID2(params.__refdataValue)
            new_obj[params.__refdataName] = refdata
          }

          // Need to do the right thing depending on who owns the relationship. If new obj
          // BelongsTo other, should be added to recip collection.
          if ( params.__recip ) {
            log.debug("Set reciprocal property ${params.__recip} to ${contextObj}");
            new_obj[params.__recip] = contextObj
            log.debug("Saving ${new_obj}");
            if ( new_obj.save(flush:true) ) {
              log.debug("Saved OK");
              if (contextObj.respondsTo("lastUpdateComment")){
                contextObj.lastUpdateComment = "Added new connected ${new_obj.class.simpleName}(ID: ${new_obj.id})."
              }
              contextObj.save(flush: true)
            }
            else {
              new_obj.errors.each { e ->
                log.debug("Problem ${e}");
              }
            }
          }
          else if ( params.__addToColl ) {
            contextObj[params.__addToColl].add(new_obj)
            log.debug("Saving ${new_obj}");

            if ( new_obj.save(flush:true) ) {
              log.debug("New Object Saved OK");
            }
            else {
              new_obj.errors.each { e ->
                log.debug("Problem ${e}");
              }
            }

            if ( contextObj.save(flush:true) ) {
              log.debug("Context Object Saved OK");
            }
            else {
              contextObj.errors.each { e ->
                log.debug("Problem ${e}");
              }
            }
          }
          else {
            // Stand alone object.. Save it!
            log.debug("Saving stand alone reference object");
            if ( new_obj.save(flush:true, failOnError:true) ) {
              log.debug("Saved OK (${new_obj.class.name} ${new_obj.id})");
            }
            else {
              new_obj.errors.each { e ->
                log.debug("Problem ${e}");
              }
            }
          }

          // Special combo processing
          if ( ( new_obj != null ) &&
              ( new_obj.hasProperty('hasByCombo') ) && ( new_obj.hasByCombo != null ) ) {
            log.debug("Processing hasByCombo properties...${new_obj.hasByCombo}");
            new_obj.hasByCombo.keySet().each { hbc ->
              log.debug("Testing ${hbc} -> ${params[hbc]}");
              if ( params[hbc] ) {
                log.debug("Setting ${hbc} to ${params[hbc]}");
                new_obj[hbc] = resolveOID2(params[hbc])
              }
            }
            new_obj.save(flush:true, failOnError:true)
          }
        }
        else {
          log.debug("could not add to collection!")
          if(!flash.message) {
            flash.message = "Unable to add component!"
          }
        }
      }
      else if (!contextObj) {
        log.debug("Unable to locate instance of context class with oid ${params.__context}");
      }
      else {
        log.debug("Located instance of context class with oid ${params.__context} is not editable.");
      }
    }
    else {
      if(!domain_class) {
        log.error("Unable to lookup domain class ${params.__newObjectClass}");
      }else{
        log.error("Unable to create domain class ${params.__newObjectClass}");
      }
    }

    redirect(url: request.getHeader('referer'))
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def addToStdCollection() {
    log.debug("addToStdCollection(${params})");
    // Adds a link to a collection that is not mapped through a join object
    def contextObj = resolveOID2(params.__context)
    if ( contextObj && (contextObj.isEditable() || springSecurityService.currentUser == contextObj)) {
      if (!contextObj["${params.__property}"].contains(resolveOID2(params.__relatedObject))) {
        contextObj["${params.__property}"].add (resolveOID2(params.__relatedObject))
        contextObj.save(flush:true, failOnError:true)
        log.debug("Saved: ${contextObj.id}");
      }else{
        log.debug("Tried to add the same object twice!")
      }
    }
    else {
      log.debug("context object not found, or not editable.")
    }
    redirect(url: request.getHeader('referer'))
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def unlinkManyToMany() {
    log.debug("unlinkManyToMany(${params})");
    // Adds a link to a collection that is not mapped through a join object
    def contextObj = resolveOID2(params.__context)
    if ( contextObj && contextObj.isEditable()) {
      def item_to_remove = resolveOID2(params.__itemToRemove)
      if ( item_to_remove ) {
        if ( ( item_to_remove != null ) && ( item_to_remove.hasProperty('hasByCombo') ) && ( item_to_remove.hasByCombo != null ) ) {
          item_to_remove.hasByCombo.keySet().each { hbc ->
            log.debug("Testing ${hbc}");
            log.debug("here's the data: "+ item_to_remove[hbc])
            if (item_to_remove[hbc]==contextObj) {
              log.debug("context found");
              //item_to_remove[hbc]=resolveOID2(null)
              if(item_to_remove.respondsTo('deleteParent')) {
                log.debug("deleteParent()")
                item_to_remove.deleteParent();
              }
              log.debug("tried removal: "+item_to_remove[hbc]);
            }
          }
        }
        log.debug("${params}");
        log.debug("removing: "+item_to_remove+" from "+params.__property+" for "+contextObj);

        def remove_result = contextObj[params.__property].remove(item_to_remove);

        log.debug("remove successful?: ${remove_result}")
        log.debug("child ${item_to_remove} removed: "+ contextObj[params.__property]);

        def new_cobj = contextObj.save(flush: true, failOnError: true)

        if (!new_cobj) {
          log.debug("${contextObj.errors.allErrors()}")
        } else {
          log.debug("saved ok (${new_cobj[params.__property]})");
        }

        if (item_to_remove.hasProperty('fromComponent') && item_to_remove.fromComponent == contextObj) {
          item_to_remove.delete(flush:true)
        }
        else {
          item_to_remove.refresh();

          if (params.__otherEnd && item_to_remove[params.__otherEnd]!=null) {
            log.debug("remove parent: "+item_to_remove[params.__otherEnd])
            //item_to_remove.setParent(null);
            item_to_remove[params.__otherEnd]=null; //this seems to fail
            log.debug("parent removed: "+item_to_remove[params.__otherEnd]);
          }
          if (item_to_remove.save()==false) {
            log.debug("${item_to_remove.errors.allError()}");
          }
        }
      } else {
        log.error("Unable to resolve item to remove : ${params.__itemToRemove}");
      }
    }
    else if (!contextObj) {
      log.debug("Unable to locate instance of context class with oid ${params.__context}");
    }
    else {
      log.debug("Located instance of context class with oid ${params.__context} is not editable.");
    }
    redirect(url: request.getHeader('referer'))
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def delete() {
    log.debug("delete(${params}), referer: ${request.getHeader('referer')}");
    // Adds a link to a collection that is not mapped through a join object
    def contextObj = resolveOID2(params.__context)
    if ( contextObj && contextObj.isDeletable()) {
      contextObj.delete(flush:true)
    }
    else if (!contextObj) {
      log.debug("Unable to locate instance of context class with oid ${params.__context}");
    }
    else {
      log.debug("Located instance of context class with oid ${params.__context} is not editable.");
    }

    def redirect_to = request.getHeader('referer')

    if ( params.redirect ) {
      redirect_to = params.redirect
    }
    else if ( ( params.fragment ) && ( params.fragment.length() > 0 ) ) {
      redirect_to = "${redirect_to}#${params.fragment}"
    }

    redirect(url: redirect_to)
  }

  def resolveOID2(oid) {
    def oid_components = oid.split(':');
    def result = null;
    def domain_class=null;
    domain_class = grailsApplication.getArtefact('Domain',oid_components[0])
    if ( domain_class ) {
      if ( oid_components[1]=='__new__' ) {
        result = domain_class.getClazz().refdataCreate(oid_components)
        log.debug("Result of create ${oid} is ${result}");
      }
      else {
        result = domain_class.getClazz().get(oid_components[1])
      }
    }
    else {
      log.error("resolve OID failed to identify a domain class. Input was ${oid_components}");
    }
    result
  }


  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def lookup() {
    log.debug("AjaxController::lookup ${params}");
    def result = [:]
    params.max = params.max ?: 10;
    def domain_class = grailsApplication.getArtefact('Domain',params.baseClass)
    if ( domain_class && domain_class.getClazz().isTypeReadable() ) {
      result.values = domain_class.getClazz().refdataFind(params);
    }
    else {
      log.error("Unable to locate domain class ${params.baseClass} or not readable");
      result.values=[]
    }
    //result.values = [[id:'Person:45',text:'Fred'],
    //                 [id:'Person:23',text:'Jim'],
    //                 [id:'Person:22',text:'Jimmy'],
    //                 [id:'Person:3',text:'JimBob']]

    if ( params.addEmpty=='Y' || params.addEmpty=='y' ) {
      result.values.add(0, [id:'', text:'']);
    }

    render result as JSON
  }


  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def editableSetValue() {
    log.debug("editableSetValue ${params}");
    def target_object = resolveOID2(params.pk)
    def user = springSecurityService.currentUser
    def errors = null
    if ( target_object && ( target_object.isEditable() || target_object == user ) ) {
      if ( params.type=='date' ) {
        target_object."${params.name}" = params.date('value',params.format ?: 'yyyy-MM-dd')
      }
      else {
        def binding_properties = [:]
        binding_properties[ params.name ] = params.value
        bindData(target_object, binding_properties)
      }
      
      if (target_object.validate()) {
        target_object.save(flush:true);
      }
      else {

        errors = []

        target_object.errors.allErrors.each { eo ->

          String[] messageArgs = eo.getArguments()
          def errorMessage = null

          log.debug("Found error with args: ${messageArgs}")

          eo.getCodes().each { ec ->

            if (!errorMessage) {
              log.debug("testing code -> ${ec}")

              def msg = messageSource.resolveCode(ec, request.locale)?.format(messageArgs)

              if(msg && msg != ec) {
                errorMessage = msg
              }

              if(!errorMessage) {
                log.debug("Could not resolve message")
              }else{
                log.debug("found message: ${msg}")
              }
            }
          }

          if (errorMessage) {
            errors.add(errorMessage)
          }else{
            log.debug("Found no message for error code ${eo}")
          }
        }

      }
    }
    else {
      log.debug("Object ${target_object} is not editable.");
    }

    response.setContentType('text/plain')
    def outs = response.outputStream
    if (!errors) {
      outs << params.value
    }
    else {
      response.status = 400
      outs << errors[0]
    }
    outs.flush()
    outs.close()
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def genericSetRel() {
    // [id:1, value:JISC_Collections_NESLi2_Lic_IOP_Institute_of_Physics_NESLi2_2011-2012_01012011-31122012.., type:License, action:inPlaceSave, controller:ajax
    // def clazz=grailsApplication.domainClasses.findByFullName(params.type)
    log.debug("genericSetRel ${params}");

    def target=genericOIDService.resolveOID(params.pk)
    def value=genericOIDService.resolveOID(params.value)

    def result = null

    if ( target != null && target.isEditable()) {
      // def binding_properties = [ "${params.name}":value ]
      log.debug("Binding: ${params.name} into ${target} - a ${target.class.name}");
      // bindData(target, binding_properties)
      target[params.name] = value
      log.debug("Saving... after assignment ${params.name} = ${target[params.name]}");
      if ( target.save(flush:true) ) {

        if ( params.resultProp ) {
          result = value ? value[params.resultProp] : ''
        }

        // We should clear the session values for a user if this is a user to force reload of the,
        // parameters.
        if (target instanceof User) {
          session.userPereferences = null
        }
        else {
          if ( value ) {
            result = renderObjectValue(value);
            // result = value.toString()
          }
        }
      }
      else {
        log.error("Problem saving.. ${target.errors}");
        result="ERROR"
      }
    }
    else {
      log.error("no type (target=${params.pk}, value=${params.value}) or not editable");
    }

    def resp = [ newValue: target[params.name] ]
    log.debug("return ${resp}");
    render resp as JSON
  }

  def renderObjectValue(value) {
    def result=''
    if ( value ) {
      switch ( value.class ) {
        case org.gokb.cred.RefdataValue.class:
          if ( value.icon != null ) {
            result="<span class=\"select-icon ${value.icon}\"></span>${value.value}"
          }
          else {
            result=value.value
          }
          break;
        default:
          result=value.toString();
      }
    }
    result;
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def addIdentifier() {
    log.debug("addIdentifier - ${params}");
    // Check identifier namespace present, and identifier value valid for that namespace
    if ( ( params.identifierNamespace?.length() > 0 ) &&
         ( params.identifierValue?.length() > 0 ) &&
         ( params.__context?.length() > 0 ) ) {
      def ns = genericOIDService.resolveOID(params.identifierNamespace)
      def owner = genericOIDService.resolveOID(params.__context)
      if ( ( ns != null ) && ( owner != null ) && owner.isEditable() ) {
        // Lookup or create Identifier
        try {
          def identifier_instance = Identifier.lookupOrCreateCanonicalIdentifier(ns.value, params.identifierValue)

          if (identifier_instance) {

            log.debug("Got ID: ${identifier_instance}")
            // Link if not existing
            owner.ids.add(identifier_instance);
            owner.save(flush:true);
          }
        } catch (grails.validation.ValidationException ve) {

          log.debug("${ve}")
          flash.message = message(code:'identifier.value.IllegalIDForm')
        }
      }else{
        log.debug("could not create identifier!")
      }
    }
    log.debug("Redirecting to referer: ${request.getHeader('referer')}");
    redirect(url: (request.getHeader('referer')+params.hash?:''))
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def appliedCriterion() {
    log.debug("applied criterion AJAXSupportController - ${params} ");
    def result = [status:'OK']

    // val:r, comp:139862, crit:1
    def component = KBComponent.get(params.comp);
    def crit      = DSCriterion.get(params.crit);
    def lookup    = [ 'r' : 'Red', 'a' : 'Amber', 'g' : 'Green' ]
    def rdv       = RefdataCategory.lookupOrCreate('RAG', lookup[params.val]).save()
    def user      = springSecurityService.currentUser

    def current_applied = DSAppliedCriterion.findByUserAndAppliedToAndCriterion(user,component,crit);
    if ( current_applied == null ) {
      log.debug("Create new applied criterion");
      current_applied = new DSAppliedCriterion(user: user, appliedTo:component, criterion:crit, value: rdv).save(flush: true, failOnError:true)
    }
    else {
      log.debug("Update existing vote");
      current_applied.value=rdv
      current_applied.save(flush: true, failOnError:true)
    }
    result.username = user.username
    render result as JSON
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def criterionComment() {
    log.debug('CRITERION COMMENT HAS BEEN CALLED!:'+params);
    log.debug('criterionComment:'+params);
    def result    = [:]
    result.status = 'OK'
    def idparts   = params.comp.split('_');
    log.debug(idparts);
    if ( idparts.length == 2 ) {
      def component = KBComponent.get(idparts[0]);
      def crit      = DSCriterion.get(idparts[1]);

      def user = springSecurityService.currentUser
      def current_applied = DSAppliedCriterion.findByUserAndAppliedToAndCriterion(user,component,crit);

      if ( current_applied == null ) {
        // Create a new applied criterion to comment on
        def rdv  = RefdataCategory.lookupOrCreate('RAG', 'Unknown');
        current_applied = new DSAppliedCriterion(user: user, appliedTo:component, criterion:crit, value: rdv).save(failOnError:true)
      }

      def note = new DSNote(criterion:current_applied, note:params.comment, isDeleted:false).save(failOnError:true);
      result.newNote  = note.id
      result.created  = note.dateCreated
      log.debug("Found applied critirion ${current_applied} for ${idparts[0]} ${idparts[1]} ${component} ${crit}");
    }
    render result as JSON
  }


  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def criterionCommentDelete() {
    log.debug('criterionCommentDelete:'+params);
    def result       = [:]
    result.status    = 'OK'
    def user         = springSecurityService.currentUser
    def note         = DSNote.get(params.note)
    if (note)
    {
        if (note.criterion.user?.id == user?.id)
            note.isDeleted       = true
        else
            result.status = '401'
    }

    render result as JSON
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def plusOne() {
    log.debug("plusOne ${params}");
    def result       = [:]
    def user         = springSecurityService.currentUser
    def oid = params.object
    if (oid) {
      def oid_components = oid.split(':');

      log.debug("oid_components:${oid_components}");

       if ( oid_components.length == 2 ) {
         def existing_like = ComponentLike.executeQuery('select cl from ComponentLike as cl where cl.ownerClass=:oc and cl.ownerId=:oi and cl.user=:u',
                             [oc:oid_components[0], oi:Long.parseLong(oid_components[1]), u:user]);
         switch ( existing_like.size() ) {
           case 0:
             log.debug("Like");
             new ComponentLike(ownerClass:oid_components[0], ownerId:Long.parseLong(oid_components[1]), user:user).save(flush:true, failOnError:true)
             break;
           case 1:
             log.debug("UnLike");
             existing_like.get(0).delete(flush:true, failOnError:true)
             break;
           default:
             break;
         }
       }
       
       result.status = 'OK'
       result.newcount = ComponentLike.executeQuery('select count(cl) from ComponentLike as cl where cl.ownerClass=:oc and cl.ownerId=:oi',
                             [oc:oid_components[0], oi:Long.parseLong(oid_components[1])]).get(0)


    }
    log.debug("result: ${result}");
    render result as JSON
  }
}
