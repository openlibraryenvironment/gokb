package com.k_int

import groovy.util.logging.*
import org.gokb.cred.*;

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
  public static def build(grailsApplication, qbetemplate, params, result, target_class) {
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

    def baseclass = target_class.getClazz()
    criteria.each { crit ->
      processProperty(hql_builder_context,crit,baseclass)
      // List props = crit.def..split("\\.")
    }

    log.debug("At end of build, ${hql_builder_context}");
  }

  static def processProperty(hql_builder_context,crit,baseclass) {
    log.debug("processProperty ${hql_builder_context}, ${crit}");
    switch ( crit.defn.contextTree.ctxtp ) {
      case 'qry':
        processQryContextType(hql_builder_context,crit,baseclass)
        break;
    }
  }

  static def processQryContextType(hql_builder_context,crit, baseclass) {
    processQryContextType(hql_builder_context, crit, 'o', baseclass)
  }

  static def processQryContextType(hql_builder_context,crit, parent_scope, the_class) {
    List proppath = crit.defn.contextTree.prop.split("\\.")

    // Get all the combo properties defined on the class.
    def allProps = KBComponent.getAllComboPropertyDefinitionsFor(the_class)

    if ( proppath.size() > 1 ) {
      def head = proppath.remove(0)
      def newscope = parent_scope+'.'+head
      if ( hql_builder_context.declared_scopes.containsKey(newscope) ) {
        // Already established scope for this context
      }
      else {
        establishScope(hql_builder_context, newscope, parent_scope, head)
      }
    }
    else {
      // If this is an ordinary property, add the operation. If it's a special, the make the extra joins
      Class target_class = allProps[proppath[0]]
      if ( target_class ) {
        // Combo property...
        boolean incoming = KBComponent.lookupComboMappingFor (the_class, Combo.MAPPED_BY, proppath[0])
        log.debug("combo property, incoming=${incoming}");
      }
    }
  }

  static def establishScope(hql_builder_context, proppath, parent_scope, property_to_join) {
    log.debug("establishScope ${hql_builder_context} ${proppath}");
    def newscope_name = parent_scope+'.'+property_to_join
    hql_builder_context.declared_scopes[newscope_name] = "join ${parent_scope}.${property_to_join} as ${newscope_name}" 
  }

}
