<!DOCTYPE html>
<html>
<head>
<meta name='layout' content='public' />
<title>GOKb: Packages</title>
</head>

<body>

   <div class="container">
       <div class="row">
         <div class="col-md-12">
           <g:form controller="public" class="form" role="form" action="index" method="get" params="${params}">
             <div class="well form-horizontal">

               <label for="q">Search for packages...</label>
               <div class="input-group">
                 <input type="text" class="form-control" placeholder="Find package like..." value="${params.q}" name="q">
                 <span class="input-group-btn">
                   <button class="btn btn-primary" type="submit" value="yes" name="search"><span class="fa fa-search" aria-hidden="true">Search</span></button>
                 </span>
               </div>

               <p>
                 <g:each in="${['type','endYear','startYear','consortiaName','cpname']}" var="facet">
                   <g:each in="${params.list(facet)}" var="fv">
                     <span class="badge alert-info">${facet}:${fv} &nbsp; <g:link controller="${controller}" action="index" params="${removeFacet(params,facet,fv)}"><i class="icon-remove icon-white"></i></g:link></span>
                   </g:each>
                 </g:each>
               </p>
             </div>
           </g:form>

         </div>
       </div>
   </div>


  <div class="container">
    <div class="row">
      <div class="col-md-2">
           <g:each in="${facets?.sort{it.key}}" var="facet">
             <g:if test="${facet.key != 'type'}">
             <div class="panel panel-default">
               <div class="panel-heading">
                 <h5><g:message code="facet.so.${facet.key}" default="${facet.key}" /></h5>
               </div>
               <div class="panel-body">
                 <ul>
                   <g:each in="${facet.value?.sort{it.display}}" var="v">
                     <li>
                       <g:set var="fname" value="facet:${facet.key+':'+v.term}"/>
 
                       <g:if test="${params.list(facet.key).contains(v.term.toString())}">
                         ${v.display} (${v.count})
                       </g:if>
                       <g:else>
                         <g:link controller="${controller}" action="${action}" params="${addFacet(params,facet.key,v.term)}">${v.display}</g:link> (${v.count})
                       </g:else>
                     </li>
                   </g:each>
                 </ul>
               </div>
             </div>
             </g:if>
           </g:each>
      </div>


      <div class="col-md-10">
          <table class="table table-striped well">
            <thead>
              <tr>
                <th>Package name</th>
                <th>Provider</th>
                <th>Title count</th>
                <th>Last updated</th>
              </tr>
            </thead>
            <tbody>
              <g:each in="${hits}" var="hit">
                <tr>
                  <td>
                      <g:link controller="public" action="packageContent" id="${hit.id}">${hit.source.name}</g:link>
                      <g:link controller="public" action="kbart" id="${hit.id}">(Download Kbart File)</g:link>
                  </td>
                  <td></td>
                  <td></td>
                  <td></td>
                </tr>
              </g:each>
            </tbody>
          </table>
         </div>
      </div>
    </div>
  </div> <!-- /.container -->

</body>
</html>
