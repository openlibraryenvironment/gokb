<g:set var="perRow" value="${2}" />
<g:set var="fullRows" value="${(widgets.size() / perRow).toInteger() * perRow}" />
<g:set var="lastRow" value="${(widgets.size() % perRow).toInteger()}" />
<!DOCTYPE html>
<html>
  <head>
    <meta name='layout' content='sb-admin'/>
    <r:require modules="gokbcharts"/>
    <title>GOKb: Welcome</title>
  </head>
  <body>
  	<h1 class="page-header">Welcome to GOKb</h1>
  	
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
  </body>
</html>
