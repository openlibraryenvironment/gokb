<g:set var="perRow" value="${3}" />
<g:set var="fullRows" value="${(widgets.size() / perRow).toInteger() * perRow}" />
<g:set var="lastRow" value="${(widgets.size() % perRow).toInteger()}" />
<!DOCTYPE html>
<html>
  <head>
    <meta name='layout' content='no-lhnav'/>
    <title>GOKb: Welcome</title>
  </head>
  <body>
    <!-- Header Carousel -->
    <header id="carousel" class="carousel slide" data-ride="carousel" data-interval="4000" data-wrap="true" >
	    <!-- Indicators -->
	    <ol class="carousel-indicators">
	      <li data-target="#carousel" data-slide-to="0" class="active"></li>
	      <li data-target="#carousel" data-slide-to="1"></li>
        <li data-target="#carousel" data-slide-to="2"></li>
        <li data-target="#carousel" data-slide-to="3"></li>
	    </ol>
	
	    <!-- Wrapper for slides -->
	    <div class="carousel-inner" >
	        <div class="item active">
	            <asset:image src="gokb/carousel/001_crop_small.jpg" width="100%" />
	            <div class="carousel-caption">
	                <h2>Caption number 1.</h2>
	            </div>
	        </div>
	        <div class="item">
              <asset:image src="gokb/carousel/002_crop_small.jpg" width="100%" />
              <div class="carousel-caption">
                  <h2>Caption number 2.</h2>
              </div>
	        </div>
          <div class="item">
              <asset:image src="gokb/carousel/003_crop_small.jpg" width="100%" />
              <div class="carousel-caption">
                  <h2>Caption number 3.</h2>
              </div>
          </div>
          <div class="item">
              <asset:image src="gokb/carousel/004_crop_small.jpg" width="100%" />
              <div class="carousel-caption">
                  <h2>Caption number 4.</h2>
              </div>
          </div>
	    </div>
	
	    <!-- Controls -->
	    <a class="left carousel-control" href="#carousel" data-slide="prev">
	        <span class="icon-prev"></span>
	    </a>
	    <a class="right carousel-control" href="#carousel" data-slide="next">
	        <span class="icon-next"></span>
	    </a>
    </header>
    <div class="container" >
      <div class="row" >
		    <h1 class="page-header">Welcome to GOKb</h1>
		    <cache:block>
		      <!-- Full rows -->
		      <g:each var="name, widget" in="${widgets}" status="wcount" >
		        <div class="col-md-${ (wcount + 1) <= fullRows ? (12 / perRow) : (12 / lastRow) }">
		          <div class="panel panel-default">
		            <div class="panel-heading">${name}</div>
		            <!-- /.panel-heading -->
		            <div class="panel-body">
		              ${ gokb.chart(widget) }
		            </div>
		            <!-- /.panel-body -->
		          </div>
		          <!-- /.panel -->
		        </div>
		      </g:each>
		    </cache:block>
		  </div>
	  </div>
  </body>
</html>
