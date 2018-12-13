<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<asset:stylesheet src="gokb/application.css" />
<asset:javascript src="gokb/application.js" />
<title>GOKb: Platform Replacement</title>
</head>
<body>

	<g:form controller="workflow" action="processPackageReplacement"
		method="get">
		<h1 class="page-header">Replace Platform</h1>
		<div id="mainarea" class="panel panel-default">
			<div class="panel-body">
				<h3>Update TIPP records and replace the following platform(s)</h3>
				<table class="table table-striped table-bordered no-select-all">
					<thead>
						<tr>
							<th></th>
							<th>Platforms(s)</th>
						</tr>
					</thead>
					<tbody>
						<g:each in="${objects_to_action}" var="o">
							<tr>
								<td><input type="checkbox" name="tt:${o.id}"
									checked="checked" /></td>
								<td>
									${o.name}
								</td>
						</g:each>
					</tbody>
				</table>
				<dl class="dl-horizontal clearfix">
					<dt>With Platform:</dt>
					<dd>
						<div class="input-group">
							<g:simpleReferenceTypedown class="form-control" style="max-width:350px;"
								name="newplatform" baseClass="org.gokb.cred.Platform" filter1="Current"/>
							<div class="input-group-btn">
								<button type="submit" class="btn btn-default">Update</button>
							</div>
						</div>
					</dd>
					<dt>This action will:</dt>
					<dd>
						<ul>
						<li>Replace the platform for all TIPPs currently associated with the selected platforms</li>
						<li>Set the status of selected platforms to "Retired." </li>
						</ul>
					</dd>
				</dl>
			</div>
		</div>
	</g:form>
</body>
</html>

