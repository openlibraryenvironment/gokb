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
    <nav class="navbar navbar-default navbar-static-top container-fluid" role="navigation"
      style="margin-bottom: 0">
      <div class="navbar-header">
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
    </nav>

    <!-- Page Content -->
    <div id="page-wrapper-nolhm" class="${ params.controller ?: 'default' }-display" >
	    <div id="page-content-nolhm" >
	      <g:layoutBody />
	    </div>
	    <!-- /.col-lg-12 -->
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
