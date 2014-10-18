<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<asset:stylesheet src="gokb/application.css" />
<asset:javascript src="gokb/application.js" />
<title>GOKb::Review Request Transfer</title>
</head>
<body>
	<div class="container">
		<g:form controller="workflow" action="processRRTransfer" method="get">
			<input type="hidden" name="from"
				value="${request.getHeader('referer')}" />
			<div class="row">
				<div class="col-md-12 hero well">Review Request Transfer</div>
			</div>
			<div class="row">

				<div class="col-md-6">
					Transfer the following Review Requests:<br />
					<g:each in="${objects_to_action}" var="o">
						<input type="checkbox" name="tt:${o.id}" checked="true" />
						${o.status}
						${o.stdDesc?.value}
						${o.reviewRequest}
						${o.descriptionOfCause}<br />
					</g:each>
					</ul>
				</div>

				<div class="col-md-6">
					To User:
					<g:simpleReferenceTypedown class="form-control" name="allocToUser"
						baseClass="org.gokb.cred.User" />
					<br /> Note:
					<textarea rows="5" cols="40" name="note"></textarea>
					&nbsp;<br /> <input type="submit" value="Transfer ->"
						class="btn btn-default btn-primary btn-sm " />
				</div>


			</div>
			<div class="row">
				<div class="col-md-12">Notes</div>
			</div>
   
		</g:form>
	</div>
</body>
</html>

