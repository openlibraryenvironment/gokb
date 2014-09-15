<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<asset:stylesheet src="gokb/application.css" />
<asset:javascript src="gokb/application.js" />
<title>GOKb: Title Transfer</title>
</head>
<body>
	<g:form controller="workflow" action="editTitleTransfer"
		id="${params.id}">
		<h1 class="page-header">Title Transfer</h1>
		<div id="mainarea" class="panel panel-default">
			<div class="panel-heading">
				<h3 class="panel-title">Step 2 of 2</h3>
			</div>
			<div class="panel-body">
				<h3>
					${d.activityName}
					-
					${d.status?.value}
				</h3>
				<p>The following titles:</p>
				<ul>
					<g:each in="${titles}" var="title">
						<li>
							${title.name}
						</li>
					</g:each>
				</ul>
				<p>
					Will be transferred from their current publisher to
					${newPublisher.name}. <span style="background-color: #FF4D4D;">Current
						tipps shown with a red background</span> will be deprecated. <span
						style="background-color: #11bb11;">New tipps with a green
						background</span> will be created by this transfer.
				</p>
			</div>
			<table class="table table-striped table-bordered no-select-all">
				<thead>
					<tr>
						<th>Select</th>
						<th>Type</th>
						<th>Title</th>
						<th>Package</th>
						<th>Platform</th>
						<th>Start Date</th>
						<th>Start Volume</th>
						<th>Start Issue</th>
						<th>End Date</th>
						<th>End Volume</th>
						<th>End Issue</th>
						<th>Close</th>
						<th>Review</th>
					</tr>
				</thead>
				<tbody>
					<g:each in="${tipps}" var="tipp">
						<tr
							style="background-color: ${tipp.type=='NEW'?'#4DFF4D':'#FF4D4D'};">
							<td><g:if test="${tipp.type=='CURRENT'}">
									<input name="addto-${tipp.id}" type="checkbox"
										checked="checked" />
								</g:if></td>
							<td>
								${tipp.type}
							</td>
							<td>
								${tipp.title.name}
							</td>
							<td>
								${tipp.pkg.name}
							</td>
							<td>
								${tipp.hostPlatform.name}
							</td>
							<td><g:formatDate
									format="${session.sessionPreferences?.globalDateFormat}"
									date="${tipp.startDate}" /></td>
							<td>
								${tipp.startVolume}
							</td>
							<td>
								${tipp.startIssue}
							</td>
							<td><g:formatDate
									format="${session.sessionPreferences?.globalDateFormat}"
									date="${tipp.endDate}" /></td>
							<td>
								${tipp.endVolume}
							</td>
							<td>
								${tipp.endIssue}
							</td>
							<td><g:if test="${tipp.type=='CURRENT'}">
									<input name="close-${tipp.id}" type="checkbox"
										checked="checked" />
								</g:if></td>
							<td><input name="review-${tipp.id}" type="checkbox"
								checked="checked" /></td>
						</tr>
					</g:each>
				</tbody>
			</table>
			<div class="panel-footer clearfix">
				<g:if test="${d.status?.value=='Active'}">
					<p>Use the following form to indicate the package and platform
						for new TIPPs. Select/De-select TIPPS above to indicate</p>

					<dl class="dl-horizontal clearfix">
						<dt>New Package</dt>
						<dd>
							<g:simpleReferenceTypedown class="form-control" name="Package"
								baseClass="org.gokb.cred.Package" />
						</dd>
						<dt>New Platform</dt>
						<dd>
							<g:simpleReferenceTypedown class="form-control" name="Platform"
								baseClass="org.gokb.cred.Platform" />
						</dd>
						<dt></dt>
						<dd>
							<button type="submit" class="btn btn-default btn-primary btn-sm"
								name="addTransferTipps" value="AddTipps">Add transfer
								tipps</button>
						</dd>
					</dl>
					<div class="btn-group clearfix pull-right">
						<button type="submit"
							class="btn btn-default btn-success btn-sm pull-right"
							name="process" value="process">Process Transfer</button>
						<button type="submit" class="btn btn-default btn-danger btn-sm "
							name="abandon" value="abandon">Abandon Transfer</button>
					</div>
				</g:if>
				<g:else>
	          This activity has been completed.
	        </g:else>
			</div>
		</div>
	</g:form>
</body>
</html>

