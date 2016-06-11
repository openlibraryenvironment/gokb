<!DOCTYPE html>
<html>
  <head>
    <meta name='layout' content='sb-admin'/>
    <title>GOKb: User Dashboard</title>
  </head>
  <body>
    <h1 class="page-header">${request.user?.displayName ?: request.user?.username}</h1>
    <div class="container">
      <div class="row">
        <div class="col-md-8">
        </div>
        <div class="col-md-4">
          <div class="panel panel-default">
            <div class="panel-heading clearfix">
              <div class="btn-group pull-right">
                <g:link controller="create" action="index" params="[tmpl:'org.gokb.cred.CuratoryGroup']" class="btn btn-success btn-small pull-right" >New Group</g:link>
              </div>
              <h3 class="panel-title">Organisational / curatory groups you are a member of</h3>
            </div>
            <div class="panel-body">
              <table class="table table-striped">
                <thead>
                  <tr>
                    <th>Group Name</th>
                    <th>Owner</th>
                  </tr>
                </thead>
                <tbody>
                  <g:each in="${request.user.curatoryGroups}" var="cg">
                    <tr>
                      <td>${cg.name}</td>
                      <td>${cg.owner?:''}</td>
                    </tr>
                  </g:each>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  </body>
</html>
