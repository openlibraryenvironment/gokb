package org.gokb

import grails.converters.JSON
import org.gokb.cred.*

class AjaxSupportController {

  def genericOIDService

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

  def setRef() {
    def result = [:]
    render result as JSON
  }


  def getRefdata() {

    def result = [:]

    def config = refdata_config[params.id]
    if ( config ) {
      def query_params = []
      config.qryParams.each { qp ->
        if ( qp.clos ) {
          query_params.add(qp.clos(params[qp.param]?:''));
        }
        else {
          query_params.add(params[qp.param]);
        }
      }

      log.debug("Params: ${query_params}");
      log.debug("Count qry: ${config.countQry}");
      log.debug("Row qry: ${config.rowQry}");

      def cq = Org.executeQuery(config.countQry,query_params);
      def rq = Org.executeQuery(config.rowQry,
                                query_params,
                                [max:params.iDisplayLength?:10,offset:params.iDisplayStart?:0]);

      rq.each { it ->
        result["${it.class.name}:${it.id}"] = it[config.cols[0]];
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
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.owner.desc='Package Type'",
      rowQry:"select rdv from RefdataValue as rdv where rdv.owner.desc='Package Type'",
      qryParams:[],
      cols:['value'],
      format:'simple'
    ],
    'PackageStatus' : [
      domain:'RefdataValue',
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.owner.desc='Package Status'",
      rowQry:"select rdv from RefdataValue as rdv where rdv.owner.desc='Package Status'",
      qryParams:[],
      cols:['value'],
      format:'simple'
    ],
    'CoreStatus' : [
      domain:'RefdataValue',
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.owner.desc='CoreStatus'",
      rowQry:"select rdv from RefdataValue as rdv where rdv.owner.desc='CoreStatus'",
      qryParams:[],
      cols:['value'],
      format:'simple'
    ]
  ]



 def addToCollection() {
    log.debug("AjaxController::addToCollection ${params}");

    def contextObj = resolveOID2(params.__context)
    def domain_class = grailsApplication.getArtefact('Domain',params.__newObjectClass)

    if ( domain_class ) {

      if ( contextObj ) {
        log.debug("Create a new instance of ${params.__newObjectClass}");

        def new_obj = domain_class.getClazz().newInstance();

        domain_class.getPersistentProperties().each { p -> // list of GrailsDomainClassProperty
          log.debug("${p.name} (assoc=${p.isAssociation()}) (oneToMany=${p.isOneToMany()}) (ManyToOne=${p.isManyToOne()}) (OneToOne=${p.isOneToOne()})");
          if ( params[p.name] ) {
            if ( p.isAssociation() ) {
              if ( p.isManyToOne() || p.isOneToOne() ) {
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
              log.debug("Set simple prop ${p.name} = ${params[p.name]}");
              new_obj[p.name] = params[p.name]
            }
          }
        }

        if ( params.__recip ) {
          log.debug("Set reciprocal property ${params.__recip} to ${contextObj}");
          new_obj[params.__recip] = contextObj
        }

        log.debug("Saving ${new_obj}");
        if ( new_obj.save() ) {
          log.debug("Saved OK");
        }
        else {
          new_obj.errors.each { e ->
            log.debug("Problem ${e}");
          }
        }
      }
      else {
        log.debug("Unable to locate instance of context class with oid ${params.__context}");
      }
    }
    else {
      log.error("Unable to ookup domain class ${params.__newObjectClass}");
    }

    redirect(url: request.getHeader('referer'))
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


  def lookup() {
    log.debug("AjaxController::lookup ${params}");
    def result = [:]
    params.max = params.max ?: 10;
    def domain_class = grailsApplication.getArtefact('Domain',params.baseClass)
    if ( domain_class ) {
      result.values = domain_class.getClazz().refdataFind(params);
    }
    else {
      log.error("Unable to locate domain class ${params.baseClass}");
      result.values=[]
    }
    //result.values = [[id:'Person:45',text:'Fred'],
    //                 [id:'Person:23',text:'Jim'],
    //                 [id:'Person:22',text:'Jimmy'],
    //                 [id:'Person:3',text:'JimBob']]
    render result as JSON
  }


  def editableSetValue() {
    log.debug("editableSetValue ${params}");
    def target_object = resolveOID2(params.pk)
    if ( target_object ) {
      if ( params.type=='date' ) {
        target_object."${params.name}" = params.date('value','yyyy-MM-dd')
      }
      else {
        target_object."${params.name}" = params.value
      }
      target_object.save(flush:true);
    }

    response.setContentType('text/plain')
    def outs = response.outputStream
    outs << params.value
    outs.flush()
    outs.close()
  }

}
