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
  <asset:stylesheet src="gokb/themes/${ grailsApplication.config.gokb.theme }/theme.css"/>
  <asset:stylesheet src="gokb/application.css"/>
  <s2ui:stylesheet src='spring-security-ui'/>
  <asset:stylesheet src="gokb/security-styles.css"/>
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

      <div class="navbar-default sidebar" role="navigation">
        <div class="sidebar-nav navbar-collapse">
          <ul class="nav" id="side-menu">
            <sec:ifLoggedIn>
              <li class="${params?.controller == "home" && (params?.action == 'index' || params?.action == 'dashboard') ? 'active' : ''}"><g:link controller="home"><i class="fa fa-dashboard fa-fw"></i> Statistics</g:link></li>
              <li class="${params?.controller == "home" && (params?.action == 'userdash') ? 'active' : ''}"><g:link controller="home" action="userdash"><i class="fa fa-dashboard fa-fw"></i> My Dashboard</g:link></li>
              <li class="${(params?.controller == "search" || params?.controller == "globalSearch")  ? 'active' : ''}"><a href="#"><i class="fa fa-search fa-fw"></i> Search<span class="fa arrow"></span></a>
                <ul class="nav nav-second-level">
                  <li class="sidebar-search">
                    <g:form controller="globalSearch" action="index" method="get">
                      <label for="global-search" class="sr-only">Global Search</label>
                      <div class="input-group custom-search-form">
                        <input id="global-search" name="q" type="text" class="form-control" placeholder="Global Search...">
                        <span class="input-group-btn">
                          <button class="btn btn-default" type="submit">
                            <i class="fa fa-search"></i>
                          </button>
                        </span>
                      </div><!-- /input-group -->
                    </g:form>
                  </li>

                  <li class="divider"></li>
                  <g:each in="${session.menus?.search}" var="type,items" status="counter">
                    <g:if test="${ counter > 0 }" >
                      <li class="divider">${type}</li>
                    </g:if>
                    <g:each in="${items}" var="item">
                      <li class="menu-search-${type}">${ g.link(item.link + item.attr) { "<i class='fa fa-angle-double-right fa-fw'></i> ${item.text}" } }</li>
                    </g:each>
                  </g:each>
                </ul> <!-- /.nav-second-level -->
              </li>
              <g:if test="${session.menus?.create}">
                <li class="${params?.controller == "create" ? 'active' : ''}"><a href="#"><i class="fa fa-plus fa-fw"></i> Create<span class="fa arrow"></span></a>
                  <ul class="nav nav-second-level">
                    <g:each in="${session.menus?.create}" var="type,items" status="counter">
                      <g:if test="${ counter > 0 }" >
                        <li class="divider">${type}</li>
                      </g:if>
                      <g:each in="${items}" var="item">
                        <li class="menu-create-${type}">${ g.link(item.link + item.attr) { "<i class='fa fa-angle-double-right fa-fw'></i> ${item.text}" } }</li>
                      </g:each>
                    </g:each>

                  </ul> <!-- /.nav-second-level -->
                </li>
                <li><g:link controller="welcome"><i class="fa fa-tasks fa-fw"></i> To Do<span class="fa arrow"></span></g:link>

                  <ul class="nav nav-second-level">
                    <li><g:link controller="search" action="index"
                            params="[
                            qbe:'g:reviewRequests',
                            qp_allocatedto:'org.gokb.cred.User:'+request.user.id,
                            qp_status: ('org.gokb.cred.RefdataValue:'+(RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Open').id))
                            ]">
                            <i class="fa fa-angle-double-right fa-fw"></i> My ToDos</g:link></li>
                    <li><g:link controller="search" action="index"
                            params="${[
                            qbe:'g:reviewRequests',
                            qp_status: ('org.gokb.cred.RefdataValue:'+(RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Open').id))
                            ]}"><i class="fa fa-angle-double-right fa-fw"></i>
                            Data Review</g:link></li>
                  </ul>
                </li>
              </g:if>

              <g:if test="${session.curatorialGroups && ( session.curatorialGroups.size() > 0 ) }">
                <li><a href="#"><i class="fa fa-search fa-fw"></i> My Groups<span class="fa arrow"></span></a>
                  <ul class="nav nav-second-level">
                    <g:each in="${session.curatorialGroups}" var="cg">
                      <li><g:link controller="group" action="index" id="${cg.id}">${cg.name}</g:link></li>
                    </g:each>
                  </ul>
                </li>
              </g:if>
              <g:if test="${grailsApplication.config.feature.directUpload}">
                <li class="${params?.controller == "savedItems" ? 'active' : ''}" ><g:link controller="savedItems" action="index"><i class="fa fa-folder fa-fw"></i> Saved Items</g:link></li>
                <sec:ifAnyGranted roles="ROLE_EDITOR, ROLE_CONTRIBUTOR, ROLE_ADMIN, ROLE_SUPERUSER">
                <li class="${params?.controller == "upload" ? 'active' : ''}" ><g:link controller="upload" action="index"><i class="fa fa-upload fa-fw"></i> File Upload</g:link></li>
                </sec:ifAnyGranted>
                <li class="${params?.controller == "ingest" ? 'active' : ''}" ><g:link controller="ingest" action="index"><i class="fa fa-upload fa-fw"></i> Direct Ingest</g:link></li>
              </g:if>

              <li class="${params?.controller == "coreference" ? 'active' : ''}"><g:link controller="coreference" action="index"><i class="fa fa-list-alt fa-fw"></i> Coreference</g:link></li>

              <sec:ifAnyGranted roles="ROLE_ADMIN">
                <li class="${params?.controller == "admin" ? 'active' : ''}"><a href="#"><i class="fa fa-wrench fa-fw"></i> Admin<span class="fa arrow"></span></a>
                  <ul class="nav nav-second-level">
                    <li><g:link controller="user" action="search"><i class="fa fa-angle-double-right fa-fw"></i> User Management Console</g:link></li>
                    <sec:ifAnyGranted roles="ROLE_SUPERUSER">
                    <%-- <li><g:link controller="admin" action="tidyOrgData" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Tidy Orgs Data</g:link></li> --%>
                      <li><g:link controller="admin" action="jobs"><i class="fa fa-angle-double-right fa-fw"></i> Manage Jobs</g:link></li>
                      <li class="divider">Jobs</li>
                      <li><g:link controller="admin" action="reSummariseLicenses" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Regenerate License Summaries</g:link></li>
                      <li><g:link controller="admin" action="updateTextIndexes" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Update Free Text Indexes</g:link></li>
                      <li><g:link controller="admin" action="resetTextIndexes" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Reset Free Text Indexes</g:link></li>
                      <li><g:link controller="admin" action="masterListUpdate" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Force Master List Update</g:link></li>
                      <li><g:link controller="admin" action="clearBlockCache" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Clear Block Cache (eg Stats)</g:link></li>
                      <li><g:link controller="admin" action="recalculateStats" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Recalculate Statistics</g:link></li>
                      <li><g:link controller="admin" action="convertTippCoverages" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Convert old coverage statements</g:link></li>
                      <li><g:link controller="admin" action="cleanup" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Expunge Deleted Records</g:link></li>
                      <li><g:link controller="admin" action="cleanupPlatforms" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Deprecate Platforms Without URLs</g:link></li>
                      <li><g:link controller="admin" action="markInconsistentDates" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Add Reviews for wrong Dates</g:link></li>
                      <li><g:link controller="admin" action="cleanupRejected" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Expunge Rejected Records</g:link></li>
                      <li><g:link controller="admin" action="cleanupOrphanedTipps" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Expunge Orphaned TIPPs</g:link></li>
                      <li><g:link controller="admin" action="ensureUuids" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Ensure UUIDs</g:link></li>
                      <li><g:link controller="admin" action="ensureTipls" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Ensure TIPLs</g:link></li>
                      <li><g:link controller="admin" action="triggerEnrichments" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Trigger enrichments</g:link></li>
                      <li><g:link controller="admin" action="logViewer"><i class="fa fa-angle-double-right fa-fw"></i> Log Viewer</g:link></li>
                    <%--      <li><g:link controller="admin" action="housekeeping" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Housekeeping</g:link></li> --%>
                    </sec:ifAnyGranted>
                    <!--
                    <li><g:link controller="api" action="downloadUpdate"><i class="fa fa-angle-double-right fa-fw"></i> Get Refine Extension</g:link></li>
                    -->
                    <li class="divider"></li>
                    <li><a href="https://github.com/openlibraryenvironment/gokb/wiki/Integration-APIs:-Telling-GOKb-about-new-or-corresponding-resources-and-local-identifiers"><i class="fa fa-database fa-fw"></i> Integration API</a></li>
                  </ul>
                </li>
                <li class="${params?.controller == "home" && params?.action == 'about' ? 'active' : ''}" ><g:link controller="home" action="about"><i class="fa fa-info fa-fw"></i> Operating environment</g:link></li>
              </sec:ifAnyGranted>
              <g:if test="${ grailsApplication.config.gokb.decisionSupport && user.hasRole('ROLE_EDITOR') }">
                <li><g:link controller="decisionSupport"><i class="fa fa-search fa-fw"></i> Decision Support Dashboard</g:link></li>
              </g:if>

            </sec:ifLoggedIn>
            <sec:ifNotLoggedIn>
              <li class="${params?.controller == "home" && params?.action == 'home' ? 'active' : ''}"><g:link controller="home"><i class="fa fa-home fa-fw"></i> Home</g:link></li>
              <li class="${params?.controller == "register" ? 'active' : ''}"><g:link controller="register"><i class="fa fa-edit fa-fw"></i> Register</g:link></li>
              <li class="${params?.controller == "login" ? 'active' : ''}"><g:link controller="login"><i class="fa fa-sign-in fa-fw"></i> Sign in</g:link></li>
            </sec:ifNotLoggedIn>
            <li><a href="https://github.com/openlibraryenvironment/gokb/wiki/API"><i class="fa fa-cogs fa-fw"></i> API Documentation</a></li>
          </ul>
        </div>
            <!-- /.sidebar-collapse -->
      </div>
    <!-- /.navbar-static-side -->
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
