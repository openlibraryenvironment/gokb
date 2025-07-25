<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<asset:stylesheet src="gokb/application.css" />
<asset:javascript src="gokb/application.js" />
<title>GOKB: Transfer Org Packages</title>
</head>
<body>

  <h1 class="page-header">Transfer Org Packages</h1>


  <div id="mainarea" class="panel panel-default">
    <div class="panel-heading">
      <h3 class="panel-title">Step 1 of 1</h3>
      <p>
        This action will replace the existing Provider in all associated packages with the new Provider selection
      </p>
    </div>
    <div class="panel-body">
      <g:form name="TransferProviderPackages" controller="workflow" action="transferPackages" method="post">
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
              <dt>New Provider:</dt>
              <dd>
                 <g:simpleReferenceTypedown class="form-control" name="neworg" baseClass="org.gokb.cred.Org" />
              </dd>
            </dl>
            <button class="btn btn-success" type="submit">Transfer Packages</button>
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
