<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: Group</title>
</head>
<body>
  <h1 class="page-header">${group?.name}</h1>
  <div id="mainarea" class="panel panel-default">

    <g:render template="/search/pagination" model="${[s_controller:'group',offset_param:'pkg_offset',offset:pkg_offset,records:packages,jumpToPage:'pkg_jumpToPage',page:pkg_page,page_max:pkg_page_max,lasthit:(pkg_offset.toInteger() + packages.size()),recset:true,max:max,params:params,reccount:package_count,hideActions:true]}"/>

    <table class="table table-striped table-condensed table-bordered">
      <thead style="white-space:nowrap;">
        <tr class="inline-nav">
          <th>
            <g:link params="${params+[pkg_sort:'name',pkg_sort_order:('desc'== params.pkg_sort_order?'asc':'desc')]}">
              Package Name 
              <i class="glyphicon glyphicon-sort"></i>
            </g:link>
              
          </th>
          <th>Status</th>
          <th>List verified by</th>
          <th>List verified date</th>
          <th>
            <g:link params="${params+[pkg_sort:'lastUpdated',pkg_sort_order:('desc'== params.pkg_sort_order?'asc':'desc')]}">
              Last Modified
              <i class="glyphicon glyphicon-sort"></i>
            </g:link>
          </th>
          <th>Scope</th>
          <th>ListStatus</th>
          <th>Number of Titles (TIPPs)</th>
        </tr>
      </thead>
      <tbody>
        <g:each in="${packages}" var="pkg">
          <g:set var="titles" value="${pkg.getTitles().size()}" />
          <tr>
            <td>
              <g:link controller="resource" action="show" id="${pkg?.getClassName()+':'+pkg?.id}">
                ${pkg.name}
              </g:link>
            </td>
            <td>${pkg.status?.value}</td>
            <td>
              <g:if test="${pkg?.userListVerifier}">
                <g:link controller="resource" action="show" id="${pkg.userListVerifier.getClassName()+':'+pkg.userListVerifier.id}">${pkg.userListVerifier.displayName} </g:link>
              </g:if>
            </td>
            <td>${pkg.listVerifiedDate}</td>
            <td>${pkg.lastUpdated}</td>
            <td style="white-space:nowrap;">${pkg.scope?.value}</td>
            <td style="white-space:nowrap;">${pkg.listStatus?.value}</td>
            <td style="white-space:nowrap;">${titles} (${pkg.tipps?.findAll{ it.status.value == 'Current'}?.size() ?: '0'})</td>
          </tr>
        </g:each>
      </tbody>
    </table>
  </div>

  <h1 class="page-header">${group?.name} Review Tasks</h1>
  <div id="mainarea" class="panel panel-default">
    <g:render template="/search/pagination" model="${[s_controller:'group',offset_param:'rr_offset',offset:rr_offset,records:rrs,jumpToPage:'rr_jumpToPage', page:rr_page,page_max:rr_page_max,lasthit:(rr_offset.toInteger() + rrs.size()),recset:true,max:max,params:params,reccount:rr_count,hideActions:true]}"/>

    <table class="table table-striped table-condensed table-bordered">
      <thead>
        <tr>
          <th>
            <g:link params="${params+[rr_sort:'displayName',rr_sort_order:('desc'== params.rr_sort_order?'asc':'desc')]}">
              Allocated To
              <i class="glyphicon glyphicon-sort"></i>
            </g:link>
          </th>
          <th>Component</th>
          <th>Cause</th>
          <th>Review Request</th>
          <th>Request Status</th>
          <th>Days open</th>
        </tr>
      </thead>
      <tbody>
        <g:each in="${rrs}" var="rr">
            <tr>
              <td>
            <g:link controller="resource" action="show" id="${rr.allocatedTo?.getClassName()+':'+rr.allocatedTo.id}">${rr.allocatedTo?.displayName} </g:link>
              </td>
              <td>
  <g:link controller="resource" action="show" id="${rr.componentToReview?.getClassName()+':'+rr.componentToReview?.id}">${rr.componentToReview}</g:link>
       </td>
              <td>${rr.descriptionOfCause}</td>
              <td>${rr.reviewRequest}</td>
              <td>${rr.status}</td>
              <td>${Math.round((new Date().getTime()-rr.dateCreated.getTime())/(1000*60*60*24))}</td>
            </tr>
        </g:each>
      </tbody>
    </table>
  </div>
%{--   <g:link class="display-inline" controller="search" action="index"
          params="[qbe:'g:reviewRequests']"
          id="">Titles in this package</g:link> --}%

</body>
</html>
