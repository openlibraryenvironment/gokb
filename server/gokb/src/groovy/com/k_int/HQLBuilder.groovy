package com.k_int

import groovy.util.logging.*

@Log4j
public class HQLBuilder {

  /**
   *  Accept a qbetemplate of the form
   *  [
   *		baseclass:'Fully.Qualified.Class.Name.To.Search',
   *		title:'Title Of Search',
   *		qbeConfig:[
   *			// For querying over associations and joins, here we will need to set up scopes to be referenced in the qbeForm config
   *			// Until we need them tho, they are omitted. qbeForm entries with no explicit scope are at the root object.
   *			qbeForm:[
   *				[
   *					prompt:'Name or Title',
   *					qparam:'qp_name',
   *					placeholder:'Name or title of item',
   *					contextTree:['ctxtp':'qry', 'comparator' : 'ilike', 'prop':'name']
   *				],
   *				[
   *					prompt:'ID',
   *					qparam:'qp_id',
   *					placeholder:'ID of item',
   *					contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'id', 'type' : 'java.lang.Long']
   *				],
   *				[
   *					prompt:'SID',
   *					qparam:'qp_sid',
   *					placeholder:'SID for item',
   *					contextTree:['ctxtp':'qry', 'comparator' : 'eq', 'prop':'ids.value']
   *				],
   *			],
   *			qbeResults:[
   *				[heading:'Type', property:'class.simpleName'],
   *				[heading:'Name/Title', property:'name', link:[controller:'resource',action:'show',id:'x.r.class.name+\':\'+x.r.id'] ]
   *			]
   *		]
   *	]
   *
   *
   */
  public static def build(qbetemplate, params, result) {
    // select o from Clazz as o where 

    log.debug("build ${params}");

    // Step 1 : Walk through all the properties defined in the template and build a list of criteria
    def criteria = []
    qbetemplate.qbeConfig.qbeForm.each { query_prop_def ->
      if ( ( params[query_prop_def.qparam] != null ) && ( params[query_prop_def.qparam].length() > 0 ) ) {
        criteria.add([defn:query_prop_def, value:params[query_prop_def]]);
      }
    }

    StringWriter sw = new StringWriter()
    sw.write("select o from ${qbetemplate.baseclass} as o")
    def hql_builder_context = [:]
    hql_builder_context.declared_scopes = [:]

    criteria.each { crit ->
      processProperty(hql_builder_context,crit)
      // List props = crit.def..split("\\.")
    }

    log.debug("At end of build, ${hql_builder_context}");
  }

  static def processProperty(hql_builder_context,crit) {
    log.debug("processProperty ${hql_builder_context}, ${crit}");
    switch ( crit.defn.contextTree.ctxtp ) {
      case 'qry':
        processQryContextType(hql_builder_context,crit)
        break;
    }
  }

  static def processQryContextType(hql_builder_context,crit) {
    List proppath = crit.defn.contextTree.prop.split("\\.")
    def scope = 'o'                      // default scope is 'o'
    def prop = proppath.remove(proppath.size() - 1) // The actual property is the last thing in a dotted list
    if ( proppath.size() > 1 ) {
      scope = proppath.join(".")
      if ( hql_builder_context.declared_scopes.containsKey(scope) ) {
        // Already established scope for this context
      }
      else {
        establishScope(hql_builder_context, proppath)
      }
    }
    log.debug("Prop: ${scope} :: ${prop}");
  }

  static def establishScope(hql_builder_context, proppath) {
    log.debug("establishScope ${hql_builder_context} ${proppath}");
  }
}
