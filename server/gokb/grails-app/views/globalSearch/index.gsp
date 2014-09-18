<!DOCTYPE html>
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

     <div class="row">
       <div class="col-md-2">
         <div class="facetFilter">
           <g:each in="${facets}" var="facet">
             <div>
               <b>${facet.key}</b>
               <ul>
                 <g:each in="${facet.value}" var="fe">
                   <li>${fe.display}:${fe.count}</li>
                 </g:each>
               </ul>
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
               </tr>
             </thead>
             <tbody>
               <g:each in="${hits}" var="hit">
                 <tr>
                   <td> <g:link controller="resource" action="show" id="${hit.source._id}">${hit.source.name}</g:link> </td>
                   <td> ${hit.source.componentType} </td>
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
