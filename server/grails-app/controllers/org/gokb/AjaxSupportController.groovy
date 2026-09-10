package org.gokb

import grails.converters.JSON
import java.text.SimpleDateFormat
import java.text.MessageFormat

import com.k_int.ClassUtils

import org.gokb.cred.*

import org.springframework.security.access.annotation.Secured;
import grails.gorm.transactions.Transactional
import grails.util.GrailsNameUtils
import grails.core.GrailsClass
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*
import grails.validation.ValidationException

class AjaxSupportController {

  def genericOIDService
  def aclUtilService
  def springSecurityService
  def componentLookupService
  def messageSource
  def messageService
  def validationService


  def setRef() {
    def result = [:]
    render result as JSON
  }

  /**
   *  getRefdata : Used to retrieve a list of all RefdataValues for a specific category.
   * @param id : The label of the RefdataCategory
   */

  def getRefdata() {
    log.debug("AjaxController::getRefdata ${params}")
    List result = []
    Map config = refdata_config[params.id]

    if (!config) {
      log.debug("Use generic config.")

      config = [
        domain:'RefdataValue',
        countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc = ?0",
        rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc = ?0 order by rdv.sortKey asc, rdv.description asc",
        rdvCat: "${params.id}",
        qryParams:[],
        cols:['value'],
        format:'simple'
      ]
    }

    if ( params.id == 'boolean' ) {
      result.add([text:'Yes', value: 1])
      result.add([text:'No', value: 0])
    } else {
      List query_params = [config.rdvCat.toString()]

      config.qryParams.each { qp ->
        if (qp.clos) {
          query_params.add(qp.clos(params[qp.param]?:'').toString())
        }
        else {
          query_params.add(params[qp.param] ?: qp.cat.toString())
        }
      }

      log.debug("Params: ${query_params}")
      log.debug("Count qry: ${config.countQry}")
      log.debug("Row qry: ${config.rowQry}")
      log.debug("DOMAIN: ${config.domain}")

      GrailsClass dc = grailsApplication.getArtefact("Domain", 'org.gokb.cred.'+ config.domain)

      if (dc?.getClazz()?.isTypeReadable()) {
        int cq = dc.getClazz().executeQuery(config.countQry,query_params)[0]
        List rq = dc.getClazz().executeQuery(config.rowQry, query_params, [max: params.iDisplayLength ?: 400, offset: params.iDisplayStart ?: 0])

        if (!config.required) {
          result.add([id:'', text:'', value:''])
        }

        rq.each { it ->
          Object o = ClassUtils.deproxy(it)
          result.add([id: "${o.class.name}:${o.id}", text: o[config.cols[0]], value: "${o.class.name}:${o.id}"])
        }
      }
    }

    render result as JSON
  }


  Map refdata_config = [
    'KBComponent.EditStatus' : [
      domain:'RefdataValue',
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc = ?0",
      rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc = ?0",
      required:true,
      qryParams:[],
      rdvCat: "KBComponent.EditStatus",
      cols:['value'],
      format:'simple'
    ],
    'KBComponent.Language' : [
      domain:'RefdataValue',
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
      rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
      qryParams:[],
      rdvCat: "KBComponent.Language",
      cols:['value'],
      format:'simple'
    ],
    'VariantNameType' : [
      domain:'RefdataValue',
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
      rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
      qryParams:[],
      rdvCat: "KBComponentVariantName.VariantType",
      cols:['value'],
      format:'simple'
    ],
    'KBComponentVariantName.VariantType' : [
      domain:'RefdataValue',
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
      rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
      qryParams:[],
      rdvCat: "KBComponentVariantName.VariantType",
      cols:['value'],
      format:'simple'
    ],
    'Locale' : [
      domain:'RefdataValue',
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
      rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
      qryParams:[],
      rdvCat: "KBComponentVariantName.Locale",
      cols:['value'],
      format:'simple'
    ],
    'Language' : [
      domain:'RefdataValue',
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
      rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
      qryParams:[],
      rdvCat: "KBComponent.Language",
      cols:['value'],
      format:'simple'
    ],
    'ReviewRequest.Status' : [
      domain:'RefdataValue',
      // countQry:"select count(rdv) from RefdataValue as rdv where rdv.owner.desc='KBComponent.Status' and rdv.value !='${KBComponent.STATUS_DELETED}'",
      // rowQry:"select rdv from RefdataValue as rdv where rdv.owner.desc='KBComponent.Status' and rdv.value !='${KBComponent.STATUS_DELETED}'",
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
      rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
      qryParams:[],
      rdvCat: "ReviewRequest.Status",
      cols:['value'],
      format:'simple'
    ],
    'TitleInstance.Medium' : [
        domain:'RefdataValue',
        countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
        rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
        required:true,
        qryParams:[],
        rdvCat: "TitleInstance.Medium",
        cols:['value'],
        format:'simple'
    ],
    'TitleInstancePackagePlatform.Medium' : [
        domain:'RefdataValue',
        countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
        rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
        required:true,
        qryParams:[],
        rdvCat: "TitleInstancePackagePlatform.Medium",
        cols:['value'],
        format:'simple'
    ],
    'TitleInstancePackagePlatform.CoverageDepth' : [
      domain:'RefdataValue',
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
      rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
      required:true,
      qryParams:[],
      rdvCat: "TitleInstancePackagePlatform.CoverageDepth",
      cols:['value'],
      format:'simple'
    ],
    'TIPPCoverageStatement.CoverageDepth' : [
      domain:'RefdataValue',
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
      rowQry:"select rdv from RefdataValue as rdv where rdv.useInstead is null and rdv.owner.desc=?0",
      required:true,
      qryParams:[],
      rdvCat: "TIPPCoverageStatement.CoverageDepth",
      cols:['value'],
      format:'simple'
    ],
  ]



  /**
   *  addToCollection : Used to create a form which will add a new object to a named collection within the target object.
   * @param __context : the OID ([FullyQualifiedClassName]:[PrimaryKey]) Of the context object
   * @param __newObjectClass : The fully qualified class name of the instance to create
   * @param __recip : Optional - If set, then new_object.recip will point to __context
   * @param __addToColl : The name of the local set to which the new object should be added
   * @param All other parameters are taken to be property names on newObjectClass and used to init the new instance.
   */

  @Transactional
  def addToCollection() {
    log.debug("AjaxController::addToCollection ${params}");
    User user = springSecurityService.currentUser
    Object contextObj = genericOIDService.resolveOID2(params.__context)
    Object new_obj = null
    List errors = []
    GrailsClass domain_class = grailsApplication.getArtefact('Domain', params.__newObjectClass)

    if (domain_class && (domain_class.getClazz().isTypeCreatable() || domain_class.getClazz().isTypeAdministerable())) {
      if (contextObj) {
        boolean editable = checkEditable(contextObj, user)

        if (editable || contextObj.id == user.id) {
          log.debug("Create a new instance of ${params.__newObjectClass}");

          if (params.__newObjectClass == "org.gokb.cred.KBComponentVariantName") {
            String norm_variant = GOKbTextUtils.normaliseString(params.variantName)
            List existing_variants = KBComponentVariantName.findByNormVariantNameAndOwner(norm_variant, contextObj)

            if (existing_variants){
              log.debug("found dupes!")
              errors.add(message(code:'variantName.value.notUnique', default:'This variant is already present in this list'))
            }
            else {
              log.debug("create new variantName")
            }
          }

          if (params.__newObjectClass == "org.gokb.cred.TitleInstancePackagePlatform") {
            if (contextObj.class != Package) {
              log.debug("TIPPs must be created in a package context!")
              errors.add(message(code:'tipp.pkg.nullable', default:'TIPPs must be created in a package context!'))
            }

            if (!params.title || params.title.size() == 0) {
              log.debug("missing title for TIPP creation")
              errors.add(message(code:'tipp.title.nullable', default:'Please provide a title for the TIPP'))
            }

            if (!params.hostPlatform || params.hostPlatform.size() == 0) {
              log.debug("missing platform for TIPP creation")
              errors.add(message(code:'tipp.hostPlatform.nullable', default:'Please provide a platform for the TIPP'))
            }

            if(!params.url || params.url.size() == 0) {
              log.debug("missing url for TIPP creation")
              errors.add(message(code:'tipp.url.nullable', default:'Please provide an url for the TIPP'))
            }
          }

          if (errors.size() == 0) {
            new_obj = domain_class.getClazz().newInstance()
            PersistentEntity pent = grailsApplication.mappingContext.getPersistentEntity(domain_class.fullName)

            pent.getPersistentProperties().each { p -> // list of PersistentProperties
              log.debug("${p.name} (assoc=${p instanceof Association}) (oneToMany=${p instanceof OneToMany}) (ManyToOne=${p instanceof ManyToOne}) (OneToOne=${p instanceof OneToOne})");
              if ( params[p.name] && p.name != 'format' ) {
                if ( p instanceof Association ) {
                  if ( p instanceof ManyToOne || p instanceof OneToOne ) {
                    // Set ref property
                    log.debug("set assoc ${p.name} to lookup of OID ${params[p.name]}");
                    // if ( key == __new__ then we need to create a new instance )
                    new_obj[p.name] = genericOIDService.resolveOID2(params[p.name])
                  }
                  else {
                    // Add to collection
                    log.debug("add to collection ${p.name} for OID ${params[p.name]}");
                    new_obj[p.name].add(genericOIDService.resolveOID2(params[p.name]))
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
                      log.debug("Set simple prop ${p.name} = ${params[p.name]} (as Date ${dateObj}))");
                      break;

                    case LocalDate.class:
                      try {
                        LocalDate dateObj = LocalDate.parse(params[p.name])
                        new_obj[p.name] = dateObj
                        log.debug("Set simple prop ${p.name} = ${params[p.name]} (as LocalDate ${dateObj}))");
                      }
                      catch (Exception e) {
                        log.debug("Unable to parse date value ${arams[p.name]} as LocalDate")
                      }

                    case Float.class:
                      log.debug("Set simple prop ${p.name} = ${params[p.name]} (as float=${Float.valueOf(params[p.name])})");
                      new_obj[p.name] = Float.valueOf(params[p.name]);
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
              RefdataValue refdata = genericOIDService.resolveOID2(params.__refdataValue)
              new_obj[params.__refdataName] = refdata
            }

            // Need to do the right thing depending on who owns the relationship. If new obj
            // BelongsTo other, should be added to recip collection.
            if ( params.__recip ) {
              log.debug("Set reciprocal property ${params.__recip} to ${contextObj}")
              new_obj[params.__recip] = contextObj
              log.debug("Saving ${new_obj}")

              if ( new_obj.validate() ) {
                new_obj.save(flush:true)
                log.debug("Saved OK")

                if (contextObj.respondsTo("lastUpdateComment")){
                  contextObj.lastUpdateComment = "Added new connected ${new_obj.class.simpleName}(ID: ${new_obj.id})."
                }

                contextObj.save(flush: true)
              }
              else {
                errors.addAll(messageService.processValidationErrors(new_obj.errors, request.locale))
              }
            }
            else if (params.__addToColl) {
              contextObj[params.__addToColl].add(new_obj)
              log.debug("Saving ${new_obj}")

              if ( new_obj.validate() ) {
                new_obj.save(flush:true)
                log.debug("New Object Saved OK")
              }
              else {
                errors.addAll(messageService.processValidationErrors(new_obj.errors, request.locale))
              }

              if ( contextObj.validate() ) {
                  contextObj.save(flush:true)
                log.debug("Context Object Saved OK")
              }
              else {
                errors.addAll(messageService.processValidationErrors(contextObj.errors, request.locale))
              }
            }
            else {
              // Stand alone object.. Save it!
              log.debug("Saving stand alone reference object")

              if ( new_obj.validate() ) {
                new_obj.save(flush:true, failOnError:true)
                log.debug("Saved OK (${new_obj.class.name} ${new_obj.id})")
              }
              else {
                errors.addAll(messageService.processValidationErrors(new_obj.errors, request.locale))
              }
            }
          }
        }
        else {
          log.debug("Located instance of context class with oid ${params.__context} is not editable.");
          flash.error = message(code:'component.addToList.denied.label')
        }
      }
      else if (!contextObj) {
        log.debug("Unable to locate instance of context class with oid ${params.__context}");
        flash.error = message(code:'component.context.notFound.label')
      }
    }
    else {
      if (!domain_class) {
        log.error("Unable to lookup domain class ${params.__newObjectClass}");
        flash.error = message(code:'component.classNotFound.label', args:[params.__newObjectClass])
      } else {
        flash.error = message(code:'component.create.denied.label', args:[params.__newObjectClass])
        log.error("No permission to create an object of domain class ${params.__newObjectClass}");
      }
    }

    if (errors.size() > 0) {
      flash.error = errors
    }

    withFormat {
      html {
        if ( new_obj && params.__showNew && errors.size() == 0) {
          redirect(controller:'resource', action:'show', id:"${new_obj.class.name}:${new_obj.id}");
        }
        else {
          def redirect_to = request.getHeader('referer')

          if ( params.fragment && params.fragment.length() > 0 ) {
            redirect_to = "${redirect_to}#${params.fragment}"
          }
          redirect(url: redirect_to)
        }
      }
      json {
        Map result = ['result': 'OK', 'params': params]

        if (flash.error) {
          result.result = 'ERROR'
          result.errors = flash.error
        }
        else {
          result.new_object = new_obj
          result.new_oid = "${new_obj.class.name}:${new_obj.id}"
        }

        render result as JSON
      }
    }
  }

  /**
   *  addToStdCollection : Used to add an existing object to a named collection that is not mapped through a join object.
   * @param __context : the OID ([FullyQualifiedClassName]:[PrimaryKey]) of the context object
   * @param __relatedObject : the OID ([FullyQualifiedClassName]:[PrimaryKey]) of the object to be added to the list
   * @param __property : The property name of the collection to which the object should be added
   */

  @Transactional
  def addToStdCollection() {
    log.debug("addToStdCollection(${params})");
    // Adds a link to a collection that is not mapped through a join object
    Object contextObj = genericOIDService.resolveOID2(params.__context)
    User user = springSecurityService.currentUser
    Object relatedObj = genericOIDService.resolveOID2(params.__relatedObject)
    Map result = ['result': 'OK', 'params': params]

    if (relatedObj != null && contextObj != null) {
      boolean editable = checkEditable(contextObj, user)

      if (editable || user.id == contextObj.id) {
        if (!contextObj["${params.__property}"].contains(relatedObj)) {
          contextObj["${params.__property}"].add (relatedObj)
          contextObj.save(flush:true, failOnError:true)
          log.debug("Saved: ${contextObj.id}");
          result.context = contextObj
        }
        else{
          flash.error = "Object is already present in this list!"
          log.debug("Tried to add the same object twice!")
          result.result = 'ERROR'
          result.error = "Object is already present in this list!"
        }
      }
      else {
        flash.error = message(code:'component.list.add.denied.label')
        log.debug("context object not editable.")
        result.result = 'ERROR'
        result.error = "Permission to add to this list was denied."
      }
    }
    else if (!contextObj) {
      flash.error = message(code:'component.context.notFound.label')
      result.result = 'ERROR'
      result.error = "Context object could not be found!"
    }
    else if (!relatedObj) {
      flash.error = message(code:'component.listItem.notFound.label')
      result.result = 'ERROR'
      result.error = "List item not found!"
    }

    withFormat {
      html {
        String redirect_to = request.getHeader('referer')

        if ( params.fragment && params.fragment.length() > 0 ) {
          redirect_to = "${redirect_to}#${params.fragment}"
        }
        redirect(url: redirect_to)
      }
      json {
        render result as JSON
      }
    }
  }

  /**
   *  unlinkManyToMany : Used to remove an object from a named collection.
   * @param __context : the OID ([FullyQualifiedClassName]:[PrimaryKey]) of the context object
   * @param __itemToRemove : the OID ([FullyQualifiedClassName]:[PrimaryKey]) of the object to be removed from the list
   * @param __property : The property name of the collection from which the object should be removed from
   * @param __otherEnd : The property name from the side of the object to be removed
   */

  @Transactional
  def unlinkManyToMany() {
    log.debug("unlinkManyToMany(${params})");
    Object contextObj = genericOIDService.resolveOID2(params.__context)
    User user = springSecurityService.currentUser
    Map result = ['result': 'OK', 'params': params]

    if (contextObj) {
      boolean editable = checkEditable(contextObj, user)

      if (editable || contextObj.id == user.id) {
        Object item_to_remove = genericOIDService.resolveOID2(params.__itemToRemove)

        if ( item_to_remove ) {
          log.debug("${params}");
          log.debug("removing: ${item_to_remove} from ${params.__property} for ${contextObj}");

          def remove_result = contextObj[params.__property].remove(item_to_remove);

          log.debug("remove successful?: ${remove_result}")
          log.debug("child ${item_to_remove} removed: "+ contextObj[params.__property]);

          if ( params.propagate == "true" && KBComponent.isAssignableFrom(contextObj.class)) {
            contextObj.lastSeen = new Date().getTime()
          }

          if (contextObj.save(flush: true, failOnError: true)) {
            log.debug("Saved context object ${contextObj.class.name}")
          }
          else {
            flash.error = messageService.processValidationErrors(contextObj.errors, request.locale)
            result.result = 'ERROR'
            result.code = 400
          }

          if (params.__otherEnd && item_to_remove[params.__otherEnd] != null) {
            log.debug("remove parent: " + item_to_remove[params.__otherEnd])
            item_to_remove[params.__otherEnd] = null
            log.debug("parent removed: " + item_to_remove[params.__otherEnd])
          }

          if (!item_to_remove.validate()) {
            flash.error = messageService.processValidationErrors(item_to_remove.errors, request.locale)
          }
          else {
            item_to_remove.save(flush:true)
          }
        } else {
          log.error("Unable to resolve item to remove : ${params.__itemToRemove}");
          flash.error(code:'component.listItem.notFound.label')
        }
      }
      else {
        flash.error = message(code:'component.list.remove.denied.label')
        log.debug("Located instance of context class with oid ${params.__context} is not editable.");
        result.result = 'ERROR'
        result.code = 403
      }
    }
    else {
      flash.error = message(code:'component.context.notFound.label')
      log.debug("Unable to locate instance of context class with oid ${params.__context}");
      result.result = 'ERROR'
      result.code = 404
    }

    withFormat {
      html {
        def redirect_to = request.getHeader('referer')

        if ( params.fragment && params.fragment.length() > 0 ) {
          redirect_to = "${redirect_to}#${params.fragment}"
        }
        redirect(url: redirect_to)
      }
      json {
        if (flash.error) {
          result.errors = flash.error
        }

        render result as JSON
      }
    }
  }

  /**
   *  delete : Used to delete a domain class object.
   * @param __context : the OID ([FullyQualifiedClassName]:[PrimaryKey]) of the context object
   */

  @Transactional
  def delete() {
    log.debug("delete(${params}), referer: ${request.getHeader('referer')}");
    // Adds a link to a collection that is not mapped through a join object
    Object contextObj = genericOIDService.resolveOID2(params.__context)
    User user = springSecurityService.currentUser
    Map result = ['result': 'OK', 'params': params]

    if ( contextObj ) {
      boolean editable = checkEditable(contextObj, user)

      if (editable && contextObj.isDeletable()) {
        if (contextObj.respondsTo('deleteSoft')) {
          contextObj.deleteSoft()
        }
        else {
          contextObj.delete(flush:true)
        }
        log.debug("Item deleted.")
      }
      else {
        flash.error = message(code:'component.delete.denied.label')
        log.debug("Located instance of context class with oid ${params.__context} is not editable.");
      }
    }
    else {
      flash.error = message(code:'component.notFound.label', args:[params.__context])
      log.debug("Unable to locate instance of context class with oid ${params.__context}");
    }

    def redirect_to = request.getHeader('referer')

    if ( params.redirect ) {
      redirect_to = params.redirect
    }
    else if ( ( params.fragment ) && ( params.fragment.length() > 0 ) ) {
      redirect_to = "${redirect_to}#${params.fragment}"
    }

    withFormat {
      html {
        redirect(url: redirect_to)
      }
      json {
        if (flash.error) {
          result.errors = flash.error
          result.result = 'ERROR'
        }

        render result as JSON
      }
    }
  }

  /**
   *  lookup : Calls the refdataFind function of a specific class and returns a simple result list.
   * @param baseClass : The class name to
   * @param max : Number of results to return
   * @param addEmpty : Add an empty row at the start of the list
   * @param filter1 : A status value string which should be filtered out after the query has been executed
   */

  def lookup() {
    log.debug("AjaxController::lookup ${params}")
    Map result = [:]
    params.max = params.max ?: 10
    GrailsClass domain_class = grailsApplication.getArtefact('Domain', params.baseClass)

    if ( domain_class && domain_class.getClazz().isTypeReadable() ) {
      result.values = domain_class.getClazz().refdataFind(params)
    }
    else {
      log.debug("Unable to locate domain class ${params.baseClass} or not readable")
      result.values = []
      result.error = "Unable to locate domain class ${params.baseClass}, or this user is not permitted to view it."
    }
    //result.values = [[id:'Person:45',text:'Fred'],
    //                 [id:'Person:23',text:'Jim'],
    //                 [id:'Person:22',text:'Jimmy'],
    //                 [id:'Person:3',text:'JimBob']]

    //     if ( params.addEmpty=='Y' || params.addEmpty=='y' ) {
    //       result.values.add(0, [id:'', text:'']);
    //     }

    render result as JSON
  }

  /**
   *  editableSetValue : Used to set a primitive property value.
   * @param pk : the OID ([FullyQualifiedClassName]:[PrimaryKey]) of the context object
   * @param type : Used for date parsing with value 'date'
   * @param dateFormat : Used for overriding the default date format ('yyyy-MM-dd')
   * @param name : The name of the property to be changed
   * @param value : The new value for the property
   */

  @Transactional
  def editableSetValue() {
    log.debug("editableSetValue ${params}");
    User user = springSecurityService.currentUser
    Object target_object = genericOIDService.resolveOID(params.pk)

    Map result = ['result': 'OK', 'params': params]
    Map errors = [:]

    if (target_object) {
      boolean editable = checkEditable(target_object, user)

      if (editable || target_object == user) {
        if (params.type == 'date') {
          target_object."${params.name}" = params.date('value', params.dateFormat ?: 'yyyy-MM-dd')
        }
        else if (params.type == 'localdate') {
          target_object."${params.name}" = params.value ? LocalDate.parse(params.value) : null
        }
        else if (params.type == 'boolean') {
          target_object."${params.name}" = params.boolean('value')
        }
        else if (params.name == 'uuid' || params.name == 'password') {
          errors[params.name] = "This property is not editable."
        }
        else if (params.type == 'number') {
          target_object."${params.name}" = params.int('value')
        }
        else {
          Map binding_properties = [:]
          String new_val = params.value?.trim() ?: null

          binding_properties[ params.name ] = new_val
          bindData(target_object, binding_properties)
        }

        if (target_object.validate()) {
          target_object.save(flush:true)
        }
        else {
          errors = messageService.processValidationErrors(target_object.errors, request.locale)
        }
      }
      else {
        errors['global'] = [[message:"Object ${target_object} is not editable.".toString()]]
        log.debug("Object ${target_object} is not editable.")
      }
    }
    else {
      errors['global'] = [[message:"Not able to resolve object from ${params.pk}.".toString()]]
      log.debug("Object ${target_object} could not be resolved.")
    }

    withFormat {
      html {
        String resp = null

        if (errors.size() == 0) {
          resp = params.value
        }
        else {
          Map error_obj = errors[params.name] ? errors[params.name][0] : errors['global'][0]
          log.debug("Error msg: ${error_obj} (${error_obj.message})")

          resp = error_obj.message
          response.setContentType('text/plain;charset=UTF-8')
          response.status = 400
          render resp
        }
      }
      json {
        if (errors.size() > 0) {
          result.errors = errors
          result.result = 'ERROR'
        }

        render result as JSON
      }
    }
  }

  private boolean checkEditable(obj, user) {
    boolean editable = obj.isEditable()

    if (editable) {
      Object curatedObj = obj.respondsTo("getCuratoryGroups") ? obj : ( KBComponent.has(obj, 'pkg') ? obj.pkg : null )

      if (curatedObj && curatedObj.curatoryGroups?.size() > 0) {

        editable = (user.curatoryGroups?.id.intersect(curatedObj.curatoryGroups?.id).size() > 0 || user.isAdmin()) ? true : false
      }
    }

    editable
  }

  /**
   *  genericSetRel : Used to set a complex property value.
   * @param pk : the OID ([FullyQualifiedClassName]:[PrimaryKey]) of the context object
   * @param name : The name of the property to be changed
   * @param value : The OID ([FullyQualifiedClassName]:[PrimaryKey]) of the object to link
   */

  @Transactional
  def genericSetRel() {
    // [id:1, value:JISC_Collections_NESLi2_Lic_IOP_Institute_of_Physics_NESLi2_2011-2012_01012011-31122012.., type:License, action:inPlaceSave, controller:ajax
    // def clazz=grailsApplication.domainClasses.findByFullName(params.type)
    log.debug("genericSetRel ${params}");
    User user = springSecurityService.currentUser
    def target = genericOIDService.resolveOID(params.pk)
    def value = null

    if (params.type == 'boolean') {
      value = params.boolean('value')
    }
    else {
      value = genericOIDService.resolveOID(params.value)
    }

    Map result = ['result':'OK']

    if ( target != null) {
      boolean editable = checkEditable(target, user)

      if (editable) {
        // def binding_properties = [ "${params.name}":value ]
        log.debug("Binding: ${params.name} into ${target} - a ${target.class.name}");
        // bindData(target, binding_properties)
        target[params.name] = value
        log.debug("Saving... after assignment ${params.name} = ${target[params.name]}");

        if ( target.validate() ) {
          target = target.merge(flush: true, failOnError: true)

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
              result.objVal = renderObjectValue(value);
              // result = value.toString()
            }
          }
        }
        else {
          log.debug("Problem saving.. ${target.errors}");
          result.errors = messageService.processValidationErrors(target.errors, request.locale)
          result.result = "ERROR"
        }
      }
      else {
        log.debug("Target is not editable!");
        result.result = "ERROR"
        result.errors = ["Not able to edit this property!"]
      }
    }
    else {
      log.debug("Target not found!");
      result.result = "ERROR"
      result.errors = ["Unable to locate intended target object!"]
    }

    withFormat {
      html {
        def redirect_to = request.getHeader('referer')

        if ( params.redirect ) {
          redirect_to = params.redirect
        }
        else if ( ( params.fragment ) && ( params.fragment.length() > 0 ) ) {
          redirect_to = "${redirect_to}#${params.fragment}"
        }

        redirect(url: redirect_to);
      }
      json {
        result.newValue = target[params.name]
        log.debug("return ${result}");
        render result as JSON
      }
    }
  }

  private String renderObjectValue(value) {
    String result = ''

    if ( value ) {
      switch ( value.class ) {
        case org.gokb.cred.RefdataValue.class:
          if ( value.icon != null ) {
            result = "<span class=\"select-icon ${value.icon}\"></span>${value.value}"
          }
          else {
            result = value.value
          }
          break;
        case Boolean.class:
          result = value ? 'Yes' : 'No'
        default:
          result = value.toString()
      }
    }
    result
  }

  /**
   *  addIdentifier : Used to add an identifier to a list.
   * @param __context : The OID ([FullyQualifiedClassName]:[PrimaryKey]) of the context object
   * @param identifierNamespace : The OID ([FullyQualifiedClassName]:[PrimaryKey]) of the identifier namespace
   * @param identifierValue : The value of the identifier to link
   */

  @Transactional
  def addIdentifier() {
    log.debug("addIdentifier - ${params}")
    Map result = ['result': 'OK', 'params': params]
    User user = springSecurityService.currentUser
    Identifier identifier_instance = null
    // Check identifier namespace present, and identifier value valid for that namespace

    if ( ( params.identifierNamespace?.trim() ) &&
         ( params.identifierValue?.trim() ) &&
         ( params.__context?.trim() ) ) {
      IdentifierNamespace ns = genericOIDService.resolveOID(params.identifierNamespace)
      KBComponent owner = genericOIDService.resolveOID(params.__context)

      if ( ( ns != null ) && ( owner != null ) ) {
        boolean editable = checkEditable(owner, user)

        if (editable) {
          // Lookup or create Identifier
          try {
              identifier_instance = componentLookupService.lookupOrCreateCanonicalIdentifier(ns.value, params.identifierValue)

              if (identifier_instance && !identifier_instance.hasErrors()) {

                log.debug("Got ID: ${identifier_instance}")
                // Link if not existing
                if (!owner.ids.contains(identifier_instance)) {
                  owner.addIdentifier(identifier_instance)
                  owner.save(flush: true)
                }
                else {
                  flash.error = message(code:'identifier.link.unique')
                }
              }
          }
          catch (grails.validation.ValidationException ve) {

            log.debug("${ve}")
            flash.error = message(code:'identifier.value.illegalIdForm')
          }
        }
        else {
          flash.error = message(code:'component.addToList.denied.label')
        }
      }
      else {
        flash.error = message(code:'identifier.create.error')
        log.debug("could not create identifier!")
      }
    }
    log.debug("Redirecting to referer: ${request.getHeader('referer')}");

    withFormat {
      html {
        redirect(url: (request.getHeader('referer')+params.hash?:''))
      }
      json {
        if (flash.error) {
          result.result = 'ERROR'
          result.error = flash.error
        }
        else {
          result.new_obj = identifier_instance
          result.new_oid = "${identifier_instance.class.name}:${identifier_instance.id}"
        }

        render result as JSON
      }
    }
  }

/**
   *  addSubject : Used to add a subject to a list.
   * @param __context : The OID ([FullyQualifiedClassName]:[PrimaryKey]) of the context object
   * @param scheme : The RefdataValue of the subject scheme
   * @param val : The value/heading of the subject
   */

  @Transactional
  def addSubject() {
    log.debug("addSubject - ${params}")
    Map result = [result: 'OK', params: params]
    User user = springSecurityService.currentUser
    ComponentSubject new_cs = null

    if (params.__context?.trim() && params.scheme?.trim() && params.val?.trim()) {
      KBComponent owner = genericOIDService.resolveOID(params.__context)
      RefdataValue scheme = genericOIDService.resolveOID(params.scheme)
      RefdataValue scheme_ddc = RefdataCategory.lookup('Subject.Scheme', 'DDC')

      if (owner && scheme) {
        boolean editable = checkEditable(owner, user)

        if (editable) {
          Subject active_subject = Subject.findBySchemeAndHeading(scheme, params.val.trim())

          if (!active_subject) {
            if (scheme != scheme_ddc || validationService.checkDDCNotation(params.val).result == 'OK') {
              active_subject = new Subject(scheme: scheme, heading: params.val.trim())
            }
            else if (scheme == scheme_ddc) {
              flash.error = message(code:'subject.ddc.notation.error.format')
            }
          }

          if (active_subject && !ComponentSubject.findByComponentAndSubject(owner, active_subject)) {
            new_cs = new ComponentSubject(component: owner, subject: active_subject).save(flush: true)
          }
          else if (active_subject) {
            flash.error = message(code:'subject.link.unique')
          }
        }
      }
      else {
        flash.error = message(code:'subject.create.error')
      }
    }

    withFormat {
      html {
        redirect(url: (request.getHeader('referer')+params.hash?:''))
      }
      json {
        if (flash.error) {
          result.result = 'ERROR'
          result.error = flash.error
        }
        else {
          result.new_obj = new_cs
          result.new_oid = "${new_cs.class.name}:${new_cs.id}"
        }

        render result as JSON
      }
    }
  }

  /**
   *  plusOne : Like or Unlike a component for the current user.
   * @param object : The OID ([FullyQualifiedClassName]:[PrimaryKey]) of the context object
   */

  @Transactional
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

  /**
   *  authorizeVariant : Used to replace the name of a component by one of its existing variant names.
   * @param id : The id of the variant name
   */

  @Transactional
  def authorizeVariant() {
    log.debug("${params}");
    Map result = ['result':'OK', 'params':params]
    KBComponentVariantName variant = KBComponentVariantName.get(params.id)
    User user = springSecurityService.currentUser

    if ( variant != null) {
      KBComponent owner = variant.owner
      boolean editable = checkEditable(owner, user)

      if (editable) {
        // Does the current owner.name exist in a variant? If not, we should create one so we don't loose the info
        KBComponentVariantName current_name_as_variant = owner.variantNames.find { it.variantName == owner.name }

        result.owner = "${owner.class.name}:${owner.id}"

        if ( current_name_as_variant == null ) {
          log.debug("No variant name found for current name: ${owner.name} ")
          String variant_name

          if (variant.owner.name) {
            variant_name = owner.name
          }
          else if (owner?.respondsTo('getDisplayName') && owner.getDisplayName()) {
            variant_name = owner.getDisplayName()?.trim()
          }
          else if(owner?.respondsTo('getName') ) {
            variant_name = owner?.getName()?.trim()
          }

          if (variant_name) {
            new KBComponentVariantName(owner: owner, variantName: variant_name).save(flush: true)
          }
          else {
            log.warn("authorizeVariant :: Unable to save existing name for component ${owner} as new variant!")
          }

        }
        else{
            log.debug("Found existing variant name: ${current_name_as_variant}")
        }

        variant.variantType = RefdataCategory.lookup('KBComponentVariantName.VariantType', 'Authorized')
        owner.name = variant.variantName

        if (owner.validate()) {
          owner.save(flush: true)
          result.new_name = variant.owner.name
        }
        else {
          result.result = 'ERROR'
          result.code = 400
          result.message = "This name already belongs to another component of the same type!"
          flash.error = message(code:'variantName.authorize.notUnique')
        }
      }
      else {
        result.result = 'ERROR'
        result.code = 403
        result.message = "No permission to edit variants for this object!"
        flash.error = message(code:'variantName.owner.denied')
      }
    }
    else if (!variant) {
      result.result = 'ERROR'
      result.code = 404
      result.message = "Variant with id ${params.id} not found!".toString()
      def vname = message(code:'variantName.label')
      flash.message = message(code:'default.not.found.message', args:[vname, params.id])
    }

    withFormat {
      html {
        def redirect_to = request.getHeader('referer')

        if ( params.redirect ) {
          redirect_to = params.redirect
        }
        else if ( ( params.fragment ) && ( params.fragment.length() > 0 ) ) {
          redirect_to = "${redirect_to}#${params.fragment}"
        }

        redirect(url: redirect_to);
      }
      json {
        render result as JSON
      }
    }
  }

  /**
   *  deleteVariant : Used to delete a variant name of a component.
   * @param id : The id of the variant name
   */

  @Transactional
  def deleteVariant() {
    log.debug("${params}");
    Map result = ['result':'OK', 'params': params]
    KBComponentVariantName variant = KBComponentVariantName.get(params.id)
    User user = springSecurityService.currentUser
    KBComponent variantOwner = variant?.owner ?: null

    if ( variant != null ) {
      boolean editable = checkEditable(variantOwner, user)

      if (editable) {
        String variantName = variant.variantName

        variant.delete()
        variantOwner.lastUpdateComment = "Deleted Alternate Name '${variantName}'."
        variantOwner.save(flush: true)

        result.owner_oid = "${variantOwner.class.name}:${variantOwner.id}"
        result.deleted_variant = "${variantName}"
      }
      else {
        result.result = 'ERROR'
        result.code = 403
        result.message = "No permission to edit variants for this object!"
        flash.error = message(code:'variantName.owner.denied')
      }
    }
    else if (!variant) {
      result.result = 'ERROR'
      result.code = 404
      def vname = message(code:'variantName.label')
      flash.error = message(code:'default.not.found.message', args:[vname, params.id])
      result.message = "Variant with id ${params.id} not found!".toString()
    }

    withFormat {
      html {
        def redirect_to = request.getHeader('referer')

        if ( params.redirect ) {
          redirect_to = params.redirect
        }
        else if ( ( params.fragment ) && ( params.fragment.length() > 0 ) ) {
          redirect_to = "${redirect_to}#${params.fragment}"
        }

        redirect(url: redirect_to)
      }
      json {
        render result as JSON
      }
    }
  }

  /**
   *  deleteComment : Used to delete a comment of a component.
   * @param id : The id of the comment
   */

  @Transactional
  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def deleteComment() {
    log.debug("${params}");
    Map result = ['result':'OK', 'params': params]
    KBComponentComment comment = KBComponentComment.get(params.id)
    User user = springSecurityService.currentUser
    KBComponent commentOwner = comment?.owner ?: null

    if ( comment != null ) {
      boolean editable = checkEditable(commentOwner, user)

      if (editable) {
        result.deleted_comment = "${comment.language.value}"
        result.owner_oid = "${commentOwner.class.name}:${commentOwner.id}"

        comment.delete()
        commentOwner.lastUpdateComment = "Deleted comment for language '${comment.language.value}'"
        commentOwner.save(flush: true)
      }
      else {
        result.result = 'ERROR'
        result.code = 403
        result.message = "No permission to edit comments for this object!"
        flash.error = message(code:'comment.owner.denied')
      }
    }
    else if (!variant) {
      result.result = 'ERROR'
      result.code = 404
      def vname = message(code:'comment.label')
      flash.error = message(code:'default.not.found.message', args:[vname, params.id])
      result.message = "Comment with id ${params.id} not found!".toString()
    }

    withFormat {
      html {
        def redirect_to = request.getHeader('referer')

        if ( params.redirect ) {
          redirect_to = params.redirect
        }
        else if ( ( params.fragment ) && ( params.fragment.length() > 0 ) ) {
          redirect_to = "${redirect_to}#${params.fragment}"
        }

        redirect(url: redirect_to)
      }
      json {
        render result as JSON
      }
    }
  }

  /**
   *  deleteCoverageStatement : Used to delete a TIPPCoverageStatement.
   * @param id : The id of the coverage statement object
   */

  @Transactional
  def deleteCoverageStatement() {
    log.debug("${params}");
    Map result = ['result':'OK', 'params': params]
    User user = springSecurityService.currentUser
    TIPPCoverageStatement tcs = TIPPCoverageStatement.get(params.id)
    TitleInstancePackagePlatform tipp = tcs.owner

    if ( tcs != null) {
      boolean editable = checkEditable(tipp, user)

      if (editable) {
        tcs.delete()
        tipp.lastUpdateComment = "Deleted Coverage Statement."
        tipp.save(flush: true)
      }
      else {
        result.result = 'ERROR'
        result.code = 403
        result.message = "This TIPP is not editable!"
        flash.error = message(code:'tipp.coverage.denied.label')
      }
    }
    else if (!tcs) {
      result.result = 'ERROR'
      result.code = 404
      String vname = message(code:'TIPPCoverageStatement.label')
      result.message = "TIPPCoverageStatement with id ${params.id} not found!".toString()
      flash.error = message(code:'default.not.found.message', args:[vname, params.id])
    }

    withFormat {
      html {
        String redirect_to = request.getHeader('referer')

        if ( params.redirect ) {
          redirect_to = params.redirect
        }
        else if ( ( params.fragment ) && ( params.fragment.length() > 0 ) ) {
          redirect_to = "${redirect_to}#${params.fragment}"
        }

        redirect(url: redirect_to)
      }
      json {
        render result as JSON
      }
    }
  }

  /**
   *  deleteIdLink : Used to delete an object of a linked ids join table.
   * @param id : The id of the link object
   * @param propagate : 'true' if the component should be marked as updated
   * @param keepLink : 'true' if the link status should be set to deleted
   */

  @Transactional
  def deleteIdLink() {
    Map result = ['result': "OK", 'params': params]
    ComponentIdentifier obj = ComponentIdentifier.get(params.id)
    User user = springSecurityService.currentUser

    if (obj) {
      KBComponent comp = obj.component
      boolean editable = checkEditable(comp, user)

      if (editable) {
        log.debug("Delete ID link..")

        if (params.propagate == "true") {
          comp.lastSeen = new Date().getTime()
          comp.save(flush: true)
        }

        if (params.keepLink) {
          obj.status = RefdataCategory.lookup(ComponentIdentifier.RD_STATUS, ComponentIdentifier.STATUS_DELETED)
        }
        else{
          obj.delete(flush:true)
        }
      }
      else {
        result.code = 403
        result.message = "Not deleting link.. no edit permissions on ${comp}!".toString()
        result.result = 'ERROR'
        flash.error = message(code:'combo.fromComponent.denied.label', args:["${comp}"])
        log.debug("Not deleting link.. no edit permissions on component!")
      }
    }
    else {
      result.code = 404
      result.message = "Unable to reference ComponentIdentifier!"
      flash.error = message(code:'default.not.found.message', args:["TitlePublisher", params.id])
    }

    withFormat {
      html {
        String redirect_to = request.getHeader('referer')

        if ( params.redirect ) {
          redirect_to = params.redirect
        }
        else if ( ( params.fragment ) && ( params.fragment.length() > 0 ) ) {
          redirect_to = "${redirect_to}#${params.fragment}"
        }

        redirect(url: redirect_to)
      }
      json {
        render result as JSON
      }
    }
  }

  /**
   *  deletePublisherLink : Used to delete an object of the publisher join table.
   * @param id : The id of the link object
   * @param propagate : 'true' if the title should be marked as updated
   * @param keepLink : 'true' if the link status should be set to deleted
   */

  @Transactional
  def deletePublisherLink() {
    Map result = ['result': "OK", 'params': params]
    TitlePublisher obj = TitlePublisher.get(params.id)
    User user = springSecurityService.currentUser

    if (obj) {
      TitleInstance title = obj.title
      boolean editable = checkEditable(title, user)

      if (editable) {
        log.debug("Delete publisher link..")

        if ( params.propagate == "true") {
          title.lastSeen = new Date().getTime()
          title.save(flush: true)
        }

        if (params.keepLink) {
          obj.status = RefdataCategory.lookup(TitlePublisher.RD_STATUS, TitlePublisher.STATUS_DELETED)
        }
        else {
          obj.delete(flush: true)
        }
      }
      else {
        result.code = 403
        result.message = "Not deleting link.. no edit permissions on ${title}!".toString()
        result.result = 'ERROR'
        flash.error = message(code:'combo.fromComponent.denied.label', args:["${title}"])
        log.debug("Not deleting link.. no edit permissions on title!")
      }
    }
    else {
      result.code = 404
      result.message = "Unable to reference TitlePublisher!"
      flash.error = message(code:'default.not.found.message', args:["TitlePublisher", params.id])
    }

    withFormat {
      html {
        String redirect_to = request.getHeader('referer')

        if ( params.redirect ) {
          redirect_to = params.redirect
        }
        else if ( ( params.fragment ) && ( params.fragment.length() > 0 ) ) {
          redirect_to = "${redirect_to}#${params.fragment}"
        }

        redirect(url: redirect_to)
      }
      json {
        render result as JSON
      }
    }
  }

  /**
   *  deletePrice : Used to delete a ComponentPrice from a TitleInstance.
   * @param id : The id of the ComponentPrice
   */

  @Transactional
  def deletePrice() {
    Map result = ['result': "OK", 'params': params]
    ComponentPrice c = ComponentPrice.get(params.id);
    User user = springSecurityService.currentUser

    if (c) {
      boolean editable = checkEditable(c.owner, user)

      if (editable) {
        log.debug("Delete Price..")
        c.delete(flush: true)
      }
    }

    withFormat {
      html {
        String redirect_to = request.getHeader('referer')

        if ( params.redirect ) {
          redirect_to = params.redirect
        }
        else if ( ( params.fragment ) && ( params.fragment.length() > 0 ) ) {
          redirect_to = "${redirect_to}#${params.fragment}"
        }

        redirect(url: redirect_to)
      }
      json {
        render result as JSON
      }
    }
  }
}
