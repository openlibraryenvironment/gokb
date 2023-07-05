<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<asset:stylesheet src="gokb/application.css" />
<asset:javascript src="gokb/application.js" />
<title>GOKb: Deprecate Org Transfer</title>
</head>
<body>

  <h1 class="page-header">Deprecate Org</h1>

  <g:if test='${flash.message}'>
    <div class='well'>${flash.message}</div>
  </g:if>

  <div id="mainarea" class="panel panel-default">
    <div class="panel-heading">
      <h3 class="panel-title">Remove Org from all Titles</h3>
      <p> This action will remove the Publisher name from all associated titles and leave the publisher name field empty.  </p>
    </div>
    <div class="panel-body">
      <g:form name="DeprecateOrg" controller="workflow" action="deprecateDeleteOrg" method="post">
        <div class="row">
          <div class="col-md-12">
            <dt>Orgs To Deprecate:</dt>
            <dd>
              <ul>
                <g:each in="${objects_to_action}" var="o">
                  <li>
                    <g:link controller="resource" action="show" id="org.gokb.cred.Org:${o.id}">[${o.id}] - ${o.name}</g:link>
                    <input type="hidden" name="orgsToDeprecate" value="${o.id}"/>
                  </li>
                </g:each>
              </ul>
            </dd>

            <button class="btn btn-success" type="submit">Confirm :: Remove this org from all titles</button>
          </div>
        </div>
      </g:form>
    </div>
  </div>
</body>
</html>

