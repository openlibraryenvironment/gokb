<!DOCTYPE html>
<!--[if lt IE 7 ]> <html lang="en" class="no-js ie6"> <![endif]-->
<!--[if IE 7 ]>    <html lang="en" class="no-js ie7"> <![endif]-->
<!--[if IE 8 ]>    <html lang="en" class="no-js ie8"> <![endif]-->
<!--[if IE 9 ]>    <html lang="en" class="no-js ie9"> <![endif]-->
<!--[if (gt IE 9)|!(IE)]><!-->
<html lang="en" class="no-js">
<!--<![endif]-->
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
  <title><g:layoutTitle default="GOKb" /></title>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <link rel="shortcut icon"
    href="${resource(dir: 'images', file: 'favicon.ico')}"
    type="image/x-icon">
  
  <g:layoutHead />
  <asset:javascript src="gokb/application.grass.js" />
  <asset:stylesheet src="gokb/sb-admin-2.css"/>
  <asset:stylesheet src="gokb/themes/${ grailsApplication.config.gokb.theme }/theme.css"/>
  <asset:stylesheet src="gokb/application.css"/>
  <asset:script type="text/javascript" src="//cdn.jsdelivr.net/webshim/1.12.4/extras/modernizr-custom.js"></asset:script>
  <asset:script type="text/javascript" src="//cdn.jsdelivr.net/webshim/1.12.4/polyfiller.js"></asset:script>
</head>

<body class="theme-${ grailsApplication.config.gokb.theme }">
  <div class="navbar navbar-default navbar-fixed-top">
    <div class="container-fuid">
      <div class="navbar-header">
        <g:link controller="home" action="index" class="navbar-brand">GOKb ${params} ${error}</g:link>
      </div>
      <div class="navbar-collapse collapse" id="navbar-main">
        <ul class="nav navbar-nav navbar-right">
          <sec:ifLoggedIn>
            <li class="dropdown"><a href="#" class="dropdown-toggle"
              data-toggle="dropdown">
                ${request.user?.displayName?:request.user?.username} <b class="caret"></b>
            </a>
              <ul class="dropdown-menu">
                <li><g:link controller="profile">Profile</g:link></li>
                <li><g:link controller="logout">Logout</g:link></li>
                <li><g:link controller="integration">Integration API</g:link></li>
              </ul></li>
          </sec:ifLoggedIn>
          <sec:ifNotLoggedIn>
            <li>Not logged in</li>
          </sec:ifNotLoggedIn>
        </ul>
        <ul class="nav navbar-nav">
          <li class="dropdown"><a href="#" class="dropdown-toggle"
            data-toggle="dropdown">Search</a>
            <ul class="dropdown-menu">
              <li><g:link controller="globalSearch" action="index">Global Search</g:link></li>
              <g:each in="${session.userPereferences?.mainMenuSections}"
                var="secname,sec">
                <!-- ${secname.toLowerCase()} -->
                <g:each in="${sec}" var="srch">
                  <li class="menu-${secname.toLowerCase()}"><g:link
                      controller="search" action="index"
                      params="${[qbe:'g:'+srch.key]}">
                      ${srch.value.title}
                    </g:link></li>
                </g:each>
                <li class="divider"></li>
              </g:each>
            </ul></li>

          <li class="dropdown"><a href="#" class="dropdown-toggle" data-toggle="dropdown">Create</a>
            <ul class="dropdown-menu">
              <g:if test="${org.gokb.cred.License.isTypeCreatable(false)==true}">
                <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.License']}">License</g:link></li>
              </g:if>
              <g:if test="${org.gokb.cred.Office.isTypeCreatable(false)==true}">
                <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Office']}">Office</g:link></li>
              </g:if>
              <g:if test="${org.gokb.cred.Org.isTypeCreatable(false)==true}">
                <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Org']}">Org</g:link></li>
              </g:if>
              <g:if test="${org.gokb.cred.Package.isTypeCreatable(false)==true}">
                <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Package']}">Package</g:link></li>
              </g:if>
              <g:if test="${org.gokb.cred.Platform.isTypeCreatable(false)==true}">
                <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Platform']}">Platform</g:link></li>
              </g:if>
              <g:if test="${org.gokb.cred.ReviewRequest.isTypeCreatable(false)==true}">
                <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.ReviewRequest']}">Request For Review</g:link></li>
              </g:if>
              <g:if test="${org.gokb.cred.Source.isTypeCreatable(false)==true}">
                <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Source']}">Source</g:link></li>
              </g:if>
              <g:if test="${org.gokb.cred.TitleInstance.isTypeCreatable(false)==true}">
                <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.TitleInstance']}">Title</g:link></li>
              </g:if>
              <g:if test="${org.gokb.cred.Imprint.isTypeCreatable(false)==true}">
                <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Imprint']}">Imprint</g:link></li>
              </g:if>

              <sec:ifAnyGranted roles="ROLE_ADMIN">
                <li class="divider"></li>
                <li><g:link controller="create" action="index"
                    params="${[tmpl:'org.gokb.cred.AdditionalPropertyDefinition']}">Additional Property Definition</g:link></li>
                <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.RefdataCategory']}">Refdata Category</g:link></li>
                <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Territory']}">Territory</g:link></li>
              </sec:ifAnyGranted>
            </ul></li>
          <li class="dropdown"><a href="#" class="dropdown-toggle" data-toggle="dropdown">ToDo</a>
            <ul class="dropdown-menu">
              <li><g:link controller="search" action="index"
                  params="${[qbe:'g:reviewRequests',qp_allocatedto:'org.gokb.cred.User:'+request.user.id]}">My ToDos</g:link></li>
              <li><g:link controller="search" action="index"
                  params="${[qbe:'g:reviewRequests']}">Data Review</g:link></li>
            </ul></li>
          <li><g:link controller="upload" action="index">File Upload</g:link></li>
          <li><g:link controller="masterList" action="index">Master List</g:link></li>
          <li><g:link controller="coreference" action="index">Coreference</g:link></li>
          <sec:ifAnyGranted roles="ROLE_ADMIN">
            <li class="dropdown"><a href="#" class="dropdown-toggle" data-toggle="dropdown">Admin</a>
              <ul class="dropdown-menu">
                <li><g:link controller="admin" action="tidyOrgData">Tidy Orgs Data</g:link></li>
                <li><g:link controller="admin" action="reSummariseLicenses">Regenerate License Summaries</g:link></li>
                <li><g:link controller="admin" action="updateTextIndexes">Update Free Text Indexes</g:link></li>
                <li><g:link controller="admin" action="resetTextIndexes">Reset Free Text Indexes</g:link></li>
                <li><g:link controller="admin" action="masterListUpdate">Force Master List Update</g:link></li>
                <li><g:link controller="admin" action="clearBlockCache">Clear Block Cache (eg Stats)</g:link></li>
                <li><g:link controller="user" action="search">User Management Console</g:link></li>
                <li><g:link controller="home" action="about">About</g:link></li>
              </ul></li>
          </sec:ifAnyGranted>
          <sec:ifNotGranted roles="ROLE_ADMIN">
          <li><g:link controller="coreference" action="index">NotAnAdmin</g:link></li>
          </sec:ifNotGranted>
          <li><g:link controller="coreference" action="index">FlibbleDibbleWibble</g:link></li>
        </ul>
      </div>
    </div>
  </div>
  <div class="container-fuid" >
    <g:layoutBody />
  </div>
  <div class="navbar navbar-default navbar-fixed-bottom">
    <div class="container-fluid">
      <ul class="nav navbar-nav">
        <li><g:link controller="home" action="about">GOKb <g:meta
              name="app.version" /> / build <g:meta name="app.buildNumber" />
          </g:link></li>
      </ul>
      <ul class="nav navbar-nav pull-right">
        <li class="dropdown"><a href="#" class="dropdown-toggle"
          data-toggle="dropdown">Tools <b class="caret"></b>
        </a></li>
      </ul>
    </div>
  </div>

  <g:if test="${(grailsApplication.config.kuali?.analytics?.code instanceof String ) }">
    <g:javascript>
        (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
        (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
        m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
        })(window,document,'script','//www.google-analytics.com/analytics.js','ga');
  
        ga('create', '${grailsApplication.config.kuali.analytics.code}', 'kuali.org');
        ga('send', 'pageview');
      </g:javascript>
  </g:if>
</body>
<r:layoutResources />
</html>
