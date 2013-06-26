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
              <li><g:link controller="home" action="index">Home</g:link></li>
              <li class="dropdown">
                <a href="#" class="dropdown-toggle" data-toggle="dropdown">Search</a>
                <ul class="dropdown-menu">
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
              </li>
              <li><g:link controller="create" action="index">Create</g:link></li>
              <li><g:link controller="home" action="showRules">Validation Rules</g:link></li>
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
