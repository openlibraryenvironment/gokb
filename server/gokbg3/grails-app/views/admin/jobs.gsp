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
  <div style="margin:10px 0px;text-align:right">
    <button style="margin-left:10px;" class="btn btn-default pull-right" value="Refresh Page" onClick="window.location.reload()">Reload <i class="fa fa-refresh" aria-hidden="true"></i></button>
    <g:form controller="admin" action="cleanJobList">
      <button onClick="clearList()" class="btn btn-default">Clean Job List</button>
    </g:form>
  </div>
  <table class="table table-bordered">
    <thead>
      <tr>
        <th>ID</th>
        <th>Group-ID</th>
        <th>Type</th>
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
          <td>${v.groupId}</td>
          <td>${v.type}</td>
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
            <g:elseif test="${v.isDone()}">
              Done
            </g:elseif>
            <g:else>
              Not Done <g:if test="${v.progress}">(${v.progress}%)</g:if>
            </g:else>
          </td>
          <td>${v.endTime}</td>
          <td><g:if test="${!v.isCancelled() && !v.isDone()}"><g:link controller="admin" action="cancelJob" onclick="return confirm('Are you sure?')" id="${v.uuid}">Cancel</g:link></g:if></td>
        </tr>
        <tr>
          <td colspan="6">
            messages:
            <ul>
              <g:each in="${v.messages}" var="m">
                <g:if test="${m instanceof String}">
                  <li>${m}</li>
                </g:if>
                <g:else>
                  <li>${m?.message}</li>
                </g:else>
              </g:each>
            </ul>
          </td>
        </tr>
      </g:each>
    </tbody>
  </table>
  <asset:script type="text/javascript" >
    function clearList() {

    }
  </asset:script>
</body>
</html>
