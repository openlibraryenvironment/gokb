
<!DOCTYPE html>
<%
  def addFacet = { params, facet, val ->
    def newparams = [:]
    newparams.putAll(params)

    newparams.remove('offset');
    newparams.remove('max');

    def current = newparams[facet]
    if ( current == null ) {
      newparams[facet] = val
    }
    else if ( current instanceof String[] ) {
      newparams.remove(current)
      newparams[facet] = current as List
      newparams[facet].add(val);
    }
    else {
      newparams[facet] = [ current, val ]
    }
    newparams
  }

  def removeFacet = { params, facet, val ->
    def newparams = [:]
    newparams.putAll(params)
    def current = newparams[facet]

    newparams.remove('offset');
    newparams.remove('max');

    if ( current == null ) {
    }
    else if ( current instanceof String[] ) {
      newparams.remove(current)
      newparams[facet] = current as List
      newparams[facet].remove(val);
    }
    else if ( current?.equals(val.toString()) ) {
      newparams.remove(facet);
    }
    newparams
  }
%>

<html>
  <head>
    <meta name="layout" content="sb-admin"/>
    <title>GOKb: Global Search</title>
  </head>
  <body>
  
    <h1 class="page-header">Global Search</h1>

    <div id="mainarea" class="panel panel-default">
      <div class="panel-body">
        <g:form action="index" method="get">           
           <div class="input-group">
            <input type="text" name="q" id="q" class="form-control" value="${params.q}" placeholder="Search for..." />
            <span class="input-group-btn">
              <button type="submit" class="btn btn-default" >Search</button>
            </span>
          </div>
         </g:form>
      </div>

      <div class="panel-footer" >
        <g:if test="${resultsTotal != null}">
          Search returned ${resultsTotal}
        </g:if>
        <g:else>
          Please enter criteria above (* to search all)
        </g:else>
      </div>
    </div>
      <p>
        <g:each in="${['componentType']}" var="facet">
          <g:each in="${params.list(facet)}" var="fv">
            <span class="badge alert-info">${facet}:${fv} &nbsp; <g:link controller="${controller}" action="${action}" params="${removeFacet(params,facet,fv)}"><i class="fa fa-times"></i></g:link></span>
          </g:each>
        </g:each>
      </p> 
     <div class="row">

       <div class="col-md-2">
         <div class="facetFilter">
          <g:each in="${facets}" var="facet">
            <div class="panel panel-default">
              <div class="panel-heading">
                <b><g:message code="facet.so.${facet.key}" default="${facet.key}" /></b>
              </div>
              <div class="panel-body">
                <ul>
                  <g:each in="${facet.value}" var="v">
                    <li>
                      <g:set var="fname" value="facet:${facet.key+':'+v.term}"/>

                      <g:if test="${params.list('componentType').contains(v.term.toString())}">
                        ${v.display} (${v.count})
                      </g:if>
                      <g:else>
                        <g:link controller="${controller}" action="${action}" params="${addFacet(params,'componentType',v.term)}">${v.display}</g:link> (${v.count})
                      </g:else>
                    </li>
                  </g:each>
                </ul>
              </div>
            </div>
          </g:each>
         </div>
       </div>

       <div class="col-md-10">

         <div id="resultsarea">
           <table class="table table-striped table-bordered">
             <thead>
               <tr>
                 <th>Component Name</th>
                 <th>Component Type</th>
                 <th style="width:10%">Status</th>
               </tr>
             </thead>
             <tbody>
               <g:each in="${hits}" var="hit">
                <g:set var="hitInst" value="${org.gokb.cred.KBComponent.get(hit.id.split(':')[1].toLong())}" />
                 <tr>
                   <td> <g:if test="${hitInst}"><g:link controller="resource" action="show" id="${hit.id}">${hit.source.name ?: "- Not Set -"}</g:link></g:if><g:else>${hit.source.name ?: "- Not Set -"}</g:else></td>
                   <td> ${hit.source.componentType} </td>
                   <td> ${hitInst?.status?.value ?: 'Unknown'}</td>
                 </tr>
               </g:each>
             </tbody>
           </table>

           <div class="pagination" style="text-align:center">
             <g:if test="${resultsTotal?:0 > 0 }" >
               <g:paginate  controller="globalSearch" action="index" params="${params}" next="Next" prev="Prev" max="${max}" total="${resultsTotal}" />
             </g:if>
           </div>
         </div>
       </div>
     </div>

   </div>
  
  </body>
</html>
