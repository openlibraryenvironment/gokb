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
              <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Package']}">Package</g:link></li>
              <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Org']}">Org</g:link></li>
              <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Platform']}">Platform</g:link></li>
              <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.TitleInstance']}">Title</g:link></li>
              <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.RefdataCategory']}">Refdata Category</g:link></li>
            </ul>
          </div><!--/.well -->
        </div><!--/span-->

        <div id="mainarea" class="span10">
          <div class="well">
            <g:if test="${displaytemplate != null}">
              <g:if test="${displaytemplate.type=='staticgsp'}">
                <g:render template="${displaytemplate.rendername}" contextPath="../apptemplates" model="${[d:displayobj]}"/>
              </g:if>
            </g:if>
          </div>
        </div>

      </div>
    </div>

  </body>
</html>
