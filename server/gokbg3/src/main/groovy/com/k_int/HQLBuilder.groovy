package com.k_int

import groovy.util.logging.*
import org.gokb.cred.*;
import grails.util.GrailsClassUtils
import groovy.util.logging.Slf4j

@Slf4j
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
  public static def build(grailsApplication, 
                          qbetemplate, 
                          params,
                          result, 
                          target_class, 
                          genericOIDService,
                          returnObjectsOrRows='objects') {
    // select o from Clazz as o where 

    // log.debug("build ${params}");

    // Step 1 : Walk through all the properties defined in the template and build a list of criteria
    def criteria = []
    qbetemplate.qbeConfig.qbeForm.each { query_prop_def ->
      if ( ( params[query_prop_def.qparam] != null ) && ( params[query_prop_def.qparam].length() > 0 ) ) {
        criteria.add([defn:query_prop_def, value:params[query_prop_def.qparam]]);
      }
    }

    qbetemplate.qbeConfig.qbeGlobals.each { global_prop_def ->
      // log.debug("Adding query global: ${global_prop_def}");
      // create a contextTree so we can process the filter just like something added to the query tree
      // Is this global user selectable
      
      def interpretedValue = interpretGlobalValue(grailsApplication,global_prop_def)
      
      if( interpretedValue ) {
        if ( global_prop_def.qparam != null) {  // Yes
          if ( params[global_prop_def.qparam] == null ) { // If it's not be set
            if ( global_prop_def.default == 'on' ) { // And the default is set
              // log.debug("Adding prop ${global_prop_def.prop} ${global_prop_def.prop.replaceAll('\\.','_')}");
              criteria.add([defn:[ qparam:global_prop_def.prop.replaceAll('\\.','_'), contextTree:global_prop_def],
                            value:interpretedValue])
            }
          }
          else if ( params[global_prop_def.qparam] == 'on' ) { // It's set explicitly, if its on, add the criteria
            criteria.add([defn:[qparam:global_prop_def.prop.replaceAll('\\.','_'),contextTree:global_prop_def],value:interpretedValue])
          }
        }
        else {
          criteria.add([defn:[qparam:global_prop_def.prop.replaceAll('\\.','_'),contextTree:global_prop_def],value:interpretedValue])
        }
      }else{
        log.error("Could not interpret global filter ${global_prop_def}.. ignoring!")
      }
    }

    def hql_builder_context = new java.util.HashMap();
    hql_builder_context.declared_scopes = [:];
    hql_builder_context.query_clauses = []
    hql_builder_context.bindvars = new java.util.HashMap();
    hql_builder_context.genericOIDService = genericOIDService;
    hql_builder_context.sort = params.sort ?: ( qbetemplate.containsKey('defaultSort') ? qbetemplate.defaultSort : null )
    hql_builder_context.order = params.order ?: ( qbetemplate.containsKey('defaultOrder') ? qbetemplate.defaultOrder : null )

    def baseclass = target_class.getClazz()
    criteria.each { crit ->
      // log.debug("Processing crit: ${crit}");
      processProperty(hql_builder_context,crit,baseclass)
      // List props = crit.def..split("\\.")
    }

    // log.debug("At end of build, ${hql_builder_context}");
    hql_builder_context.declared_scopes.each { ds ->
      // log.debug("Scope: ${ds}");
    }

    hql_builder_context.query_clauses.each { qc ->
      // log.debug("QueryClause: ${qc}");
    }

    String hql = outputHqlWithoutSort(hql_builder_context, qbetemplate)
    // log.debug("HQL: ${hql}");
    // log.debug("BindVars: ${hql_builder_context.bindvars}");

    String count_hql = null; //"select count (distinct o) ${hql}"
    if ( qbetemplate.useDistinct == true ) {
      count_hql = "select count (distinct o.id) ${hql}".toString()
    }
    else {
      count_hql = "select count (*) ${hql}".toString()
    }

    def fetch_hql = null
    if ( returnObjectsOrRows=='objects' ) {
      fetch_hql = "select ${qbetemplate.useDistinct == true ? 'distinct' : ''} o ${hql}".toString()
    }
    else {
      fetch_hql = "select ${buildFieldList(qbetemplate.qbeConfig.qbeResults)} ${hql}".toString()
    }

    // Many SQL variants freak out if you order by on a count(*) query, so only order by for the actual fetch
    if ( hql_builder_context.containsKey('sort' ) && 
         ( hql_builder_context.get('sort') != null ) && 
         ( hql_builder_context.get('sort').length() > 0 ) ) {
      log.debug("Setting sort order to ${hql_builder_context.sort}");
      fetch_hql += " order by o.${hql_builder_context.sort} ${hql_builder_context.order}";
    }


    log.debug("Attempt count qry ${count_hql}");
    log.debug("Attempt qry ${fetch_hql}");
    log.debug("Bindvars ${hql_builder_context.bindvars}");
    def count_start_time = System.currentTimeMillis();
    result.reccount = baseclass.executeQuery(count_hql, hql_builder_context.bindvars,[readOnly:true])[0]

    log.debug("Count completed (${result.reccount}) after ${System.currentTimeMillis() - count_start_time}");

    def query_params = [:]
    if ( result.max )
      query_params.max = result.max;
    if ( result.offset )
      query_params.offset = result.offset

    query_params.readOnly = true;

    def query_start_time = System.currentTimeMillis();
    // log.debug("Get data rows..");
    result.recset = baseclass.executeQuery(fetch_hql, hql_builder_context.bindvars,query_params);
    // log.debug("Returning..");
    log.debug("Fetch completed after ${System.currentTimeMillis() - query_start_time}");
  }

  static def processProperty(hql_builder_context,crit,baseclass) {
    // log.debug("processProperty ${hql_builder_context}, ${crit}");
    switch ( crit.defn.contextTree.ctxtp ) {
      case 'qry':
        processQryContextType(hql_builder_context,crit,baseclass)
        break;
      case 'filter':
        processQryContextType(hql_builder_context,crit,baseclass)
        break;
      default:
        log.error("Unhandled property context type ${crit}");
        break;
    }
  }

  static def processQryContextType(hql_builder_context,crit, baseclass) {
    List l =  crit.defn.contextTree.prop.split("\\.")
    processQryContextType(hql_builder_context, crit, l, 'o', baseclass)
  }

  static def processQryContextType(hql_builder_context,crit, proppath, parent_scope, the_class) {

    // log.debug("processQryContextType.... ${proppath}");

    // Get all the combo properties defined on the class.
    def allProps = KBComponent.getAllComboPropertyDefinitionsFor(the_class)

    log.debug("combo props for ${the_class} are: ${allProps}")

    if ( proppath.size() > 1 ) {
      
      def head = proppath.remove(0)
      def newscope = parent_scope+'_'+head
      if ( hql_builder_context.declared_scopes.containsKey(newscope) ) {
        // Already established scope for this context
        // log.debug("${newscope} already a declared contest");
      }
      else {
        // log.debug("Intermediate establish scope - ${head} :: ${proppath}");
        // We're looking at an intermediate property which needs to add some bind scopes. The property can be a simple 
        // standard association, or it could be a virtual (Combo) property which will need multiple joins.

        // 1. Determine if this is a combo property
        Class target_class = allProps[head]
        if ( target_class ) {
          // Combo... Process
          def intermediate_scope = createComboScope(the_class, head, hql_builder_context, parent_scope)
          // Recurs into this function with the new proppath
          processQryContextType(hql_builder_context,crit, proppath, intermediate_scope, target_class)
          // Now process
        }
        else {
          
          // Target class can be looked up in standard way.
          target_class = GrailsClassUtils.getPropertyType(the_class, head)
          
          // Standard association, just make a bind variable..
          establishScope(hql_builder_context, parent_scope, head, newscope)
          processQryContextType(hql_builder_context,crit, proppath, newscope, target_class)
        }
      }
    }
    else {
      log.debug("head prop...");
      // If this is an ordinary property, add the operation. If it's a special, the make the extra joins
      Class target_class = allProps[proppath[0]]
      if ( target_class ) {
        log.debug("Combo property.....");
        def component_scope_name = createComboScope(the_class, proppath[0], hql_builder_context, parent_scope)
        // Finally, because the leaf of the query path is a combo property, we must be being asked to match on an 
        // object.
        addQueryClauseFor(crit,hql_builder_context,component_scope_name)
      }
      else {
        log.debug("Standard property ${proppath}...");
        // The property is a standard property
        addQueryClauseFor(crit,hql_builder_context,parent_scope+'.'+proppath[0])
      }
    }
  }

  static def createComboScope(the_class, propname, hql_builder_context, parent_scope) {
    // Combo property... We need to establish the target scope, and then add whatever the comparison is
    boolean incoming = KBComponent.lookupComboMappingFor (the_class, Combo.MAPPED_BY, propname)
    // log.debug("combo property, incoming=${incoming}");
    def combo_set_name = incoming ? 'incomingCombos' : 'outgoingCombos'
    def combo_prop_name = incoming ? 'fromComponent' : 'toComponent'

    // Firstly, establish a scope called proppath[0]_combo. This will be the combo link to the desired target
    def combo_scope_name = propname+"_combos"
    if ( ! hql_builder_context.declared_scopes.containsKey(combo_scope_name) ) {
      // log.debug("Adding scope ${combo_scope_name}");
      establishScope(hql_builder_context, parent_scope, combo_set_name, combo_scope_name);
      def combo_type_bindvar = combo_scope_name+"_type"
      def combo_status_bindvar = combo_scope_name+"_status"
      hql_builder_context.query_clauses.add("${combo_scope_name}.type = :${combo_type_bindvar}");
      hql_builder_context.query_clauses.add("${combo_scope_name}.status = :${combo_status_bindvar}");
      hql_builder_context.bindvars[combo_type_bindvar] = RefdataCategory.lookupOrCreate ( "Combo.Type", the_class.getComboTypeValueFor (the_class, propname))
      hql_builder_context.bindvars[combo_status_bindvar] = RefdataCategory.lookup("Combo.Status", "Active")
    }

    def component_scope_name = propname
    if ( ! hql_builder_context.declared_scopes.containsKey(component_scope_name) ) {
      // log.debug("Adding scope ${component_scope_name}");
      establishScope(hql_builder_context, combo_scope_name, combo_prop_name, component_scope_name);
    }

    // Work out what class we are at in the tree and returnt that as the current class

    component_scope_name
  }

  static def establishScope(hql_builder_context, parent_scope, property_to_join, newscope_name) {
    // log.debug("Establish scope ${newscope_name} as a child of ${parent_scope} property ${property_to_join}");
    hql_builder_context.declared_scopes[newscope_name] = "${parent_scope}.${property_to_join} as ${newscope_name}" 
  }

  static def addQueryClauseFor(crit, hql_builder_context, scoped_property) {

    switch ( crit.defn.contextTree.comparator ) {
      case 'eq':
        hql_builder_context.query_clauses.add("${crit.defn.contextTree.negate?'not ':''}${scoped_property} = :${crit.defn.qparam}");
        if ( crit.defn.type=='lookup' ) {
          hql_builder_context.bindvars[crit.defn.qparam] = hql_builder_context.genericOIDService.resolveOID2(crit.value)
        }
        else {
          switch ( crit.defn.contextTree.type ) {
            case 'java.lang.Long':
              hql_builder_context.bindvars[crit.defn.qparam] = Long.parseLong(crit.value)
              break;
            case 'java.lang.Object':
              hql_builder_context.bindvars[crit.defn.qparam] = crit.value
              break;
            default:
              hql_builder_context.bindvars[crit.defn.qparam] = crit.value.toString().trim();
              break;
          }
        }
        break;

      case 'ilike':
        hql_builder_context.query_clauses.add("${crit.defn.contextTree.negate?'not ':''}lower(${scoped_property}) like :${crit.defn.qparam}");
        def base_value = crit.value.toLowerCase().trim()
        if ( crit.defn.contextTree.normalise == true ) {
          base_value = org.gokb.GOKbTextUtils.norm2(base_value)
        }
        hql_builder_context.bindvars[crit.defn.qparam] = ( ( crit.defn.contextTree.wildcard=='L' || crit.defn.contextTree.wildcard=='B') ? '%' : '') +
                                                         base_value +
                                                         ( ( crit.defn.contextTree.wildcard=='R' || crit.defn.contextTree.wildcard=='B') ? '%' : '')
        break;

      case 'like':
        hql_builder_context.query_clauses.add("${crit.defn.contextTree.negate?'not ':''}${scoped_property} like :${crit.defn.qparam}");
        def base_value = crit.value.trim()
        if ( crit.defn.contextTree.normalise == true ) {
          base_value = org.gokb.GOKbTextUtils.norm2(base_value)
        }
        hql_builder_context.bindvars[crit.defn.qparam] = ( ( crit.defn.contextTree.wildcard=='L' || crit.defn.contextTree.wildcard=='B') ? '%' : '') +
                                                         base_value +
                                                         ( ( crit.defn.contextTree.wildcard=='R' || crit.defn.contextTree.wildcard=='B') ? '%' : '')
        break;

      default:
        log.error("Unhandled comparator '${crit.defn.contextTree.comparator}'. crit: ${crit}");
    }
  }

  static def outputHqlWithoutSort(hql_builder_context, qbetemplate) {
    StringWriter sw = new StringWriter()
    sw.write(" from ${qbetemplate.baseclass} as o\n")

    hql_builder_context.declared_scopes.each { scope_name,ds ->
      sw.write(" join ${ds}\n");
    }
    
    if ( hql_builder_context.query_clauses.size() > 0 ) {
      sw.write(" where");
      boolean conjunction=false
      hql_builder_context.query_clauses.each { qc ->
        if ( conjunction ) {
          // output and on second and subsequent clauses
          sw.write(" AND");
        }
        else {  
          conjunction=true
        }
        sw.write(" ");
        sw.write(qc);
      }
    }

    // if ( ( hql_builder_context.sort != null ) && ( hql_builder_context.sort.length() > 0 ) ) {
    //   sw.write(" order by o.${hql_builder_context.sort} ${hql_builder_context.order}");
    // }

    // Return the toString of the writer
    sw.toString();
  }

  static def buildFieldList(defns) {
    def result = new java.io.StringWriter()
    result.write('o.id');
    result.write(',o.class');
    defns.each { defn ->
      result.write(",o.");
      result.write(defn.property);
    }
    result.toString();
  }

  // If a value begins __ then it's a special value and needs to be interpreted, otherwise just return the value
  static def interpretGlobalValue(grailsApplication,prop) {
    // log.debug("interpretGlobalValue(ctx,${value})");
    def result=null;
    if ( prop.cat && prop.cat.size() > 0) {
      def rdc = RefdataCategory.findByDesc(prop.cat)
      
      if ( rdc ) {
        result = RefdataValue.findByOwnerAndValue(rdc, prop.value)
      }else{
        log.error("Could not resolve RefdataCategory for filtering!")
      }
    }
    else {
      switch(prop.value?.toString()) {
        case '__USERID':
          def springSecurityService = grailsApplication.getMainContext().getBean('springSecurityService')
          result=''+springSecurityService?.currentUser?.id;
          break;
        default:
          result=prop.value;
          break;
      }
    }
    // log.debug("Returning ${result} ${result.class.name}");
    return result;
  }
}
