<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: Move TIPP</title>
</head>
<body>

	<h1 class="page-header">Move TIPP</h1>
	<div id="mainarea" class="panel panel-default">
		<div class="panel-heading">
			<h3 class="panel-title">Change TIPP context</h3>
		</div>
		<div class="panel-body">
			<g:form controller="workflow" action="processTippMove"
				method="get">
				<g:hiddenField name="ref" value="${ref}" />
				<div class="row">
					<div class="col-md-12">
						<span class="title-merge-info">Please note: All selected TIPPs will have their status set to <b>Deleted</b> and new TIPPs will be created in their stead! Any selected contexts will be used for <b>all</b> selected items.</span>
					</div>
				</div>
				<div class="row">
					<div class="col-md-6">
						<h3>TIPP(s) to transfer</h3>
						<table class="table table-striped table-bordered no-select-all">
							<thead>
								<tr>
									<th></th>
									<th>TIPP ID</th>
									<th>Title</th>
									<th>Package</th>
								</tr>
							</thead>
							<tbody>
							<tbody>
								<g:each in="${objects_to_action}" var="o">
									<tr>
										<td style="width:83px;"><input type="checkbox" name="tt:${o.id}" checked="checked" />
										</td>
										<td>
												<g:hiddenField name="beforeTipps" value="${o.getClassName()+':'+o.id}" />
												${o.id}
												<a id="t_${o.id}" style="cursor:pointer;" class="pull-right" title="Expand" onclick="toggleDetails(${o.id})">
													Details <i class="fas fa-chevron-down"></i>
												</a>
										</td>
										<td>
											${o.title.name}
										</td>
										<td>
											${o.pkg.name}
										</td>
									</tr>
									<g:if test="${o.title.medium.value == 'Journal'}">
										<tr class="ex-${o.id} hidden">
											<td><b>Coverage</b></td>
											<td colspan="3" >
												<table class="table table-striped table-bordered">
													<thead>
														<tr>
															<th>Start Date</th>
															<th>Start Volume</th>
															<th>Start Issue</th>
															<th>End Date</th>
															<th>End Volume</th>
															<th>End Issue</th>
														</tr>
													</thead>
													<tbody>
														<g:each in="${o.coverageStatements}" var="cst">
															<tr>
																<td><g:formatDate date="${cst.startDate}" format="yyyy-MM-dd"/></td>
																<td>${cst.startVolume}</td>
																<td>${cst.startIssue}</td>
																<td><g:formatDate date="${cst.endDate}"/></td>
																<td>${cst.endVolume}</td>
																<td>${cst.endIssue}</td>
															</tr>
														</g:each>
													</tbody>
												</table>
											</td>
										</tr>
									</g:if>
									<tr class="ex-${o.id} hidden">
										<td><b>URL</b></td>
										<td colspan="3">${o.url}</td>
									</tr>
								</g:each>
							</tbody>
						</table>
					</div>

					<asset:script type="text/javascript">
						function toggleDetails(oid) {
							var ex_class = ".ex-" + oid;

							if ( $('.hidden' + ex_class).length ) {
								$(ex_class).removeClass('hidden');
								$('#t_' + oid).children().removeClass('fa-chevron-down').addClass('fa-chevron-up');
							}
							else {
								$(ex_class).addClass('hidden');
								$('#t_' + oid).children().removeClass('fa-chevron-up').addClass('fa-chevron-down');
							}
						}
					</asset:script>

          <div class="col-md-6">

            <h3>New Context:</h3>

						<label>New Title:</label>
						<g:simpleReferenceTypedown class="form-control" name="newtitle" baseClass="org.gokb.cred.TitleInstance" filter1="Current"/>

						<label>New Platform:</label>
						<g:simpleReferenceTypedown class="form-control" name="newplatform" baseClass="org.gokb.cred.Platform" filter1="Current"/>

						<label>New Package:</label>
						<g:simpleReferenceTypedown class="form-control" name="newpackage" baseClass="org.gokb.cred.Package" filter1="Current"/>

						<hr/>

						<div class="input-group-btn">
							<button type="submit" class="btn btn-default pull-right">Update</button>
						</div>
					</div>
				</div>
			</g:form>
		</div>
	</div>
</body>
</html>

