<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>Search <g:if test="${qbetemplate?.title}"></g:if>${qbetemplate?.title}</title>
</head>
<body>
	<g:if test="${qbetemplate}">
		<h1 class="page-header">${qbetemplate?.title ?:''} <g:if test="${refOid}">for <g:link controller="resource" action="show" id="${refOid}">${refName ?: refOid}</g:link></g:if></h1>
	</g:if>
	<g:else>
		<h1 class="page-header">Search</h1>
	</g:else>
	<div class="${displayobj != null ? 'col-md-5 ' : ''}" >
		<div id="mainarea" class="panel panel-default">
			
			<g:if test="${qbetemplate==null}">

				<div class="panel-heading">
					<h3 class="panel-title">
						Please select a resource to search for
					</h3>
				</div>
				<div class="panel-body">
					<g:each in="${session.menus?.search}" var="type,items" status="counter">
						<g:if test="${ counter > 0 }" >
							<div class="divider"></div>
						</g:if>
						<g:each in="${items}" var="item">
							<li><g:link controller="${item.link.controller}" action="${item.link.action}" params="${item.link.params}"> ${item.text} </g:link></li>
						</g:each>
					</g:each>
				</div>
			</g:if>
			<g:else>
				<g:if test="${!params.inline}">
					<div class="panel-heading">
						<h3 class="panel-title">
							Search
						</h3>
					</div>
				</g:if>
				<div class="panel-body">
					<g:if test="${(qbetemplate.message != null)}">
						<p style="text-align: center">
							<bootstrap:alert class="alert-info">
								${qbetemplate.message}
							</bootstrap:alert>
						</p>
					</g:if>
	
					<g:render template="qbeform"
						model="${[formdefn:qbetemplate.qbeConfig?.qbeForm, 'hide':(hide), cfg:qbetemplate.qbeConfig]}" />
				</div>
				<!-- panel-body -->
				<g:if test="${recset && !init}">
					<g:render template="qberesult"
						model="${[qbeConfig:qbetemplate.qbeConfig, rows:new_recset, offset:offset, jumpToPage:'jumpToPage', det:det, page:page_current, page_max:page_total, baseClass:qbetemplate.baseclass]}" />
				</g:if>
				<g:elseif test="${!init && !params.inline}">
					<div class="panel-footer">
						<g:render template="qbeempty" />
					</div>
				</g:elseif>
			</g:else>
	 </div>
  </div>

	<g:if test="${displayobj != null}">
	  <div class="col-md-7 desktop-only" >
			<div class="panel panel-default quickview">
				<div class="panel-heading">
					<h3 class="panel-title">Quick View</h3>
				</div>
				<div class="panel-body">
					<!--class="well"-->
	
					<nav class="navbar navbar-inverse">
						<div class="container-fluid">
							<div class="navbar-header">
								<span class="navbar-brand">Record ${det} of ${reccount}</span>
							</div>
	
							<ul class="nav navbar-nav navbar-right">
								<li><a data-toggle="modal" data-cache="false"
									title="Show History"
									data-remote='<g:createLink controller="fwk" action="history" id="${displayobj.class.name}:${displayobj.id}"/>'
									data-target="#modal"><i class="far fa-clock"></i></a></li>
	
								<li><a data-toggle="modal" data-cache="false"
									title="Show Notes"
									data-remote='<g:createLink controller="fwk" action="notes" id="${displayobj.class.name}:${displayobj.id}"/>'
									data-target="#modal"><i class="fas fa-comment"></i></a></li>
	
								<!-- li>
		                      <a data-toggle="modal" 
		                         data-cache="false" 
		                         title="Show File Attachments"
		                         data-remote='<g:createLink controller="fwk" action="attachments" id="${displayobj.class.name}:${displayobj.id}"/>' 
		                         data-target="#modal"><i class="glyphicon glyphicon-file"></i></a>
		                    </li -->
								
								<g:if test="${ det == 1 }">
									<li class="disabled">
										<a class="disabled" href="#" ><i class="fas fa-chevron-left"></i></a>
									</li>
								</g:if>
								<g:else>
									<li><g:link controller="search" title="Previous Record"
											action="index"
											params="${params+['det':det-1, offset:((int)((det-2) / max))*max]}">
											<i class="fas fa-chevron-left"></i>
										</g:link></li>
								</g:else>
								
								<g:if test="${ det == reccount }">
									<li class="disabled">
										<a class="disabled" href="#" ><i class="fas fa-chevron-right"></i></a>
									</li>
								</g:if>
								<g:else>
									<li><g:link controller="search" title="Next Record"
										action="index"
										params="${params+['det':det+1, offset:((int)(det / max))*max]}">
										<i class="fas fa-chevron-right"></i>
									</g:link></li>
								</g:else>
								
								<li><g:link controller="search" title="Close"
                    action="index"
                    params="${params+['det':null]}">
                    <i class="fa fa-times"></i>
                  </g:link></li>
							</ul>
						</div>
					</nav>
					<g:if test="${displaytemplate != null}">
						<g:if test="${displaytemplate.type=='staticgsp'}">
							<h4><g:render template="/apptemplates/component_heading" model="${[d:displayobj]}" /></h4>
							<g:render template="/apptemplates/${displaytemplate.rendername}"
								model="${[d:displayobj, rd:refdata_properties, dtype:displayobjclassname_short]}" />
	
						</g:if>
					</g:if>
					<g:else>
		                No template currently available for instances of ${displayobjclassname}
						${displayobj as grails.converters.JSON}
					</g:else>
				</div>
			</div>
		</div>
	</g:if>
	<div id="modal" class="qmodal modal fade" role="dialog">
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
