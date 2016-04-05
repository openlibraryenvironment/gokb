<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<asset:stylesheet src="gokb/application.css" />
<asset:javascript src="gokb/application.js" />
<title>GOKb: Retire Org</title>
</head>
<body>

  <h1 class="page-header">Deprecate Org -- Remove from all titles</h1>

  <g:if test='${flash.message}'>
    <div class='well'>${flash.message}</div>
  </g:if>


  <div id="mainarea" class="panel panel-default">
    <div class="panel-heading">
      <h3 class="panel-title">Remove Org from all Titles</h3>
    </div>
      <g:form name="DeprecateOrg" controller="workflow" action="deprecateDeleteOrg" method="post">
        <input type="hidden" name="orgsToDeprecate" value="${o.id}"/>
        <div class="row">
          <div class="col-md-12">
            <button class="btn btn-success" type="submit">Confirm :: Remove this org from all titles</button>
          </div>
        </div>
      </g:form>
    </div>
  </div>
</body>
</html>

