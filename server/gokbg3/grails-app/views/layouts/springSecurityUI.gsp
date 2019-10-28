<%@ page import="org.gokb.cred.RefdataCategory" %>
<!doctype html>
<html class="no-js" lang="">
<head>
	<meta charset="utf-8">
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<meta http-equiv="x-ua-compatible" content="ie=edge">
	<meta name="viewport" content="width=device-width, initial-scale=1">
  <asset:script> var contextPath="${grailsApplication.config.server.contextPath ?: '/gokb'}"; </asset:script>
  <asset:javascript src="gokb/application.grass.js" />
  <asset:stylesheet src="gokb/sb-admin-2.css"/>
  <asset:stylesheet src="gokb/application.css"/>
  <s2ui:stylesheet src='spring-security-ui'/>
  <asset:stylesheet src="gokb/security-styles.css"/>
  <asset:stylesheet src="gokb/themes/${ grailsApplication.config.gokb.theme }/theme.css"/>
<g:layoutHead/>
</head>
<body class="theme-${ grailsApplication.config.gokb.theme }">
  <div id="wrapper">

    <!-- Navigation -->
    <nav class="navbar navbar-default navbar-static-top" role="navigation"
    style="margin-bottom: 0">
      <div class="navbar-header">
        <button type="button" class="navbar-toggle" data-toggle="collapse"
        data-target=".navbar-collapse">
          <span class="sr-only">Toggle navigation</span>
          <span class="icon-bar"></span>
          <span class="icon-bar"></span>
          <span  class="icon-bar"></span>
        </button>
        <g:link uri="/" class="navbar-brand" style="font-weight:bold;">
          <g:message code="gokb.appname" default="GOKb"/> v<g:meta name="info.app.version" />
          <g:if test="${grailsApplication.config.gokb.instance?.description}">
            – ${grailsApplication.config.gokb.instance?.description}
          </g:if>
        </g:link>
      </div>
    <!-- /.navbar-header -->

      <sec:ifLoggedIn>
        <ul class="nav navbar-nav navbar-right">
          <li class="dropdown">
            <a class="dropdown-toggle" data-toggle="dropdown" href="#" style="font-weight:bold;">
              <i class="fa fa-user fa-fw"></i>
              ${request.user?.displayName ?: request.user?.username}
              <i class="fa fa-caret-down fa-fw"></i>
            </a>
            <ul class="dropdown-menu dropdown-user">
              <li class="divider"></li>
              <li><g:link controller="home" action="profile"><i class="fa fa-user fa-fw"></i>  My Profile</g:link></li>
              <li><g:link controller="home" action="preferences"><i class="fa fa-cog fa-fw"></i>  My Preferences</g:link></li>
              <li class="divider"></li>
              <li><g:link controller="logoff"><i class="fa fa-sign-out fa-fw"></i> Logout</g:link></li>
              <li class="divider"></li>
            </ul> <!-- /.dropdown-user -->
          </li>
        <!-- /.dropdown -->
          <li>
            <span style="width:20px;"></span>
          </li>
        </ul>
            <!-- /.navbar-top-links -->
      </sec:ifLoggedIn>

      <g:render template="/navigation/sidebar" />
    </nav>

                <!-- Page Content -->
    <div id="page-wrapper" class="${ params.controller ?: 'default' }-display" >
      <div class="row" >
        <div id="page-content" class="col-lg-12">
          <div>
            <h1 class="page-header" style="font-size:39px;">Admin Console</h1>
            <div id="mainarea" class="panel panel-default">
              <ul class="jd_menu jd_menu_slate">
                <sec:ifAllGranted roles='ROLE_SUPERUSER'>
                <s2ui:menu controller='user'/>
                <s2ui:menu controller='role'/>
                <g:if test='${securityConfig.securityConfigType?.toString() == 'Requestmap'}'><s2ui:menu controller='requestmap'/></g:if>
                <g:if test='${securityConfig.rememberMe.persistent}'><s2ui:menu controller='persistentLogin' searchOnly='true'/></g:if>
                <s2ui:menu controller='registrationCode' searchOnly='true'/>
                <g:if test='${applicationContext.pluginManager.hasGrailsPlugin('springSecurityAcl')}'>
                <li><a class="accessible"><g:message code='spring.security.ui.menu.acl'/></a>
                  <ul>
                    <s2ui:menu controller='aclClass' submenu='true'/>
                    <s2ui:menu controller='aclSid' submenu='true'/>
                    <s2ui:menu controller='aclObjectIdentity' submenu='true'/>
                    <s2ui:menu controller='aclEntry' submenu='true'/>
                  </ul>
                </li>
                </g:if>
                <li><a class="accessible"><g:message code='spring.security.ui.menu.securityInfo'/></a>
                  <ul>
                    <s2ui:menu controller='securityInfo' itemAction='config'/>
                    <s2ui:menu controller='securityInfo' itemAction='mappings'/>
                    <s2ui:menu controller='securityInfo' itemAction='currentAuth'/>
                    <s2ui:menu controller='securityInfo' itemAction='usercache'/>
                    <s2ui:menu controller='securityInfo' itemAction='filterChains'/>
                    <s2ui:menu controller='securityInfo' itemAction='logoutHandlers'/>
                    <s2ui:menu controller='securityInfo' itemAction='voters'/>
                    <s2ui:menu controller='securityInfo' itemAction='providers'/>
                    <s2ui:menu controller='securityInfo' itemAction='secureChannel'/>
                  </ul>
                </li>
                </sec:ifAllGranted>
              </ul>
            </div>
            <div id="s2ui_main">
              <g:set var='securityConfig' value='${applicationContext.springSecurityService.securityConfig}'/>
              <asset:script>
                var loginButtonCaption = "<g:message code='spring.security.ui.login.login'/>";
                var cancelButtonCaption = "<g:message code='spring.security.ui.login.cancel'/>";
                var loggingYouIn = "<g:message code='spring.security.ui.login.loggingYouIn'/>";
                var loggedInAsWithPlaceholder = "<g:message code='spring.security.ui.login.loggedInAs' args='["{0}"]'/>";
              </asset:script>
              <div id="s2ui_content">
                <p/>
                <g:layoutBody/>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
  <asset:javascript src='spring-security-ui.js'/>
  <s2ui:showFlash/>
  <s2ui:deferredScripts/>
</body>
</html>
