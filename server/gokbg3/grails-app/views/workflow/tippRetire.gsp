<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<asset:stylesheet src="gokb/application.css" />
<asset:javascript src="gokb/application.js" />
<title>GOKb: Retire TIPPs</title>
</head>
<body>

	<g:form controller="workflow" action="processTippRetire"
		method="get">
		<h1 class="page-header">Replace Platform</h1>
		<div id="mainarea" class="panel panel-default">
		<g:hiddenField name="ref" value="${ref}" />
                    <div class="row">
                        <div class="col-sm-6">
                          <div class="panel-body">
				<h3>Retire TIPP records and set their access end date</h3>
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
						<g:each in="${objects_to_action}" var="o">
							<tr>
								<td><input type="checkbox" name="tt:${o.id}"
									checked="checked" /></td>
                                                                <td>
                                                                    <g:hiddenField name="beforeTipps" value="${o.getClassName()+':'+o.id}" />
                                                                    ${o.id}
                                                                </td>
								<td>
                                                                    ${o.title.name}
								</td>
								<td>
                                                                    ${o.pkg.name}
								</td>
                                                        </tr>
                                                        <td></td>
                                                        <td colspan="3">${o.url}</td>
                                                        </tr>
						</g:each>
					</tbody>
				</table>
                          </div>
                        </div>
                        <div class="col-sm-6">
				<dl class="dl-horizontal" style="margin-top:45px">
					<dt>Set access end date:</dt>
					<dd>
						<div class="input-group" style="width:45%">
							<select class="form-control" id="endDateSelect" name="endDateSelect">
                                                          <option value="none" selected>None</option>
                                                          <option value="now">Today</option>
                                                          <option value="select">Selected Date</option>
							</select>

							<div class="input-group-btn">
								<button type="submit" class="btn btn-default">Update</button>
							</div>
						</div>

                                                <div class="select-date" style="margin-top:20px" hidden>
                                                  <input class="form-control" style="width:200px;display:inline" name="selectedDate" type="date"></input>
                                                </div>
					</dd>
				</dl>
                          </div>
                        </div>
                    </div>
		</div>
	</g:form>
        <asset:script type="text/javascript" >
          $('#endDateSelect').change(function() {
              var selVal = $(this).find('option:selected').attr('value');
              if ('select' == selVal) {
                  $('.select-date').show();
              }
              else {
                  $('.select-date').hide();
              }
          });
        </asset:script>
</body>
</html>

