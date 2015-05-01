<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: ${displayobj?.getNiceName() ?: 'Component'} 
&lt;${ displayobj?.isEditable() ? 'Editable' : 'Read Only' }&gt; 
&lt;${ displayobj?.isCreatable() ? 'Creatable' : 'Not Creatable' }&gt;
</title>
</head>
<body>
<br/>
<nav class="navbar navbar-inverse">
  <div class="container-fluid">
    <div class="navbar-header">
      <span class="navbar-brand">${displayobj?.getNiceName() ?: 'Component'} : ${displayobj?.id}
                                    <g:if test="${ displayobj?.respondsTo('getDisplayName') && displayobj.getDisplayName()}"> - ${displayobj.getDisplayName()}</g:if>

      </span>
    </div>

    <ul class="nav navbar-nav navbar-right">
      <li><a data-toggle="modal" data-cache="false"
             title="Show History"
             data-remote='<g:createLink controller="fwk" action="history" id="${displayobj.class.name}:${displayobj.id}"/>'
             data-target="#modal"><i class="glyphicon glyphicon-time"></i></a></li>

      <li><a data-toggle="modal" data-cache="false"
             title="Show Notes"
             data-remote='<g:createLink controller="fwk" action="notes" id="${displayobj.class.name}:${displayobj.id}"/>'
             data-target="#modal"><i class="glyphicon glyphicon-comment"></i></a></li>
    </ul>
  </div>
</nav>

	<h1 class="page-header">
		<g:render template="component_heading" contextPath="../apptemplates" model="${[d:displayobj]}" />
	</h1>
	<div id="mainarea" class="panel panel-default">
		<div class="panel-body">
			<g:if test="${displayobj != null}">
			  <g:if test="${ (!displayobj.respondsTo("isEditable")) || displayobj.isEditable() }" >
				  <g:if test="${ !((request.curator != null ? request.curator.size() > 0 : true) || (params.curationOverride == "true")) }" >
				    <div class="col-xs-3 pull-right well">
				      <div class="alert alert-warning">
                <h4>Warning</h4>
                <p>You are not a curator of this component. You can still edit it, but please contact a curator before making major changes.</p>
                <p><g:link class="btn btn-danger" controller="${ params.controller }" action="${ params.action }" id="${ displayobj.className }:${ displayobj.id }" params="${ (request.param ?: [:]) + ["curationOverride" : true] }" >Confirm and switch to edit mode</g:link></p>
              </div>
            </div>
				  </g:if>
					<g:elseif test="${displayobj.respondsTo('availableActions')}">
						<div class="col-xs-3 pull-right well" id="actionControls">
							<g:form controller="workflow" action="action" method="post"
								class='action-form'>
								<h4>Available actions</h4>
								<input type="hidden"
									name="bulk:${displayobj.class.name}:${displayobj.id}"
									value="true" />
								<div class="input-group">
									<select id="selectedAction" name="selectedBulkAction" class="form-control" >
										<option value="">-- Select an action to perform --</option>
										<g:each var="action" in="${displayobj.availableActions()}">
											<option value="${action.code}">
												${action.label}
											</option>
										</g:each>
									</select>
									<span class="input-group-btn">
										<button type="submit" class="btn btn-default" >Go</button>
									</span>
								</div>
							</g:form>
						</div>
					</g:elseif>
			  </g:if>
				<g:if test="${displaytemplate != null}">
					<g:if test="${displaytemplate.type=='staticgsp'}">
						<g:render template="${displaytemplate.rendername}"
							contextPath="../apptemplates"
							model="${[d:displayobj, rd:refdata_properties, dtype:displayobjclassname_short]}" />
					</g:if>
				</g:if>
			</g:if>
			<g:else>
				<h1>Unable to find record in database : Please notify support</h1>
			</g:else>
		</div>
	</div>

  <div id="modal" class="qmodal modal fade modal-wide" role="dialog">
                <div class="modal-dialog">
                        <div class="modal-content">
                                <div class="modal-header">
                                        <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
                                        <h3 class="modal-title">Modal header</h3>
                                </div>
                                <div class="modal-body"></div>
                                <div class="modal-footer">
                                        <button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
                                </div>
                        </div>
                </div>
        </div>

</body>
</html>
