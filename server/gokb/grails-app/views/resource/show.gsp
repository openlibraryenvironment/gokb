<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle"/>
    <title>GOKb</title>
  </head>
  <body>
    <div class="container-fluid">
      <g:if test="${displaytemplate != null}">
        <g:if test="${displaytemplate.type=='staticgsp'}">
        	<g:if test="${isComponent}" >
        		<g:render template="kbcomponent" contextPath="../apptemplates" model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}"/>
        	</g:if>
          <g:render template="${displaytemplate.rendername}" contextPath="../apptemplates" model="${[d:displayobj, rd:refdata_properties, dtype:displayobjclassname_short]}"/>
        </g:if>
      </g:if>
    </div>
  </body>
</html>
