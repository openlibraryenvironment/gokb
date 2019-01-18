<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: Jobs</title>
</head>
<body>
  <h1 class="page-header">
  <span class="pull-right">${cms.executorService.executor.activeCount} out of ${cms.executorService.executor.poolSize} threads In use</span>
  Jobs</h1>
  <table class="table table-bordered">
    <thead>
      <tr>
        <th>ID</th>
        <th>Description</th>
        <th>Has Started</th>
        <th>Start Time</th>
        <th>Status</th>
        <th>End Time</th>
        <th>Actions</th>
      </tr>
    </thead>
    <tbody>
      <g:each in="${jobs}" var="k,v">
        <tr class="${k==params.highlightJob?'highlightRow':''}">
          <td rowspan="2">${k}</td>
          <td>${v.description}</td>
          <td>${v.begun}</td>
          <td>${v.startTime}</td>
          <td>
            <g:if test="${v.isCancelled()}">
              Cancelled
            </g:if>
            <g:elseif test="${v.isDone() && v.endTime}">
              Finished
            </g:elseif>
            <g:elseif test="${v.isDone() && v.messages}">
              Error
            </g:elseif>
            <g:elseif test="${v.isDone()}">
              Done
            </g:elseif>
            <g:else>
              Not Done
            </g:else>
          </td>
          <td>${v.endTime}</td>
          <td><g:if test="${!v.isCancelled() && !v.isDone()}"><g:link controller="admin" action="cancelJob" onclick="return confirm('Are you sure?')" id="${v.id}">Cancel</g:link></g:if></td>
        </tr>
        <tr>
          <td colspan="6">
            messages: 
            <ul>
              <g:each in="${v.messages}" var="m">
                <li>${m.message}</li>
              </g:each>
            </ul>
          </td>
        </tr>
      </g:each>
    </tbody>
  </table>
</body>
</html>
