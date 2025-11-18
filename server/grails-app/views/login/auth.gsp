<g:set var='securityConfig' value='${applicationContext.springSecurityService.securityConfig}'/>
<html>
<head>
  <meta name="layout" content="public"/>
  <s2ui:title messageCode='springSecurity.login.header'/>
  <asset:stylesheet src='spring-security-ui-auth.css'/>
</head>
<body>
  <div class="container-fluid">
    <div class="row">
      <div class="col-lg-6 col-lg-offset-3">
        <g:form class="well" controller="login" action="authenticate" method="post" name="loginForm" elementId="loginForm" autocomplete="off">
        <h2>Admin Login</h2>
          <g:if test="${params.login_error}">
            <div class="alert alert-danger" style="font-size:1em; font-weight:bold;"><g:message code='springSecurity.login.error.message'/></div>
          </g:if>

          <div class="form-group">
            <label for="username"><g:message code='springSecurity.login.username.label'/></label>
            <input type="text" class="form-control" id="username" aria-describedby="usernameHelp" placeholder="Username" name="${securityConfig.apf.usernameParameter}">
          </div>

          <div class="form-group">
            <label for="password"><g:message code='springSecurity.login.password.label'/></label>
            <input type="password" class="form-control" id="password" aria-describedby="passwordHelp" placeholder="" name="${securityConfig.apf.passwordParameter}">
          </div>
<%--
          <div class="form-group">
            <input type="checkbox" class="checkbox" name="${securityConfig.rememberMe.parameter}" id="remember_me" checked="checked">
            <label for='remember_me'><g:message code='spring.security.ui.login.rememberme'/></label>
          </div> --%>
          <div>
            <button type="submit">Login</button>
          </div>
        </g:form>
      </div>
    </div>
    <div class="row">
      <div class="col-lg-6 col-lg-offset-3" style="text-align:center">
        <div class="alert alert-info" style="display: inline-block; font-size:1.2em; font-weight:bold;">
          <g:message code="login.alert.userClient.text"/> <a style="text-decoration:underline" href ="${grailsApplication.config.getProperty('gokb.uiUrl')}"><g:message code="public.userClient.label"/></a>
        </div>
      </div>
    </div>
  </div>
</body>
</html>
