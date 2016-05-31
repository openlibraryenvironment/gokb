<html>
<head>
  <meta name='layout' content='sb-admin'/>
  <title><g:message code='spring.security.ui.register.title'/></title>
</head>

<body>

  <div class="container">
    <div class="row">
      <div class='col-md-12'>
        &nbsp;
      </div>
    </div>
    <div class="row">
      <div class='col-md-12'>

        <g:if test="${flash.error}">
          <div class="alert alert-error" style="display: block">${flash.error}</div>
        </g:if>

        <g:if test="${flash.message}">
          <div class="info alert-info" style="display: block">${flash.message}</div>
        </g:if> 

        <g:if test='${emailSent}'>
          <div class="panel panel-default">
            <div class="panel-heading">Register as a new user</div>
            <div class="panel-body">
              <div class="info alert-success" style="display: block">
                <g:message code='spring.security.ui.register.sent'/>
              </div>
            </div>
          </div>
        </g:if>
        <g:else>
          <div class="panel panel-default">
            <div class="panel-heading">Register as a new user</div>
            <div class="panel-body">
              <g:form controller="register" action="register" class="form" role="form">
                <div class="form-group ${registerCommand.errors.hasFieldErrors('username') ? 'has-error' : ''}">
                  <label for="username">Username</label>
                  <input type="text" class="form-control" id="username" name="username" placeholder="Requested Username" value="${registerCommand.username}" />
                  <g:renderErrors bean="${registerCommand}" as="list" field="username"/>
                </div>
                <div class="form-group ${registerCommand.errors.hasFieldErrors('email') ? 'has-error' : ''}">
                  <label for="email">Email</label>
                  <input type="text" class="form-control" id="email" name="email" placeholder="user@yourdomain.ac.uk" value="${registerCommand.email}" />
                  <g:renderErrors bean="${registerCommand}" as="list" field="email"/>
                </div>
                <div class="form-group ${registerCommand.errors.hasFieldErrors('password') ? 'has-error' : ''}">
                  <label for="password">Password</label>
                  <input autocomplete="false" type="password" class="form-control" id="password" name="password" placeholder="password" value="${registerCommand.password}" />
                  <g:renderErrors bean="${registerCommand}" as="list" field="password"/>
                </div>
                <div class="form-group">
                  <label for="password2">Confirm Password</label>
                  <input autocomplete="false" type="password" class="form-control" id="password2" name="password2" placeholder="password" value="${registerCommand.password2}" />
                </div>
                <div class="form-group">
                  <label for=""></label>
                  <button type="submit" class="btn btn-success">Register</button>
                </div>
              </g:form>
            </div>
          </div>
        </g:else>
      </div>
    </div>
  </div>


</body>
</html>
