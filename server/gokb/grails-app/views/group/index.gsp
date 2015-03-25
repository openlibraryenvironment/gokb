<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: Group</title>
</head>
<body>
  <h1 class="page-header">${group?.name}</h1>
  <div id="mainarea" class="panel panel-default">

    Group Curated Packages - 1 to ${Math.min(20,package_count[0])} of ${package_count[0]}

    <table class="table table-striped">
      <thead>
        <tr>
          <td>Package Name</td>
          <td>Status</td>
          <td>List verified by</td>
          <td>List verified date</td>
          <td>Last Modified</td>
          <td>Scope</td>
          <td>listStatus</td>
          <td>Number of Titles</td>
        </tr>
      </thead>
      <tbody>
        <g:each in="${packages}" var="pkg">
          <tr>
            <td>${pkg.name}</td>
            <td>${pkg.status?.value}</td>
            <td>${pkg.userListVerifier?.displayName}</td>
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
    <table class="table table-striped">
      <thead>
        <tr>
          <td></td>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td></td>
        </tr>
      </tbody>
    </table>
  </div>

</body>
</html>
