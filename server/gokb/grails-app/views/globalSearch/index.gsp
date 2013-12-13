<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle"/>
    <title>GOKb</title>
  </head>
  <body>

   <div class="container-fluid">
     <div class="row-fluid">

       <g:form action="index">
       <input id="q" name="q" type="text" class="large" value="${params.q}"/><input type="submit"/>
       </g:form>

       <g:if test="${resultsTotal != null}">
       Search returned ${resultsTotal}
       </g:if>
       <g:else> 
         Not returned
       </g:else>

     </div>
     <div class="row-fluid">
       <div class="span2">
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
       <div class="span10">
         <div id="resultsarea">
           <ul>
             <g:each in="${hits}" var="hit">
               <li>
                 ${hit.source.name} ${hit.source.componentType} ${hit.source._id}
               </li>
             </g:each>
           </ul>
         </div>
       </div>
     </div>
   </div>
  
  </body>
</html>
