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

  <link rel="shortcut icon" href="${resource(dir: 'images', file: 'favicon.ico')}" type="image/x-icon">
  <g:layoutHead />
  <asset:script> var contextPath="${grailsApplication.config.server.contextPath ?: ''}"; </asset:script>
  <asset:javascript src="gokb/application.grass.js" />
  <asset:stylesheet src="gokb/sb-admin-2.css"/>
  <asset:stylesheet src="gokb/themes/${ grailsApplication.config.gokb.theme }/theme.css"/>
  <asset:stylesheet src="gokb/application.css"/>

	<asset:script type="text/javascript" src="//cdn.jsdelivr.net/webshim/1.16.0/polyfiller.js"></asset:script>
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
          <g:layoutBody />
        </div>
        <!-- /.col-lg-12 -->
      </div>
      <!-- /.row -->
    </div>
    <!-- /#page-wrapper -->

  </div>
  <!-- /#wrapper -->
  <asset:deferredScripts/>


</body>
</html>
