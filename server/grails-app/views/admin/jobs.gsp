<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: Jobs</title>
</head>
<body>
  <h1 class="page-header">Active Scheduled Jobs</h1>
  <ul>
    <g:each in="${scheduledJobs}" var="job">
      <li>${job.jobDetail.key.name}
        <ul>
          <g:each in="${job.mergedJobDataMap}" var="k, v">
            <li>${k}: ${v}</li>
          </g:each>
          <li>
            <g:link controller="admin" action="cancelQuartzJob" id="${job.jobDetail.key.name}" onclick="return confirm('Are you sure you want to interrupt this scheduled job?')">Cancel</g:link>
          </li>
        </ul>
      </li>
    </g:each>
  </ul>
  <h1 class="page-header">Manually Triggered Jobs</h1>
  <div style="margin:10px 0px;text-align:right">
    <button
      style="margin-left:10px;"
      class="btn btn-default pull-right"
      value="Refresh Page"
      onClick="window.location.reload()">
      Reload <i class="fa fa-refresh" aria-hidden="true"></i>
    </button>
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
            <div class="panel-heading" role="tab" id="${k}_messages">
              <h4 class="panel-title">
                <a role="button" class="collapsed" data-toggle="collapse" href="#collapse${k}_messages" aria-expanded="true" aria-controls="collapse${k}_messages">
                  Messages (${v.messages.size()})
                </a>
              </h4>
            </div>
            <div id="collapse${k}_messages" class="panel-collapse collapse in" role="tabpanel" aria-labelledby="${k}_messages">
              <div class="panel-body">
                <div class="container-fluid">
                  <div class="row well">
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
                  </div>
                </div>
              </div>
            </div>

            <g:if test="${v.isDone() && !v.exception && !v.isCancelled()}">
              <div class="panel-heading" role="tab" id="${k}_result">
                <h4 class="panel-title">
                  <a role="button" class="collapsed" data-toggle="collapse" href="#collapse${k}_result" aria-expanded="false" aria-controls="collapse${k}_result">
                    Result
                  </a>
                </h4>
              </div>
              <div id="collapse${k}_result" class="panel-collapse collapse" role="tabpanel" aria-labelledby="${k}_result">
                <div class="panel-body">
                  <div class="container-fluid">
                    <div class="row well">
                      ${v.get()}
                    </div>
                  </div>
                </div>
              </div>
            </g:if>

            <g:if test="${v.exception}">
              <div class="panel-heading" role="tab" id="${k}_exception">
                <h4 class="panel-title">
                  <a role="button" class="collapsed" data-toggle="collapse" href="#collapse${k}_exception" aria-expanded="false" aria-controls="collapse${k}_exception">
                    Exception
                  </a>
                </h4>
              </div>
              <div id="collapse${k}_exception" class="panel-collapse collapse" role="tabpanel" aria-labelledby="${k}_exception">
                <div class="panel-body">
                  <div class="container-fluid">
                    <div class="row well">
                      ${v.exception}
                    </div>
                  </div>
                </div>
              </div>
            </g:if>
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
