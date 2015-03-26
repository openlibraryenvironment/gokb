<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: Group</title>
</head>
<body>
  <h1 class="page-header">${group?.name}</h1>
  <div id="mainarea" class="panel panel-default">

    <g:render template="/search/pagination" model="${[offset_param:"pkg_offset",offset:pkg_offset,records:packages,page:pkg_page,page_max:pkg_page_max,lasthit:(pkg_offset.toInteger() + packages.size()),recset:"something",max:max,params:params,reccount:package_count,hideActions:true]}"/>

    <table class="table table-striped table-condensed table-bordered">
      <thead>
        <tr class="inline-nav">
          <td><b>Package Name</b></td>
          <td><b>Status</b></td>
          <td><b>List verified by</b></td>
          <td><b>List verified date</b></td>
          <td><b>Last Modified</b></td>
          <td><b>Scope</b></td>
          <td><b>ListStatus</b></td>
          <td><b>Number of Titles</b></td>
        </tr>
      </thead>
      <tbody>
        <g:each in="${packages}" var="pkg">
          <tr>
            <td>
        <g:link controller="resource" action="show" id="${pkg?.getClassName()+':'+pkg?.id}">
           ${pkg.name}
        </g:link>
          </td>
            <td>${pkg.status?.value}</td>
            <td>        
            <g:link controller="resource" action="show" id="${pkg?.userListVerifier?.getClassName()+':'+pkg?.userListVerifier?.id}">${pkg.userListVerifier?.displayName} </g:link>
         </td>
            <td>${pkg.listVerifiedDate}</td>
            <td>${pkg.lastModified}</td>
            <td>${pkg.scope?.value}</td>
            <td>${pkg.listStatus?.value}</td>
            <td>${pkg.tipps.size()}</td>
          </tr>
        </g:each>
      </tbody>
    </table>
  </div>

  <h1 class="page-header">${group?.name} Review Tasks</h1>
  <div id="mainarea" class="panel panel-default">
    <g:render template="/search/pagination" model="${[offset_param:"rr_offset",offset:rr_offset,records:rrs,page:rr_page,page_max:rr_page_max,lasthit:(rr_offset.toInteger() + rrs.size()),recset:"something",max:max,params:params,reccount:rr_count,hideActions:true]}"/>

    <table class="table table-striped table-condensed table-bordered">
      <thead>
        <tr>
          <td><b>Allocated to</b></td>
          <td><b>Component</b></td>
          <td><b>Cause</b></td>
          <td><b>Review Request</b></td>
          <td><b>Request Status</b></td>
          <td><b>Days open</b></td>
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
