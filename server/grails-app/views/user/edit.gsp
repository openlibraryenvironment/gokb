<sec:ifNotSwitched>
	<sec:ifAllGranted roles='${securityConfig.ui.switchUserRoleName}'>
	<g:set var='username' value='${uiPropertiesStrategy.getProperty(user, 'username')}'/>
	<g:if test='${username}'><g:set var='canRunAs' value='${true}'/></g:if>
	</sec:ifAllGranted>
</sec:ifNotSwitched>

<sec:ifAllGranted roles="ROLE_SUPERUSER">
  <g:set var="su" value="${true}" />
</sec:ifAllGranted>
<html>
<head>
	<meta name="layout" content="${layoutUi}"/>
	<s2ui:title messageCode='default.edit.label' entityNameMessageCode='user.label' entityNameDefault='User'/>
</head>
<body>
<h3><g:message code='default.edit.label' args='[entityName]'/></h3>
<s2ui:form type='update' beanName='user' focus='username' class='button-style' useToken='true'>
	<s2ui:tabs elementId='tabs' height='375' data='${tabData}'>
		<s2ui:tab name='userinfo' height='275'>
			<table>
				<tbody>
                                <g:if test="${su || (!user.hasRole('ROLE_SUPERUSER') && !user.hasRole('ROLE_ADMIN'))}">
                                  <s2ui:textFieldRow name='username' labelCodeDefault='Username'/>
                                  <s2ui:passwordFieldRow name='password' labelCodeDefault='Password'/>
                                  <s2ui:checkboxRow name='enabled' labelCodeDefault='Enabled'/>
                                  <s2ui:checkboxRow name='accountExpired' labelCodeDefault='Account Expired'/>
                                  <s2ui:checkboxRow name='accountLocked' labelCodeDefault='Account Locked'/>
                                  <s2ui:checkboxRow name='passwordExpired' labelCodeDefault='Password Expired'/>
                                </g:if>
                                <g:else>
                                  <tr><td>Username:</td><td>${user.username}<td></tr>
                                </g:else>
				</tbody>
			</table>
		</s2ui:tab>
		<s2ui:tab name='roles' height='275'>
		<g:each var='entry' in='${roleMap}'>
			<g:set var='roleName' value='${uiPropertiesStrategy.getProperty(entry.key, 'authority')}'/>
			<g:if test="${su || ( roleName != 'ROLE_SUPERUSER' && !user.hasRole('ROLE_SUPERUSER') )}">
        <div>
                <g:checkBox name='${roleName}' value='${entry.value}'/>
                <g:if test="${su}">
                  <g:link controller='role' action='edit' id='${entry.key.id}'>${roleName}</g:link>
                </g:if>
                <g:else>
                  ${roleName}
                </g:else>
        </div>
      </g:if>
      <g:else>
        <div>
                <g:checkBox name='${roleName}' value='${entry.value}' disabled="true"/>
                <g:if test="${su}">
                  <g:link controller='role' action='edit' id='${entry.key.id}'>${roleName}</g:link>
                </g:if>
                <g:else>
                  ${roleName}
                </g:else>
        </div>
      </g:else>
		</g:each>
		</s2ui:tab>
	</s2ui:tabs>
	<div style="float:left; margin-top: 10px;">
		<s2ui:submitButton/>
		<g:if test='${user}'>
                  <sec:ifAllGranted roles="ROLE_SUPERUSER">
                    <s2ui:deleteButton/>
                  </sec:ifAllGranted>
                </g:if>
		<g:if test='${canRunAs}'><a id="runAsButton">${message(code:'spring.security.ui.runas.submit')}</a></g:if>
	</div>
</s2ui:form>
<g:if test='${user}'><sec:ifAllGranted roles="ROLE_SUPERUSER"><s2ui:deleteButtonForm instanceId='${user.id}' useToken="true"/></sec:ifAllGranted></g:if>
<g:if test='${canRunAs}'>
<form name="runAsForm" action="${request.contextPath}${securityConfig.switchUser.switchUserUrl}" method='post'>
	<g:hiddenField name='${securityConfig.switchUser.usernameParameter}' value='${username}'/>
	<input type="submit" class="s2ui_hidden_button"/>
</form>
</g:if>
<s2ui:documentReady>
	$("#runAsButton").button();
	$('#runAsButton').bind('click', function() {
		document.forms.runAsForm.submit();
	});
</s2ui:documentReady>
</body>
</html>
