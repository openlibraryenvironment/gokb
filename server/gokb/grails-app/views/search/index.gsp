<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>Search</title>
</head>
<body>
  <h1 class="page-header">${qbetemplate.title?:''}</h1>
  <div class="${displayobj != null ? 'col-md-5 ' : ''}" >
    <div id="mainarea" class="panel panel-default">
      <g:if test="${qbetemplate==null}">
      </g:if>
      <g:else>
        <div class="panel-heading">
          <h3 class="panel-title">Search</h3>
        </div>
        <div class="panel-body">
          <g:if test="${(qbetemplate.message != null)}">
            <p style="text-align: center">
              <bootstrap:alert class="alert-info">
                ${qbetemplate.message}
              </bootstrap:alert>
            </p>
          </g:if>

          <g:render template="qbeform"
                    contextPath="."
                    model="${[formdefn:qbetemplate.qbeConfig?.qbeForm, 'hide':(hide),cfg:qbetemplate.qbeConfig]}" />
        </div>
        <!-- panel-body -->
        <g:if test="${recset && !init}">
          <g:render template="qberesult"
                    contextPath="."
                    model="${[qbeConfig:qbetemplate.qbeConfig, rows:recset, offset:offset, jumpToPage:'jumpToPage', det:det, page:page_current, page_max:page_total]}" />
        </g:if>
        <g:elseif test="${!init}">
          <div class="panel-footer">
            <g:render template="qbeempty" contextPath="." />
          </div>
        </g:elseif>
        <g:else>
          <div class="panel-footer">
            <g:render template="qbeinit" contextPath="." />
          </div>
        </g:else>
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
		<li>
                  <a data-toggle="modal"
                     data-cache="false"
                     title="Show History"
                     data-remote='<g:createLink controller="fwk" action="history" id="${displayobj.class.name}:${displayobj.id}"/>'
                     data-target="#modal">
                     <i class="glyphicon glyphicon-time"></i>
                  </a>
                </li>
	
                <li>
                  <a data-toggle="modal"
                     data-cache="false"
                     title="Show Notes"
                     data-remote='<g:createLink controller="fwk" action="notes" id="${displayobj.class.name}:${displayobj.id}"/>'
                     data-target="#modal">
                    <i class="glyphicon glyphicon-comment"></i>
                  </a>
                </li>
	
                <!-- li>
                  <a data-toggle="modal"
                     data-cache="false"
		     title="Show File Attachments"
		     data-remote='<g:createLink controller="fwk" action="attachments" id="${displayobj.class.name}:${displayobj.id}"/>'
		     data-target="#modal"><i class="glyphicon glyphicon-file"></i>
                  </a>
		</li -->

                <g:if test="${ det == 1 }">
                  <li class="disabled">
                    <a class="disabled" href="#" ><i class="glyphicon glyphicon-chevron-left"></i></a>
                  </li>
                </g:if>
                <g:else>
                  <li>
                    <g:link controller="search"
                            title="Previous Record"
                            action="index"
                            params="${params+['det':det-1, offset:((int)((det-2) / max))*max]}">
                      <i class="glyphicon glyphicon-chevron-left"></i>
                    </g:link>
                  </li>
                </g:else>

                <g:if test="${ det == reccount }">
                  <li class="disabled">
                    <a class="disabled" href="#" ><i class="glyphicon glyphicon-chevron-right"></i></a>
                  </li>
                </g:if>
                <g:else>
                  <li>
                    <g:link controller="search"
                            title="Next Record"
                            action="index"
                            params="${params+['det':det+1, offset:((int)(det / max))*max]}">
                      <i class="glyphicon glyphicon-chevron-right"></i>
                    </g:link>
                  </li>
                </g:else>

                <li>
                  <g:link controller="search"
                          title="Close"
                          action="index"
                          params="${params+['det':null]}">
                    <i class="fa fa-times"></i>
                  </g:link>
                </li>
              </ul>
            </div>
          </nav>
          <g:if test="${displaytemplate != null}">
            <g:if test="${displaytemplate.type=='staticgsp'}">
              <h4><g:render template="component_heading" contextPath="../apptemplates" model="${[d:displayobj]}" /></h4>
              <g:render template="${displaytemplate.rendername}"
                        contextPath="../apptemplates"
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
