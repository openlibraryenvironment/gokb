<g:set var="perRow" value="${2}" />
<g:set var="fullRows" value="${(widgets.size() / perRow).toInteger() * perRow}" />
<g:set var="lastRow" value="${(widgets.size() % perRow).toInteger()}" />
<!DOCTYPE html>
<html>
  <head>
    <meta name='layout' content='sb-admin'/>
    <title>GOKb: Dashboard</title>
  </head>
  <body>
    <h1 class="page-header">Welcome to GOKb</h1>
    <g:if test="${params.status == '404'}">
      <div class="alert alert-danger">
        The page you requested does not exist!
      </div>
    </g:if>
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
  </body>
</html>
