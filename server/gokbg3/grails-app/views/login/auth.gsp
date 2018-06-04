<g:set var='securityConfig' value='${applicationContext.springSecurityService.securityConfig}'/>
<html>
<head>
  <meta name="layout" content="public"/>
  <s2ui:title messageCode='spring.security.ui.login.title'/>
  <asset:stylesheet src='spring-security-ui-auth.css'/>
</head>
<body>
  <div class="container-fluid">
    <div class="row">
      <div class="col-lg-6 col-lg-offset-3">
            <g:form class="well" controller="login" action="authenticate" method="post" name="loginForm" elementId="loginForm" autocomplete="off">
            <h2>Login</h2>
              <g:if test="${params.login_error}">
                <div class="alert alert-danger"><g:message code='springSecurity.login.error.message'/></div>
              </g:if>
               <div class="form-group">
                 <label for="username"><g:message code='spring.security.ui.login.username'/></label>
                 <input type="text" class="form-control" id="username" aria-describedby="usernameHelp" placeholder="Username" name="${securityConfig.apf.usernameParameter}">
                 <small id="usernameHelp" class="form-text text-muted">We'll never share your email with anyone else.</small>
               </div>

               <div class="form-group">
                 <label for="password"><g:message code='spring.security.ui.login.password'/></label>
                 <input type="password" class="form-control" id="password" aria-describedby="passwordHelp" placeholder="" name="${securityConfig.apf.passwordParameter}">
                 <small id="passwordHelp" class="form-text text-muted">Password.</small>
               </div>

               <!-- input type="checkbox" class="checkbox" name="${securityConfig.rememberMe.parameter}" id="remember_me" checked="checked"-->
               <!-- label for='remember_me'><g:message code='spring.security.ui.login.rememberme'/></label -->
               <button type="submit">Login</button>
            </g:form>
      </div>
    </div>
  </div>
</body>
</html>
