<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: Package - Register Webhook</title>
</head>
<body>
	<g:form controller="workflow" action="processCreateWebHook"	method="get" class="webhook-form">
		<h1 class="page-header">Register Webhook</h1>
		<div id="mainarea" class="panel panel-default">
			<div class="panel-body">
				<h3>Register WebHook callbacks for:</h3>
				<table class="table table-striped table-bordered no-select-all">
       		<thead>
       			<tr>
       				<th></th>
       				<th>Package(s)</th>
       			</tr>
       		</thead>
       		<tbody>
       			<g:each in="${objects_to_action}" var="o">
       				<tr>
       					<td>
              		<input type="checkbox" name="tt:${o.id}" checked="checked" />
              	</td>
              	<td>
              		${o.name}
							</td>
            </g:each>
       		</tbody>
       	</table>
			</div>
			<div class="panel-footer" >
				<h4><i class="fa fa-link"></i> Link to Existing hook</h4>
				<dl class="dl-horizontal clearfix">
					<dt>Url</dt>
					<dd>
						<div class="input-group">
							<g:simpleReferenceTypedown name="existingHook" class="form-control" baseClass="org.gokb.cred.WebHookEndpoint" filter1="${request.user?.id}" />
							<div class="input-group-btn" >
								<button type="submit" class="btn btn-default btn-sm">Link</button>
							</div>
						</div>
					</dd>
				</dl>
				<h4><i class="fa fa-link"></i> Link to New hook</h4>
				<dl class="dl-horizontal clearfix">
					<dt>Hook Name</dt>
					<dd>
						<input type="text" class="form-control" name="newHookName" />
					</dd>
					<dt>Url</dt>
					<dd>
						<input type="text" class="form-control" name="newHookUrl" />
					</dd>
					<dt>Auth</dt>
					<dd>
						<select name="newHookAuth" class="form-control" >
							<option value="0">Anonymous (No Auth)</option>
							<option value="1">HTTP(s) Basic</option>
							<option value="2">Signed HTTP Requests</option>
						</select>
					</dd>
					<dt>Principal</dt>
					<dd>
						<input type="text" class="form-control" name="newHookPrin" />
					</dd>
					<dt>Credentials</dt>
					<dd>
						<input type="text" class="form-control" name="newHookCred" />
					</dd>
					<dt></dt>
					<dd>
						<button type="submit" class="btn btn-default btn-sm">Create Web Hook</button>
					</dd>
				</dl>
			</div>
		</div>
	</g:form>
</body>
</html>
