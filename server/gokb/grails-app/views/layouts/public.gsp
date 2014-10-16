<!DOCTYPE html>
<html lang="en">

<head>

    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="">
    <meta name="author" content="">
    <g:layoutHead />
  
	  <asset:javascript src="public/script.js" />
	  <asset:stylesheet src="public/style.css"/>

    <title><g:layoutTitle default="GOKb: Welcome" /></title>

    <!-- Fonts -->
    <link href="http://fonts.googleapis.com/css?family=Open+Sans:300italic,400italic,600italic,700italic,800italic,400,300,600,700,800" rel="stylesheet" type="text/css">
    <link href="http://fonts.googleapis.com/css?family=Josefin+Slab:100,300,400,600,700,100italic,300italic,400italic,600italic,700italic" rel="stylesheet" type="text/css">

    <!-- HTML5 Shim and Respond.js IE8 support of HTML5 elements and media queries -->
    <!-- WARNING: Respond.js doesn't work if you view the page via file:// -->
    <!--[if lt IE 9]>
        <script src="https://oss.maxcdn.com/libs/html5shiv/3.7.0/html5shiv.js"></script>
        <script src="https://oss.maxcdn.com/libs/respond.js/1.4.2/respond.min.js"></script>
    <![endif]-->

</head>

<body id="page-body">

    <div class="brand" id="main-branding">GOKb</div>
    <!-- div class="address-bar">Blah blah blah</div -->

    <!-- Navigation -->
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
                <a class="navbar-brand" href="index.html">GOKb</a>
            </div>
            <!-- Collect the nav links, forms, and other content for toggling -->
            <div class="collapse navbar-collapse" id="primary-nav">
                <ul class="nav navbar-nav">
                  <li class="hidden active"><a href="#page-body"></a></li>
                   <li><a href="#about" class="page-scroll"><i class="fa fa-newspaper-o fa-fw"></i> About</a></li>
                   <li><g:link controller="register"><i class="fa fa-edit fa-fw"></i> Register</g:link></li>
                   <li><g:link controller="login"><i class="fa fa-sign-in fa-fw"></i> Sign in</g:link></li>
                </ul>
            </div>
            <!-- /.navbar-collapse -->
        </div>
        <!-- /.container -->
    </nav>

    <g:layoutBody />

    <footer>
        <div class="container">
            <div class="row">
                <div class="col-lg-12 text-center">
                    <p>Copyright &copy; GOKb 2014</p>
                </div>
            </div>
        </div>
    </footer>

</body>

</html>