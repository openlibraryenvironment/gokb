<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<asset:stylesheet src="gokb/application.css" />
<asset:javascript src="gokb/application.js" />
<title>GOKB: Platform Replacement</title>
</head>
<body>

		<h1 class="page-header">Replace Platform Complete</h1>
		<div id="mainarea" class="panel panel-default">
			<div class="panel-body">
				<h3>Process result:</h3>
			<ul>
				<li>${result['count']} TIPPs have successfully been updated to platform ${result['new']}.</li>
				<li> The following platforms have been merged and deleted:
					<ul>
						<g:each in="${result['old']}" var="o">
							<li><g:link controller="resource" action="show" id="org.gokb.cred.Org:${o.id}">[${o.id}] - ${o.name}</g:link></li>
						</g:each>

					</ul>
				</li>
			</ul>
			</div>
		</div>
</body>


</html>
