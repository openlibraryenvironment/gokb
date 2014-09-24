<!DOCTYPE html>
<!--[if lt IE 7 ]> <html lang="en" class="no-js ie6"> <![endif]-->
<!--[if IE 7 ]>    <html lang="en" class="no-js ie7"> <![endif]-->
<!--[if IE 8 ]>    <html lang="en" class="no-js ie8"> <![endif]-->
<!--[if IE 9 ]>    <html lang="en" class="no-js ie9"> <![endif]-->
<!--[if (gt IE 9)|!(IE)]><!-->
<html lang="en" class="no-js">
<!--<![endif]-->

<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
  <title><g:layoutTitle default="GOKb" /></title>
  
  <link rel="shortcut icon" href="${resource(dir: 'images', file: 'favicon.ico')}" type="image/x-icon">
  <g:layoutHead />
  
	<asset:javascript src="gokb/application.grass.js" />
  <asset:stylesheet src="gokb/sb-admin-2.css"/>
  <asset:stylesheet src="gokb/themes/${ grailsApplication.config.gokb.theme }/theme.css"/>
  <asset:stylesheet src="gokb/application.css"/>
  
  <!-- HTML5 Shim and Respond.js IE8 support of HTML5 elements and media queries -->
  <!-- WARNING: Respond.js doesnt work if you view the page via file:// -->
  <!--[if lt IE 9]>
      <script src="https://oss.maxcdn.com/libs/html5shiv/3.7.0/html5shiv.js"></script>
      <script src="https://oss.maxcdn.com/libs/respond.js/1.4.2/respond.min.js"></script>
  <![endif]-->

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
        <g:link uri="/" class="navbar-brand">
          GOKb v<g:meta name="app.version" />
        </g:link>
      </div>
      <!-- /.navbar-header -->

      <sec:ifLoggedIn>
        <ul class="nav navbar-nav navbar-right">
          <li class="dropdown">
          	<a class="dropdown-toggle" data-toggle="dropdown" href="#">
            	<i class="fa fa-user fa-fw"></i>
            	${request.user?.displayName ?: request.user?.username}
              <i class="fa fa-caret-down fa-fw"></i>
          	</a>
            <ul class="dropdown-menu dropdown-user">
              <li class="divider"></li>
              <li><g:link controller="home" action="profile"><i class="fa fa-user fa-fw"></i>  My Profile</g:link></li>
              <li><g:link controller="home" action="preferences"><i class="fa fa-cog fa-fw"></i>  My Preferences</g:link></li>
              <li class="divider"></li>
              <li><g:link controller="logout"><i class="fa fa-sign-out fa-fw"></i> Logout</g:link></li>
              <li class="divider"></li>
            </ul> <!-- /.dropdown-user --></li>
          <!-- /.dropdown -->
        </ul>
        <!-- /.navbar-top-links -->
      </sec:ifLoggedIn>
      <sec:ifNotLoggedIn>
        <ul class="nav navbar-nav navbar-right">
          <li><g:link controller="register"><i class="fa fa-edit fa-fw"></i> Register</g:link></li>
          <li><g:link controller="login"><i class="fa fa-sign-in fa-fw"></i> Sign in</g:link></li>
        </ul>
        <!-- /.navbar-top-links -->
      </sec:ifNotLoggedIn>
      
      <div class="navbar-default sidebar" role="navigation">
        <div class="sidebar-nav navbar-collapse">
          <ul class="nav" id="side-menu">
            <li class="${params?.controller == "welcome"  ? 'active' : ''}"><g:link controller="welcome"><i class="fa fa-dashboard fa-fw"></i> My Dashboard</g:link></li>
            
            <sec:ifLoggedIn>
              <li class="${params?.controller == "search" || params?.controller == "globalSearch"  ? 'active' : ''}"><a href="#"><i class="fa fa-search fa-fw"></i>Search<span class="fa arrow"></span></a>
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

                  <g:each in="${session.userPereferences?.mainMenuSections}" var="secname,sec">
                    <g:each in="${sec}" var="srch">
                      <li class="menu-${secname.toLowerCase()}"><g:link controller="search" action="index" params="${[qbe:'g:'+srch.key]}" title="Search ${srch.value.title}">
                          <i class="fa fa-angle-double-right fa-fw"></i> ${srch.value.title}
                        </g:link></li>
                    </g:each>
                  </g:each>
                </ul> <!-- /.nav-second-level --></li>
			  <li class="${params?.controller == "create" ? 'active' : ''}"><a href="#"><i class="fa fa-plus fa-fw"></i> Create<span class="fa arrow"></span></a>
                <ul class="nav nav-second-level">

                  <g:each in="${session.userPereferences?.createMenu}" var="d">
                    <li><g:link controller="create" action="index" title="New ${d.displayName}"
                      params="${[tmpl:d.dcName]}"><i class="fa fa-angle-double-right fa-fw"></i> ${d.displayName}</g:link></li>
                  </g:each>

                </ul> <!-- /.nav-second-level --></li>
                <li><g:link controller="welcome"><i class="fa fa-tasks fa-fw"></i> To Do<span class="fa arrow"></span></g:link>

                  <ul class="nav nav-second-level">
                    <li><g:link controller="search" action="index"
                        params="${[qbe:'g:reviewRequests',qp_allocatedto:'org.gokb.cred.User:'+request.user.id]}">
                        <i class="fa fa-angle-double-right fa-fw"></i> My ToDos</g:link></li>
                    <li><g:link controller="search" action="index"
                        params="${[qbe:'g:reviewRequests']}"><i class="fa fa-angle-double-right fa-fw"></i>
                        Data Review</g:link></li>
                  </ul>
              </li>
              <li class="${params?.controller == "upload" ? 'active' : ''}" ><g:link controller="upload" action="index"><i class="fa fa-upload fa-fw"></i> File Upload</g:link></li>
              <li class="${params?.controller == "masterList" ? 'active' : ''}"><g:link controller="masterList" action="index"><i class="fa fa-list-alt fa-fw"></i> Master List</g:link></li>
              <li class="${params?.controller == "coreference" ? 'active' : ''}"><g:link controller="coreference" action="index"><i class="fa fa-list-alt fa-fw"></i> Coreference</g:link></li>

              <sec:ifAnyGranted roles="ROLE_ADMIN">
                <li class="${params?.controller == "admin" ? 'active' : ''}"><a href="#"><i class="fa fa-wrench fa-fw"></i> Admin<span class="fa arrow"></span></a>
                  <ul class="nav nav-second-level">
                    <li><g:link controller="admin" action="tidyOrgData"><i class="fa fa-angle-double-right fa-fw"></i> Tidy Orgs Data</g:link></li>
                    <li><g:link controller="admin" action="reSummariseLicenses"><i class="fa fa-angle-double-right fa-fw"></i> Regenerate License Summaries</g:link></li>
                    <li><g:link controller="admin" action="updateTextIndexes"><i class="fa fa-angle-double-right fa-fw"></i> Update Free Text Indexes</g:link></li>
                    <li><g:link controller="admin" action="resetTextIndexes"><i class="fa fa-angle-double-right fa-fw"></i> Reset Free Text Indexes</g:link></li>
                    <li><g:link controller="admin" action="masterListUpdate"><i class="fa fa-angle-double-right fa-fw"></i> Force Master List Update</g:link></li>
                    <li><g:link controller="admin" action="clearBlockCache"><i class="fa fa-angle-double-right fa-fw"></i> Clear Block Cache (eg Stats)</g:link></li>
                    <li><g:link controller="user" action="search"><i class="fa fa-angle-double-right fa-fw"></i> User Management Console</g:link></li>
                    <li class="divider"></li>
                    <li><g:link controller="integration"><i class="fa fa-database fa-fw"></i> Integration API</g:link></li>
                  </ul></li>
              </sec:ifAnyGranted>

            </sec:ifLoggedIn>
            <li><a href="https://github.com/k-int/gokb-phase1/wiki/API"><i class="fa fa-cogs fa-fw"></i> API Documentation</a></li>
            <li class="${params?.controller == "home" && params?.action == 'about' ? 'active' : ''}" ><g:link controller="home" action="about"><i class="fa fa-info fa-fw"></i>  About GOKb</g:link></li>
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
          <g:layoutBody />
        </div>
        <!-- /.col-lg-12 -->
      </div>
      <!-- /.row -->
    </div>
    <!-- /#page-wrapper -->

  </div>
  <!-- /#wrapper -->
  
  <g:if test="${(grailsApplication.config.kuali?.analytics?.code instanceof String ) }">
    <asset:script type="text/javascript">
      (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
      (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
      m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
      })(window,document,'script','//www.google-analytics.com/analytics.js','ga');

      ga('create', '${grailsApplication.config.kuali.analytics.code}', 'kuali.org');
      ga('send', 'pageview');
    </asset:script>
  </g:if>
  <asset:deferredScripts/>
</body>
</html>
