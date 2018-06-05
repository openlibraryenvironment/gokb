<%@ page import="org.gokb.cred.RefdataCategory" %>
<%@page expressionCodec="none" %>
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

  <link rel="shortcut icon" href="${resource(dir: 'images', file: 'gokb-favicon.png')}" type="image/png">
  <g:layoutHead />

  <asset:javascript src="gokb/application.grass.js" />
  <asset:stylesheet src="gokb/sb-admin-2.css"/>
  <asset:stylesheet src="gokb/themes/${ grailsApplication.config.gokb.theme }/theme.css"/>
  <asset:stylesheet src="gokb/application.css"/>

	<asset:script type="text/javascript" src="//cdn.jsdelivr.net/webshim/1.12.4/extras/modernizr-custom.js"></asset:script>
	<asset:script type="text/javascript" src="//cdn.jsdelivr.net/webshim/1.12.4/polyfiller.js"></asset:script>

	<asset:script>
	  webshims.setOptions('waitReady', false);
	  webshims.setOptions('forms-ext', {types: 'date'});
	  webshims.polyfill('forms forms-ext');
	</asset:script>

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

      <div class="navbar-default sidebar" role="navigation">
        <div class="sidebar-nav navbar-collapse">
          <ul class="nav" id="side-menu">
            <sec:ifLoggedIn>
              <li class="${params?.controller == "home" && (params?.action == 'index' || params?.action == 'dashboard') ? 'active' : ''}"><g:link controller="home"><i class="fa fa-dashboard fa-fw"></i> My Dashboard</g:link></li>
              <li class="${params?.controller == "search" || params?.controller == "globalSearch"  ? 'active' : ''}"><a href="#"><i class="fa fa-search fa-fw"></i> Search<span class="fa arrow"></span></a>
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

                </ul> <!-- /.nav-second-level --></li>
                <li><g:link controller="welcome"><i class="fa fa-tasks fa-fw"></i> To Do<span class="fa arrow"></span></g:link>

                  <ul class="nav nav-second-level">
                    <li><g:link controller="search" action="index"
                        params="${[
                          qbe:'g:reviewRequests',
                          qp_allocatedto:'org.gokb.cred.User:'+request.user.id,
                          qp_status: ('org.gokb.cred.RefdataValue:'+(RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Open').id))
                        ]}">
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

              <li class="${params?.controller == "savedItems" ? 'active' : ''}" ><g:link controller="savedItems" action="index"><i class="fa fa-folder fa-fw"></i> Saved Items</g:link></li>

              <li class="${params?.controller == "upload" ? 'active' : ''}" ><g:link controller="upload" action="index"><i class="fa fa-upload fa-fw"></i> File Upload</g:link></li>

              <g:if test="${grailsApplication.config.feature.directUpload}">
                <li class="${params?.controller == "ingest" ? 'active' : ''}" ><g:link controller="ingest" action="index"><i class="fa fa-upload fa-fw"></i> Direct Ingest</g:link></li>
              </g:if>

              <li class="${params?.controller == "coreference" ? 'active' : ''}"><g:link controller="coreference" action="index"><i class="fa fa-list-alt fa-fw"></i> Coreference</g:link></li>

              <sec:ifAnyGranted roles="ROLE_ADMIN">
                <li class="${params?.controller == "admin" ? 'active' : ''}"><a href="#"><i class="fa fa-wrench fa-fw"></i> Admin<span class="fa arrow"></span></a>
                  <ul class="nav nav-second-level">
                    <li><g:link controller="admin" action="tidyOrgData" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Tidy Orgs Data</g:link></li>
                    <li><g:link controller="admin" action="reSummariseLicenses" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Regenerate License Summaries</g:link></li>
                    <li><g:link controller="admin" action="updateTextIndexes" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Update Free Text Indexes</g:link></li>
                    <li><g:link controller="admin" action="resetTextIndexes" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Reset Free Text Indexes</g:link></li>
                    <li><g:link controller="admin" action="masterListUpdate" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Force Master List Update</g:link></li>
                    <li><g:link controller="admin" action="clearBlockCache" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Clear Block Cache (eg Stats)</g:link></li>
                    <g:if env="development">
                      <li><g:link controller="admin" action="cleanup" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Expunge Deleted Records</g:link></li>
                    </g:if>
                    <li><g:link controller="admin" action="triggerEnrichments" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Trigger enrichments</g:link></li>
                    <li><g:link controller="admin" action="buildExtension" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Build Refine Extensions</g:link></li>
                    <li><g:link controller="admin" action="logViewer"><i class="fa fa-angle-double-right fa-fw"></i> Log Viewer</g:link></li>
                    <li><g:link controller="admin" action="jobs"><i class="fa fa-angle-double-right fa-fw"></i> Manage Jobs</g:link></li>
                    <li><g:link controller="admin" action="housekeeping" onclick="return confirm('Are you sure?')"><i class="fa fa-angle-double-right fa-fw"></i> Housekeeping</g:link></li>
                    <li><g:link controller="user" action="search"><i class="fa fa-angle-double-right fa-fw"></i> User Management Console</g:link></li>
                    <!--
                    <li><g:link controller="api" action="downloadUpdate"><i class="fa fa-angle-double-right fa-fw"></i> Get Refine Extension</g:link></li>
                    -->
                    <li class="divider"></li>
                    <li><g:link controller="integration"><i class="fa fa-database fa-fw"></i> Integration API</g:link></li>
                  </ul></li>

              </sec:ifAnyGranted>
              <li class="${params?.controller == "home" && params?.action == 'about' ? 'active' : ''}" ><g:link controller="home" action="about"><i class="fa fa-info fa-fw"></i> Operating environment</g:link></li>
              <g:if test="${ grailsApplication.config.decisionSupport }">
                <li><g:link controller="decisionSupport"><i class="fa fa-search fa-fw"></i> Decision Support Dashboard</g:link></li>
              </g:if>

            </sec:ifLoggedIn>
            <sec:ifNotLoggedIn>
              <li class="${params?.controller == "home" && params?.action == 'home' ? 'active' : ''}"><g:link controller="home"><i class="fa fa-home fa-fw"></i> Home</g:link></li>
              <li class="${params?.controller == "register" ? 'active' : ''}"><g:link controller="register"><i class="fa fa-edit fa-fw"></i> Register</g:link></li>
              <li class="${params?.controller == "login" ? 'active' : ''}"><g:link controller="login"><i class="fa fa-sign-in fa-fw"></i> Sign in</g:link></li>
            </sec:ifNotLoggedIn>
            <li><a href="https://github.com/k-int/gokb-phase1/wiki/API"><i class="fa fa-cogs fa-fw"></i> API Documentation</a></li>
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
      <g:if test="${!grailsApplication.config.zendesk_disabled}">
	<asset:script type="text/javascript">
<!-- Start of gokb Zendesk Widget script -->
/*<![CDATA[*/window.zEmbed||function(e,t){var n,o,d,i,s,a=[],r=document.createElement("iframe");window.zEmbed=function(){a.push(arguments)},window.zE=window.zE||window.zEmbed,r.src="javascript:false",r.title="",r.role="presentation",(r.frameElement||r).style.cssText="display: none",d=document.getElementsByTagName("script"),d=d[d.length-1],d.parentNode.insertBefore(r,d),i=r.contentWindow,s=i.document;try{o=s}catch(c){n=document.domain,r.src='javascript:var d=document.open();d.domain="'+n+'";void(0);',o=s}o.open()._l=function(){var o=this.createElement("script");n&&(this.domain=n),o.id="js-iframe-async",o.src=e,this.t=+new Date,this.zendeskHost=t,this.zEQueue=a,this.body.appendChild(o)},o.write('<body onload="document._l();">'),o.close()}("https://assets.zendesk.com/embeddable_framework/main.js","gokb.zendesk.com");
/*]]>*/
<!-- End of gokb Zendesk Widget script -->
	</asset:script>
      </g:if>

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
