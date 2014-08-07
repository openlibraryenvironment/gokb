<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle"/>
    <title>GOKb - ${displayobj?.getNiceName()}
      (<g:if test="${displayobj?.isEditable()}">Editable</g:if><g:else>Read Only</g:else>)
    </title>
  </head>
  <body class="" >
    <g:if test="${displayobj != null}">
      <g:if test="${displayobj.respondsTo('availableActions')}">
        <div class="pull-right well" id="actionControls">
    	<g:form controller="workflow" action="action" method="post"  class='action-form' >
          <h4>Available actions</h4>
          <select id="selectedAction" name="selectedBulkAction">
            <option value="">-- Select an action to perform --</option>
            <g:each var="action" in="${displayobj.availableActions()}" >
              <option value="${action.code}">${action.label}</option>
            </g:each>
          </select>
          <input type="hidden" name="bulk:${displayobj.class.name}:${displayobj.id}" value="true" />
          <button type="submit" class="btn btn-default btn-primary btn-sm">Submit</button>
        </g:form>
        </div>
      </g:if>

      <div class="container-fluid well">
        <g:if test="${displaytemplate != null}">
          <g:if test="${displaytemplate.type=='staticgsp'}">
            <g:render template="${displaytemplate.rendername}" contextPath="../apptemplates" model="${[d:displayobj, rd:refdata_properties, dtype:displayobjclassname_short]}"/>
          </g:if>
        </g:if>
      </div>
    </g:if>
    <g:else>
      <h1>Unable to find record in database : Please notify support</h1>
    </g:else>
  </body>
</html>
