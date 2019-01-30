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


  <div id="mainarea" class="panel panel-default">
    <div class="panel-heading">
      <h3 class="panel-title">Step 1 of 1</h3>
      <p>
This action will replace the existing Publisher in all associated titles with the new Publisher selection
      </p>
    </div>
    <div class="panel-body">
      <g:form name="DeprecateOrg" controller="workflow" action="deprecateOrg" method="post">
        <div class="row">
          <div class="col-md-6">
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
          </div>

          <div class="col-md-6">
            <dl>
              <dt>Org to Use:</dt>
              <dd>
                 <g:simpleReferenceTypedown class="form-control" name="neworg" baseClass="org.gokb.cred.Org" />
              </dd>
            </dl>
            <button class="btn btn-success" type="submit">Deprecate selected titles in favour of this one</button>
          </div>
        </div>

        <div class="row">
          <div class="col-md-12">
          </div>
        </div>
      </g:form>
    </div>
  </div>
</body>
</html>

