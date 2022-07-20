<html>
  <head>
    <meta name="layout" content="sb-admin" />
    <title>GOKb Direct Ingest Service</title>
  </head>
  <body>
    <h1 class="page-header">Direct File Ingest</h1>
    <div class="col-md-6" >
      <div id="mainarea" class="panel panel-default">
        <div class="panel-heading">
          <h3 class="panel-title">Existing Profiles</h3>
        </div>
        <div class="panel-body" style="overflow:auto;">
          <table class="table table-striped table-bordered">
            <thead>
              <tr>
                <th>Package name</th>
                <th>Ingest Profile name</th>
                <th>Source name</th>
                <th>Platform Url</th>
                <th>Package Type</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <g:each in="${existingProfiles}" var="p">
                <tr>
                  <td><g:link controller="ingest" action="profile" id="${p.id}">${p.packageName}</g:link></td>
                  <td>${p.name}</td>
                  <td>${p.source}</td>
                  <td>${p.platformUrl}</td>
                  <td>${p.packageType}</td>
                  <td><g:xEditableRefData owner="${p}" field="status" config='KBComponent.Status' /></td>
                </tr>
              </g:each>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="col-md-6 desktop-only" >
      <div id="newiparea" class="panel panel-default">
        <div class="panel-heading">
          <h3 class="panel-title">New Ingest Profile</h3>
        </div>
        <div class="panel-body">
          <g:form action="addProfile" method="post" enctype="multipart/form-data" params="${params}">
            <div class="form-group">
              <label class="control-label" for='profileName'>Profile Name:</label>
              <input type='text' class='form-control' name='profileName' id='profileName' />
            </div>
            <div class="form-group">
              <label class="control-label" for='packageName'>Package Name:</label>
              <input type='text' class='form-control' name='packageName' id='packageName' />
            </div>
            <div class="form-group">
              <label class="control-label" for='packageType'>Data Format:</label>
              <g:simpleReferenceTypedown class="form-control" name="packageType" baseClass="org.gokb.cred.RefdataValue" filter1="ingest.filetype"/>
            </div>
            <div class="form-group">
              <label class="control-label" for='providerNamespace'>Provider Namespace:</label>
              <g:simpleReferenceTypedown class="form-control" name="providerNamespace" baseClass="org.gokb.cred.IdentifierNamespace" />
            </div>
            <div class="form-group">
              <label class="control-label" for='platformUrl'>Url:</label>
              <input type='text' class='form-control' name='platformUrl' id='platformUrl' value='http://' />
            </div>
            <div class="form-group">
              <label class="control-label">Source</label>
              <g:simpleReferenceTypedown class="form-control" name="sourceId" baseClass="org.gokb.cred.Source"/>
            </div>
            <button type="submit">Add Profile</button>
          </g:form>
        </div>
      </div>
    </div>
  </body>
</html>
