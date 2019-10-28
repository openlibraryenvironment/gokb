<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb::Review Request Transfer</title>
</head>
<body>
	<h1 class="page-header">Review Request Transfer</h1>
	<div id="mainarea" class="panel panel-default">
		<div class="panel-body">
			<g:form controller="workflow" action="processRRTransfer" method="get">
				<input type="hidden" name="from"
					value="${request.getHeader('referer')}" />
				<div class="row">

					<div class="col-md-6">
						<h4>Transfer the following Review Requests (${objects_to_action.size()} total):</h4>
						<table class="table table-bordered table-striped table-condensed">
							<thead>
								<tr>
									<th></th>
									<th>Status</th>
									<th>Type</th>
									<th>Request</th>
									<th>Cause</th>
								</tr>
							</thead>
							<tbody>
								<g:each in="${objects_to_action}" var="o">
									<tr>
										<td><input type="checkbox" name="tt:${o.id}" checked="true" /></td>
										<td>${o.status}</td>
										<td>${o.stdDesc?.value}</td>
										<td>${o.reviewRequest}</td>
										<td>${o.descriptionOfCause}</td>
									</tr>
								</g:each>
							</tbody>
						</table>
					</div>

					<div class="col-md-6">
						<div class="form-horizontal">
							<div class="form-group">
								<label for="allocUser" class="col-sm-2 control-label">To User</label>
								<div class="col-sm-8">
									<g:simpleReferenceTypedown class="form-control" id="allocUser" name="allocToUser" baseClass="org.gokb.cred.User" />
								</div>
							</div>
							<div class="form-group">
								<label for="note" class="col-sm-2 control-label">Note</label>
								<div class="col-sm-8">
									<textarea rows="5" cols="40" name="note"></textarea>
								</div>
							</div>
						</div>
						<div class="btn-group pull-right" role="group" aria-label="Submit">
							<input type="submit" value="Transfer" class="btn btn-default btn-primary " />
						</div>
					</div>


				</div>
		
			</g:form>
		</div>
	</div>
</body>
</html>

