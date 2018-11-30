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
    <g:else>
      <h1 class="page-header">GOKb Resources</h1>
      <g:each in="${session.menus?.search}" var="type,items" status="counter">
        <g:if test="${ counter > 0 }" >
          <div class="divider"></div>
        </g:if>
        <g:each in="${items}" var="item">
          <li><g:link controller="${item.link.controller}" action="${item.link.action}" params="${item.link.params}"> ${item.text} </g:link></li>
        </g:each>
      </g:each>
    </g:else>
  </body>
</html>
