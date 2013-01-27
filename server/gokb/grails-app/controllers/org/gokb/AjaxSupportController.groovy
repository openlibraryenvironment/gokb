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

}
