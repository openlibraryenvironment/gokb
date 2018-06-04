<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="sb-admin"/>
    <asset:stylesheet src="gokb/application.css"/>
    <asset:javascript src="gokb/application.js" />
    <title>GOKb</title>
  </head>
  <body>
    <g:if test="${params.status == '404'}">
    <h2>
      <g:message code="component.notFound.label" args="[params.id]"/>
    </h2>
    </g:if>
  </body>
</html>
