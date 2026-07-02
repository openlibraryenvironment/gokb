<!DOCTYPE html>
<html>
  <head>
    <meta name='layout' content='sb-admin'/>
    <title>GOKB: User Dashboard</title>
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
        </div>
      </div>
    </div>
  </body>
</html>
