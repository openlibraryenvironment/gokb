<!DOCTYPE html>
<html lang="en">

<head>

    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="">
    <meta name="author" content="">
    <link rel="shortcut icon" href="${resource(dir: 'images', file: 'favicon.ico')}" type="image/x-icon">
    <g:layoutHead />

    <asset:stylesheet src="gokb/themes/${ grailsApplication.config.gokb.theme }/theme.css"/>
    <asset:stylesheet src="gokb/fontawesome.css" />
    <asset:stylesheet src="gokb/application.css"/>

    <title><g:layoutTitle default="GOKb: Welcome" /></title>

    <!-- Fonts -->
    <link href="https://fonts.googleapis.com/css?family=Open+Sans:300italic,400italic,600italic,700italic,800italic,400,300,600,700,800" rel="stylesheet" type="text/css">
    <link href="https://fonts.googleapis.com/css?family=Josefin+Slab:100,300,400,600,700,100italic,300italic,400italic,600italic,700italic" rel="stylesheet" type="text/css">

    <!--[if lt IE 9]>
        <script src="https://oss.maxcdn.com/libs/html5shiv/3.7.0/html5shiv.js"></script>
        <script src="https://oss.maxcdn.com/libs/respond.js/1.4.2/respond.min.js"></script>
    <![endif]-->

</head>

<body id="page-body" class="theme-${ grailsApplication.config.gokb.theme }">

  <nav class="navbar navbar-default" id="primary-nav-bar" role="navigation">
     <div class="container">
       <!-- Brand and toggle get grouped for better mobile display -->
       <div class="navbar-header">
         <button type="button" class="navbar-toggle" data-toggle="collapse" data-target="#primary-nav">
           <span class="sr-only">Toggle navigation</span>
           <span class="icon-bar"></span>
           <span class="icon-bar"></span>
           <span class="icon-bar"></span>
         </button>
         <!-- navbar-brand is hidden on larger screens, but visible when the menu is collapsed -->
         <a class="navbar-brand" href="public" style="font-weight:bold;"><g:message code="gokb.appname" default="GOKb" /></a>
       </div>

       <div class="nav navbar-nav navbar-right">
          <g:if test="${grailsApplication.config.gokb.blogUrl}">
            <li><a  style="font-weight:bold;" href ="${grailsApplication.config.gokb.blogUrl}">About GOKb</a></li>
          </g:if>
          <li><span style="width:15px"></span></li>
          <li><g:link controller="register" action="register" style="font-weight:bold;">Register</g:link></li>
          <li><g:link controller="home" action="index" style="font-weight:bold;">Admin Home</g:link></li>
       </div>

     </div>
   </nav>




    <g:layoutBody />

    <footer>
        <div class="container">
            <div class="row">
                <div class="col-lg-12 text-center">
                </div>
            </div>
        </div>
    </footer>

</body>

</html>
