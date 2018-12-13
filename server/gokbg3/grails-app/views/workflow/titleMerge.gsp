<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="sb-admin" />
    <asset:stylesheet src="gokb/application.css" />
    <asset:javascript src="gokb/application.js" />
    <title>GOKb: Title Merge</title>
  </head>
  <body>
    <h1 class="page-header">Title Merge</h1>
    <div id="mainarea" class="panel panel-default">
      <div class="panel-heading">
        <h3 class="panel-title">Step 1 of 2</h3>
      </div>
      <div class="panel-body">
        <g:form name="ReconcileForm" controller="workflow" action="startTitleMerge" method="get">
          <div class="row">
            <div class="col-md-6">
              <h3>Title(s) to be replaced:</h3>
                <ul>
                  <g:each in="${objects_to_action}" var="ota">
                    <li>
                      <g:hiddenField name="beforeTitles" value="${ota.getClassName()+':'+ota.id}" />
                      <g:link controller="resource" action="show" id="${ota.getClassName()+':'+ota.id}" >${ota.name}</g:link>
                    </li>
                  </g:each>
                </ul>
            </div>
            <div class="col-md-6">
              <h3>Title to replace them:</h3>
              <g:simpleReferenceTypedown class="form-control" name="newTitle" baseClass="org.gokb.cred.TitleInstance" />
              <div style="margin-top:50px;">
                <button type="submit" class="btn btn-default pull-right">Next</button>
              </div>
            </div>
          </div>
        </g:form>
      </div>
    </div>
  </body>
</html>
