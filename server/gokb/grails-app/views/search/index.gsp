<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle,bootstrap-popover"/>
    <title>GOKb</title>
    <style>
      #modal {
	width: 900px; /* SET THE WIDTH OF THE MODAL */
        margin-left:-450px;
      }
      #modal .modal-body {
	max-height: 800px;
      }
    </style>
  </head>
  <body>

    <div class="container-fluid">
      <div class="row-fluid">
        <div id="sidebar" class="span2">
          <div class="well sidebar-nav">
            <ul class="nav nav-list">
              <li class="nav-header">Search In</li>
              <li><g:link controller="search" action="index" params="${[qbe:'g:components']}">Components</g:link></li>
              <li><g:link controller="search" action="index" params="${[qbe:'g:packages']}">Packages</g:link></li>
              <li><g:link controller="search" action="index" params="${[qbe:'g:orgs']}">Orgs</g:link></li>
              <li><g:link controller="search" action="index" params="${[qbe:'g:platforms']}">Platforms</g:link></li>
              <li><g:link controller="search" action="index" params="${[qbe:'g:titles']}">Titles</g:link></li>
              <li><g:link controller="search" action="index" params="${[qbe:'g:rules']}">Rules</g:link></li>
              <li><g:link controller="search" action="index" params="${[qbe:'g:projects']}">Projects</g:link></li>
              <li><g:link controller="search" action="index" params="${[qbe:'g:tipps']}">TIPPs</g:link></li>
              <li><g:link controller="search" action="index" params="${[qbe:'g:refdataCategories']}">Refdata</g:link></li>
            </ul>
          </div><!--/.well -->
        </div><!--/span-->

        <div id="mainarea" class="${displayobj != null ? 'span5' : 'span10'}">
          <div class="well">
            <g:if test="${qbetemplate==null}">
              Please select a template from the navigation menu
            </g:if>
            <g:else>
              <div class="navbar">
                <div class="navbar-inner">
                  <div class="brand">${qbetemplate.title?:'Search'}
                    <g:if test="${recset != null}"> : Records ${offset+1} to ${lasthit} of ${reccount}</g:if>
                  </div>
                  <g:if test="${recset != null}">
                    <ul class="nav pull-right">
                      <li><g:link alt="Previous Page" controller="search" action="index" params="${params+[offset:(offset-max),det:null]}"><i class="icon-chevron-left"></i></g:link></li>
                      <li class="divider-vertical"></li>
                      <li><g:link alt="Next Page" controller="search" action="index" params="${params+[offset:(offset+max),det:null]}"><i class="icon-chevron-right"></i></g:link></li>
                    </ul>
                  </g:if>
                </div>
              </div>
              <g:render template="qbeform" contextPath="." model="${[formdefn:qbetemplate.qbeConfig?.qbeForm]}"/>
              <g:if test="${recset != null}">
                <g:render template="qberesult" contextPath="." model="${[qbeConfig:qbetemplate.qbeConfig, rows:recset, offset:offset, det:det]}"/>
              </g:if>
            </g:else>
          </div>
        </div>

        <g:if test="${displayobj != null}">
          <div id="mainarea" class="span5">
            <div class="well">

              <div class="navbar">
                <div class="navbar-inner">
                  <div class="brand">Record ${det} of ${reccount}</div>
                  <ul class="nav pull-right">

                    <li>
                      <a data-toggle="modal" 
                         data-cache="false" 
                         alt="Show History"
                         data-remote='<g:createLink controller="fwk" action="history" id="${displayobj.class.name}:${displayobj.id}"/>' 
                         data-target="#modal"><i class="icon-time"></i></a>
                    </li>

                    <li>
                      <a data-toggle="modal" 
                         data-cache="false" 
                         alt="Show Notes"
                         data-remote='<g:createLink controller="fwk" action="notes" id="${displayobj.class.name}:${displayobj.id}"/>' 
                         data-target="#modal"><i class="icon-comment"></i></a>
                    </li>

                    <!-- li>
                      <a data-toggle="modal" 
                         data-cache="false" 
                         alt="Show File Attachments"
                         data-remote='<g:createLink controller="fwk" action="attachments" id="${displayobj.class.name}:${displayobj.id}"/>' 
                         data-target="#modal"><i class="icon-file"></i></a>
                    </li -->
                    <li>
                      <g:link controller="search" alt="Previous Record" action="index" params="${params+['det':det-1, offset:((int)((det-2) / max))*max]}"><i class="icon-chevron-left"></i></g:link>
                    </li>
                    <li>
                      <g:link controller="search" alt="Next Record" action="index" params="${params+['det':det+1, offset:((int)(det / max))*max]}"><i class="icon-chevron-right"></i></g:link>
                    </li>
                  </ul></div></div>
              <g:if test="${displaytemplate != null}">
                <g:if test="${displaytemplate.type=='staticgsp'}">
                  <g:render template="${displaytemplate.rendername}" contextPath="../apptemplates" model="${[d:displayobj]}"/>
                </g:if>
              </g:if>
              <g:else>
                No template currenly available for instances of ${displayobjclassname}
                ${displayobj as grails.converters.JSON}
              </g:else>
            </div>
          </div>
        </g:if>

      </div>
    </div>

    <div id="modal" class="qmodal modal hide fade" role="dialog">
      <div class="modal-header">
         <button type="button" class="close" data-dismiss="modal" aria-hidden="true">×</button>
         <h3 id="myModalLabel">Modal header</h3>
       </div>
       <div class="modal-body">
       </div>
       <div class="modal-footer">
         <button class="btn" data-dismiss="modal" aria-hidden="true">Close</button>
       </div>
    </div>

    <script language="javascript">
      $('#modal').on('hidden', function() {
        $(this).data('modal').$element.removeData();
      })
    </script>
  </body>
</html>
