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
            Showing results ${firstrec} to ${lastrec} of ${resultsTotal}

            <p>
              <g:each in="${['provider','curatoryGroups']}" var="facet">
                <g:each in="${params.list(facet)}" var="fv">
                    <g:set var="kbc" value="${fv.startsWith('org.gokb.cred') ? org.gokb.cred.KBComponent.get(fv.split(':')[1].toLong()) : null}" />
                  <span class="badge alert-info">${facet}:${kbc?.name ?: fv} &nbsp; <g:link controller="${controller}" action="index" params="${removeFacet(params,facet,fv)}"><i style="color:white" class="fa fa-times" aria-hidden="true"></i></g:link></span>
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
                <div class="panel-body" style="max-height:300px;overflow:auto;overflow-x:hidden">
                  <ul>
                    <g:each in="${facet.value?.sort{it.display}}" var="v">
                      <li style="margin-left:-5px">
                        <g:set var="fname" value="facet:${facet.key+':'+v.term}"/>
                        <g:set var="kbc" value="${v.term.startsWith('org.gokb.cred') ? org.gokb.cred.KBComponent.get(v.term.split(':')[1].toLong()) : null}" />
                        <g:if test="${params.list(facet.key).contains(v.term.toString())}">
                          ${kbc?.name ?: v.display} (${v.count})
                        </g:if>
                        <g:else>
                          <g:link controller="${controller}" action="${action}" params="${addFacet(params,facet.key,v.term)}">${kbc?.name ?: v.display}</g:link> (${v.count})
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
                <th>Content Type</th>
                <th>Title count</th>
                <th>Last updated</th>
              </tr>
            </thead>
            <tbody>
              <g:each in="${hits}" var="hit">
                <tr>
                  <td>
                      <g:link controller="public" action="packageContent" id="${hit.id}">${hit.source.name}</g:link>
                      <!-- <g:link controller="public" action="kbart" id="${hit.id}">(Download Kbart File)</g:link>-->
                      <g:if test="${hit.source.curatoryGroups?.size() > 0}">
                        <div>(Curated by <g:each in="${hit.source.curatoryGroups}" var="cg" status="i"><g:if test="${i>0}">; </g:if>${cg}</g:each>)</div>
                      </g:if>
                      <g:else>
                        <div>No Curators</div>
                      </g:else>
                  </td>
                  <td>${hit.source.cpname}</td>
                  <td>${hit.source.contentType}</td>
                  <td>${hit.source.titleCount}<g:if test="${hit.source.listStatus != 'Checked'}">*</g:if></td>
                  <td>${hit.source.lastUpdatedDisplay}</td>
                </tr>
              </g:each>
            </tbody>
          </table>

          <div style="font-size:0.8em;">
            <b>*</b> The editing status of this package is marked as 'In Progress'. The number of titles in this package should therefore not be taken as final.
          </div>

          <g:if test="${resultsTotal?:0 > 0 }" >
            <div class="pagination">
              <g:paginate controller="public" action="index" params="${params}" next="&raquo;" prev="&laquo;" max="${max}" total="${resultsTotal}" />
            </div>
          </g:if>

         </div>
      </div>

    </div>

  </div> <!-- /.container -->

</body>
</html>
