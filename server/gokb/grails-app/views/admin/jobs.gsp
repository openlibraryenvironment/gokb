<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: Jobs</title>
</head>
<body>
  <h1 class="page-header">Jobs</h1>
  <table class="table table-bordered">
    <thead>
      <tr>
        <th>ID</th>
        <th>Description</th>
        <th>Has Started</th>
        <th>Start Time</th>
        <th>Progress%</th>
      </tr>
    </thead>
    <tbody>
      <g:each in="${jobs}" var="k,v">
        <tr class="${k==params.highlightJob?'highlightRow':''}">
          <td>${k}</td>
          <td>${v.description}</td>
          <td>${v.begun}</td>
          <td>${v.startTime}</td>
          <td>${v.progress}</td>
        </tr>
      </g:each>
    </tbody>
  </table>
</body>
</html>
