<!DOCTYPE html>
<html>
<head>
<meta name='layout' content='public' />
<title>GOKb: Packages</title>
</head>

<body>

   <nav class="navbar navbar-default" id="primary-nav-bar" role="navigation">
     <div class="container">
       <!-- Brand and toggle get grouped for better mobile display -->
       <div class="navbar-header">
         <button type="button" class="navbar-toggle" data-toggle="collapse" data-target="#primary-nav">
           <span class="sr-only">Toggle navigation</span>
           <span class="icon-bar"></span>
           <span class="icon-bar"></span>
           <span class="icon-bar"></span>
         </button>
         <!-- navbar-brand is hidden on larger screens, but visible when the menu is collapsed -->
         <a class="navbar-brand" href="index.html">GOKb</a>
       </div>

       <div class="collapse navbar-collapse" id="primary-nav">
         <ul class="nav navbar-nav">
           <li class="hidden active"><a href="#page-body"></a></li>
           <li><span>GOKb Packages</span></li>
         </ul>
       </div>


       <div class="row">
         <div class="span12">

           <g:form controller="packages" class="form" role="form" action="index" method="get" params="${params}">

             <input type="hidden" name="offset" value="${params.offset}"/>
             <g:if test="${params.startYear && params.endYear}">
               <input type="hidden" name="startYear" value="${params.startYear}"/>
               <input type="hidden" name="endYear" value="${params.endYear}"/>
             </g:if>
             <if test="${params.filter}">
               <input type="hidden" name="filter" value="${params.filter}"/>
             </if>

             <ul class="nav nav-pills">
               <g:set var="active_filter" value="${params.filter}"/>
               <li class="${(active_filter != 'current')?'active':''}"><g:link action="index">All Packages</g:link></li>
  
               <li class="${active_filter=='current'?'active':''}"><g:link action="index" params="${ [filter:'current',endYear:"[ ${new Date().year +1900} TO 2100]"]}">Current Packages</g:link></li>

  
             </ul>

             <div class="well form-horizontal">
               Search Term: <input name="q" placeholder="Add &quot;&quot; for exact match" value="${params.q}"/>
               Sort: <select name="sort">
                       <option ${params.sort=='sortname' ? 'selected' : ''} value="sortname">Package Name</option>
                       <option ${params.sort=='_score' ? 'selected' : ''} value="_score">Score</option>
                       <option ${params.sort=='lastModified' ? 'selected' : ''} value="lastModified">Last Modified</option>
                     </select>
               Order: <select name="order" value="${params.order}">
                       <option ${params.order=='asc' ? 'selected' : ''} value="asc">Ascending</option>
                       <option ${params.order=='desc' ? 'selected' : ''} value="desc">Descending</option>
                     </select>
   
               <button type="submit" name="search" value="yes">Search</button>
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

       <div class="row">


         <div class="facetFilter span2">
           <g:each in="${facets.sort{it.key}}" var="facet">
             <g:if test="${facet.key != 'type'}">
             <div class="panel panel-default">
               <div class="panel-heading">
                 <h5><g:message code="facet.so.${facet.key}" default="${facet.key}" /></h5>
               </div>
               <div class="panel-body">
                 <ul>
                   <g:each in="${facet.value.sort{it.display}}" var="v">
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
          <table class="table table-striped">
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
                      <g:link controller="packages" action="packageContent" id="${hit.source._id}">${hit.source.name}</g:link>
                      <g:link controller="packages" action="kbart" id="${hit.source._id}">(Download Kbart File)</g:link>
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


      </div> <!-- /.container -->
    </nav>

  <div class="container">

    <div class="row">
      Packages
    </div>

  </div> <!-- /.container -->
</body>
</html>
