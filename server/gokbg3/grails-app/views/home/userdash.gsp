<!DOCTYPE html>
<html>
  <head>
    <meta name='layout' content='sb-admin'/>
    <title>GOKb: User Dashboard</title>
  </head>
  <body>
    <h1 class="page-header">${request.user?.displayName ?: request.user?.username}</h1>
    <div class="container-fluid">
      <div class="row">
        <div class="col-md-12">
          <div class="panel panel-default">
            <div class="panel-heading clearfix">
              <h3 class="panel-title">Your Most Recent Review Tasks</h3>
            </div>
            <div class="panel-body">
              <g:link class="display-inline" controller="search" action="index"
                params="[qbe:'g:reviewRequests', qp_allocatedto:'org.gokb.cred.User:' + Long.toString(request.user.id), qp_status:'org.gokb.cred.RefdataValue:' + Long.toString(org.gokb.cred.RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Open').id), inline:true, hide:['qp_project', 'qp_allocatedto']]"
                id="">Your Review Tasks</g:link>
            </div>
          </div>
          <div class="panel panel-default">
            <div class="panel-heading clearfix">
              <h3 class="panel-title">Most recently updated Watched Components</h3>
            </div>
            <div class="panel-body">
              <g:link class="display-inline" controller="search" action="index"
                params="[qbe:'g:UserWatchedComponents', inline:true]"
                id="">User Watched Components</g:link>
            </div>
          </div>
          <div class="panel panel-default">
            <div class="panel-heading clearfix">
              <h3 class="panel-title">Finished Upload Jobs</h3>
            </div>
            <div class="panel-body">
              <g:link class="display-inline" controller="search" action="index"
                params="[qbe:'g:JobResult', inline:true]"
                id="">Finished Upload Jobs</g:link>
            </div>
          </div>
          <g:if test="${request.user.getFolderList()}">
            <div class="panel panel-default">
              <div class="panel-heading">
                <h3 class="panel-title">Your Lists</h3>
              </div>
              <div class="panel-body">
                <table class="table table-striped table-responsive">
                  <thead>
                    <tr>
                      <th>List Name</th>
                      <th>Owner</th>
                      <th>Last Updated</th>
                    </tr>
                  </thead>
                  <tbody>
                    <g:each in="${request.user.getFolderList()}" var="f">
                      <tr>
                        <td><g:link controller="resource" action="show" id="${f.class.name}:${f.id}">${f.name}</g:link></td>
                        <td><g:link controller="resource" action="show" id="${f.owner.class.name}:${f.owner.id}">${f.owner.displayName}</g:link></td>
                        <td>${f.lastUpdated}</td>
                      </tr>
                    </g:each>
                  </tbody>
                </table>
              </div>
            </div>
          </g:if>
        </div>

<%--         <div class="col-md-4">
          <div class="panel panel-default">
            <div class="panel-heading clearfix">
              <h3 class="panel-title">Curatorial groups you are a member of</h3>
            </div>
            <div class="panel-body">
              <table class="table table-striped">
                <thead>
                </thead>
                <tbody>
                  <g:each in="${request.user.curatoryGroups}" var="ucg">
                    <tr>
                      <td style="font-weight:bold;">${ucg.name}</td>
                      <td><g:link controller="resource" action="show" id="org.gokb.cred.CuratoryGroup:${ucg.id}">users</g:link></td>
                      <td><g:link controller="group" action="index" id="${ucg.id}">curated content</g:link></td>
                    </tr>
                  </g:each>
                </tbody>
              </table>
            </div>
          </div>
        </div> --%>

      </div>
    </div>
  </body>
</html>
