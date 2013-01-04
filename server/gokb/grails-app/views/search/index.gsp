<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle"/>
    <title>GOKb</title>
  </head>
  <body>

    <div class="container-fluid">
      <div class="row-fluid">
        <div id="sidebar" class="span2">
          <div class="well sidebar-nav">
            <ul class="nav nav-list">
              <li class="nav-header">Search In</li>
              <li><g:link controller="search" action="index" params="${[template:'g:components']}">Components</g:link></li>
              <li><g:link controller="search" action="index" params="${[template:'g:packages']}">Packages</g:link></li>
              <li><g:link controller="search" action="index" params="${[template:'g:orgs']}">Orgs</g:link></li>
              <li><g:link controller="search" action="index" params="${[template:'g:platforms']}">Platforms</g:link></li>
              <li><g:link controller="search" action="index" params="${[template:'g:titles']}">Titles</g:link></li>
            </ul>
          </div><!--/.well -->
        </div><!--/span-->
        <div id="mainarea" class="span5">
          Search Area
        </div>
        <div id="mainarea" class="span5">
          Record Display Area
        </div>
      </div>
    </div>
  </body>
</html>
