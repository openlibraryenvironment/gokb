<!DOCTYPE html>
<html>
<head>
<meta name='layout' content='public' />
<title>GOKb: Welcome</title>
</head>

<body>

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

	<div class="container">
		<div class="row">
			<div class="box">
				<div class="col-lg-12 text-center">
					<div id="carousel" class="carousel slide" data-ride="carousel"
						data-interval="4000" data-wrap="true">
						<!-- Indicators -->
						<ol class="carousel-indicators hidden-xs">
							<li data-target="#carousel" data-slide-to="0" class="active"></li>
							<li data-target="#carousel" data-slide-to="1"></li>
							<li data-target="#carousel" data-slide-to="2"></li>
							<li data-target="#carousel" data-slide-to="3"></li>
						</ol>

						<!-- Wrapper for slides -->
						<div class="carousel-inner">
							<div class="item active">
								<asset:image src="gokb/carousel/001_opt.jpg" />
							</div>
							<div class="item">
								<asset:image src="gokb/carousel/002_opt.jpg" />
							</div>
							<div class="item">
								<asset:image src="gokb/carousel/003_opt.jpg" />
							</div>
							<div class="item">
								<asset:image src="gokb/carousel/004_opt.jpg" />
							</div>
						</div>

						<!-- Controls -->
						<a class="left carousel-control" href="#carousel"
							data-slide="prev"> <span class="icon-prev"></span>
						</a> <a class="right carousel-control" href="#carousel"
							data-slide="next"> <span class="icon-next"></span>
						</a>
					</div>
					<span class="brand-before"> <small>Welcome to</small>
					</span>
					<h1 class="brand-name">GOKb</h1>
				</div>
			</div>
		</div>

		<div class="row">
			<div class="box" id="about">
				<div class="col-lg-12">
					<hr>
					<h2 class="intro-text text-center">
						About <strong>GOKb</strong>
					</h2>
					<hr>
					<p>GOKb, the Global Open Knowledgebase, is a community-managed
						project that aims to describe electronic journals and books,
						publisher packages, and platforms in a way that will be familiar
						to librarians who have worked with electronic resources. Following
						launch in November 2014, libraries and consortia will be working
						together to extend and maintain the knowledge base.</p>
					<p>
						The GOKb project has been supported by the Andrew W. Mellon
						Foundation. It is managed by the <a
							href="http://www.kuali.org/ole/partners">Kuali OLE founding
							partners</a> and <a href="https://www.jisc-collections.ac.uk/">JISC
							Collections</a> of the United Kingdom, collaborating with
						organizations and editors from a number of countries.
					</p>
					<p>The data found in GOKb is freely available for reuse under a
						CC0 license.</p>
					<p>
						Organizations and individuals wishing to access data or to take
						part as editorial contributors should contact <a
							href="mailto:kristen_wilson@ncsu.edu">Kristen Wilson</a>, the
						GOKb editor.
					</p>
					<p>
						Guidance for editors can be found on the <a
							href="https://wiki.kuali.org/display/OLE/GOKb+Data+Management+Wiki">GOKb
							Wiki</a>
					</p>
					<p>
						Further general information about the project can be found at <a
							href="http://gokb.org/">http://gokb.org</a>.
					</p>
				</div>
			</div>
		</div>
	</div>
	<!-- /.container -->
</body>
</html>
