<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: Group</title>
</head>
<body>
  <h1 class="page-header">${group?.name} Curated Packages</h1>
  <div id="mainarea" class="panel panel-default">

    <div class="pull-right">Packages 1 to ${Math.min(20,package_count[0])} of ${package_count[0]}</div>&nbsp;<br/>

    <table class="table table-striped">
      <thead>
        <tr>
          <td>Package Name</td>
        </tr>
      </thead>
      <tbody>
        <g:each in="${packages}" var="pkg">
          <tr>
            <td>${pkg.name}</td>
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
