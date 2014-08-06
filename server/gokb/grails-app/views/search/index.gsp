<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="main" />
<r:require modules="gokbstyle,bootstrap-popover" />
<title>GOKb Search</title>
<style>
#modal {
  width: 900px; /* SET THE WIDTH OF THE MODAL */
  margin-left: -450px;
}

#modal .modal-body {
  max-height: 800px;
}
</style>
</head>
<body>

  <div class="container-fluid">
    <div class="row-fluid">

      <div id="mainarea" class=" panel panel-default ${displayobj != null ? 'col-xs-6' : 'col-xs-12'}">
        <div class="well">
          <g:if test="${qbetemplate==null}">
          </g:if>
          <g:else>
            <nav class="navbar navbar-inverse" role="navigation">
                <div class="navbar-header">
                  <span class="navbar-brand">${qbetemplate.title?:'Search'}
                  <g:if test="${recset != null}"> : Records ${offset+1} to ${lasthit} of ${reccount}</g:if></span>
                </div>

                  <g:if test="${recset != null}">
                    <ul class="nav navbar-nav pull-right">
                      <g:if test="${qbetemplate.qbeConfig.actions != null}">
                        <li class="dropdown">
                          <a href="#" class="dropdown-toggle" data-toggle="dropdown">Actions <b class="caret"></b> </a>

                          <ul class="dropdown-menu">
                            <g:each in="${qbetemplate.qbeConfig.actions}" var="a">
                              <li>
                                <i class="${a.iconClass}"></i>
                                <g:link controller="workflow" 
                                        action="action" 
                                        params="${[selectedBulkAction:a.code,('bulk:'+qbetemplate.baseclass):'true']}">${a.name}</g:link>
                              </li>
                            </g:each>
                          </ul>
                        </li>
                        <li class="divider-vertical"></li>
                      </g:if>

                      <li><g:link title="Previous Page" controller="search" action="index" params="${params+[offset:(offset-max),det:null]}">
                          <i class="glyphicon glyphicon-chevron-left"></i>
                        </g:link></li>

                      <li></li>

                      <li><g:link title="Next Page" controller="search" action="index" params="${params+[offset:(offset+max),det:null]}">
                          <i class="glyphicon glyphicon-chevron-right"></i>
                        </g:link></li>
                    </ul>
                  </g:if>
            </nav>

            <g:if test="${(qbetemplate.message != null)}">
              <p style="text-align:center"><bootstrap:alert class="alert-info">${qbetemplate.message}</bootstrap:alert></p>
            </g:if>

	    <g:render template="qbeform" contextPath="." model="${[formdefn:qbetemplate.qbeConfig?.qbeForm, 'hide':(hide)]}" />

            <g:if test="${recset}">
              <g:render template="qberesult" contextPath="." model="${[qbeConfig:qbetemplate.qbeConfig, rows:recset, offset:offset, det:det]}" />
            </g:if>
            <g:else>
              <g:render template="qbeempty" contextPath="." />
            </g:else>
          </g:else>
        </div>
      </div>

      <g:if test="${displayobj != null}">
        <div id="resultsarea" class="col-xs-6">
          <div class="well">

            <nav class="navbar navbar-inverse" role="navigation">
              <div class="container-fluid">
                <div class="navbar-header">
                  <span class="navbar-brand"> Record ${det} of ${reccount}</span>
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

                  <!-- li>
                      <a data-toggle="modal" 
                         data-cache="false" 
                         title="Show File Attachments"
                         data-remote='<g:createLink controller="fwk" action="attachments" id="${displayobj.class.name}:${displayobj.id}"/>' 
                         data-target="#modal"><i class="glyphicon glyphicon-file"></i></a>
                    </li -->
                  <li><g:link controller="search" title="Previous Record"
                      action="index"
                      params="${params+['det':det-1, offset:((int)((det-2) / max))*max]}">
                      <i class="glyphicon glyphicon-chevron-left"></i>
                    </g:link></li>
                  <li><g:link controller="search" title="Next Record"
                      action="index"
                      params="${params+['det':det+1, offset:((int)(det / max))*max]}">
                      <i class="glyphicon glyphicon-chevron-right"></i>
                    </g:link></li>
                </ul>
              </div>
            </nav>
            <g:if test="${displaytemplate != null}">
              <g:if test="${displaytemplate.type=='staticgsp'}">
               
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
      </g:if>

    </div>
  </div>

  <div id="modal" class="qmodal modal hide fade" role="dialog">
    <div class="modal-header">
      <button type="button" class="close" data-dismiss="modal"
        aria-hidden="true">×</button>
      <h3 id="myModalLabel">Modal header</h3>
    </div>
    <div class="modal-body"></div>
    <div class="modal-footer">
      <button class="btn btn-default btn-primary btn-sm" data-dismiss="modal" aria-hidden="true">Close</button>
    </div>
  </div>

  <script type="text/javascript">
      $('#modal').on('hidden', function() {
        $(this).data('modal').$element.removeData();
      })
    </script>
</body>
</html>
