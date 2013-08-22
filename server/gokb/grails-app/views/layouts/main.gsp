<!DOCTYPE html>
<!--[if lt IE 7 ]> <html lang="en" class="no-js ie6"> <![endif]-->
<!--[if IE 7 ]>    <html lang="en" class="no-js ie7"> <![endif]-->
<!--[if IE 8 ]>    <html lang="en" class="no-js ie8"> <![endif]-->
<!--[if IE 9 ]>    <html lang="en" class="no-js ie9"> <![endif]-->
<!--[if (gt IE 9)|!(IE)]><!--> <html lang="en" class="no-js"><!--<![endif]-->
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <title><g:layoutTitle default="GoKB"/></title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="shortcut icon" href="${resource(dir: 'images', file: 'favicon.ico')}" type="image/x-icon">

    <g:layoutHead/>
    <r:layoutResources />

  </head>

  <body>

    <div class="navbar navbar-fixed-top">
      <div class="navbar-inner">
        <div class="container-fluid">
          <a class="brand" href="#">GOKb</a>
          <div class="nav-collapse">
            <ul class="nav">
              <li class="dropdown">
                <a href="#" class="dropdown-toggle" data-toggle="dropdown">Search</a>
                <ul class="dropdown-menu">
                  <g:each in="${request?.userOptions?.availableSearches}" var="srch">
                    <li><g:link controller="search" action="index" params="${[qbe:'g:'+srch.key]}">${srch.value.title}</g:link></li>
                  </g:each>
                </ul>
              </li>
              <li class="dropdown">
                <a href="#" class="dropdown-toggle" data-toggle="dropdown">Create</a>
                <ul class="dropdown-menu">
                  <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.License']}">License</g:link></li>
                  <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Office']}">Office</g:link></li>
                  <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Org']}">Org</g:link></li>
                  <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Package']}">Package</g:link></li>
                  <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Platform']}">Platform</g:link></li>
                  <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.RefdataCategory']}">Refdata Category</g:link></li>
                  <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Territory']}">Territory</g:link></li>
                  <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.TitleInstance']}">Title</g:link></li>
                  <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.TitleInstancePackagePlatform']}">TIPP</g:link></li>
                  <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Source']}">Source</g:link></li>
                </ul>
              </li>
              <li class="dropdown">
                <a href="#" class="dropdown-toggle" data-toggle="dropdown">ToDo</a>
                <ul class="dropdown-menu">
                  <li><g:link controller="search" action="index" params="${[qbe:'g:reviewRequests']}">Data Review</g:link></li>
                </ul>
              </li>
              <li><g:link controller="coreference" action="index">Coreference</g:link></li>
            </ul>
            <ul class="nav pull-right">
              <sec:ifLoggedIn>
                <li class="dropdown">
                  <a href="#" class="dropdown-toggle" data-toggle="dropdown">${request.user?.displayName?:request.user?.username} <b class="caret"></b></a>
                  <ul class="dropdown-menu">
                    <li><g:link controller="logout">Logout</g:link></li>
                  </ul>
                </li>
              </sec:ifLoggedIn>
              <sec:ifNotLoggedIn>
                <li>Not logged in</li>
              </sec:ifNotLoggedIn>
            </ul>
          </div>
        </div>
      </div>
    </div>

    <div class="navbar-push"></div>

    <g:layoutBody/>
    <g:javascript library="application"/>
    <r:layoutResources />
  </body>
</html>
