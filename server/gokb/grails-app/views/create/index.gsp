<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle,bootstrap-popover"/>
    <title>GOKb</title>
  </head>
  <body>

    <div class="container-fluid">
      <div class="row-fluid">
        <div id="sidebar" class="span2">
          <div class="well sidebar-nav">
            <ul class="nav nav-list">
              <li class="nav-header">Create New..</li>
              <li><g:link controller="create" action="index" params="${[qbe:'g:packages']}">Package</g:link></li>
              <li><g:link controller="create" action="index" params="${[qbe:'g:orgs']}">Org</g:link></li>
              <li><g:link controller="create" action="index" params="${[qbe:'g:platforms']}">Platform</g:link></li>
              <li><g:link controller="create" action="index" params="${[qbe:'g:titles']}">Title</g:link></li>
              <li><g:link controller="create" action="index" params="${[qbe:'g:refdataCategories']}">Refdata Category</g:link></li>
            </ul>
          </div><!--/.well -->
        </div><!--/span-->

        <div id="mainarea" class="${displayobj != null ? 'span5' : 'span10'}">
          <div class="well">
            <g:if test="${qbetemplate==null}">
              Please select a template from the navigation menu
            </g:if>
          </div>
        </div>

      </div>
    </div>

  </body>
</html>
