<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="sb-admin"/>
    <title>GOKb: Global Search</title>
  </head>
  <body>
  
  	<h1 class="page-header">
			Global Search
		</h1>
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

   <div class="container">
     <div class="row">
       <div class="col-md-12 well" style="text-align:center;">

         <g:form action="index" method="get">
           <input id="q" name="q" type="text" class="large" value="${params.q}"/><button name="submit" class="btn btn-default btn-sm">Search</button>
         </g:form>

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
               <h3 class="open"><a href="">${facet.key}</a></h3>
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
                 <th>Component Type</th>
                 <th>Component ID</th>
                 <th>Component Name</th>
               </tr>
             </thead>
             <tbody>
               <g:each in="${hits}" var="hit">
                 <tr>
                   <td> ${hit.source.componentType} </td>
                   <td> <g:link controller="resource" action="show" id="${hit.source._id}">${hit.source._id}</g:link> </td>
                   <td> ${hit.source.name} </td>
                 </tr>
               </g:each>
             </tbody>
           </table>
         </div>
       </div>
     </div>
   </div>
  
  </body>
</html>
