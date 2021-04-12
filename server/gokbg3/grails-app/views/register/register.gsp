<html>
<head>
  <meta name='layout' content='public'/>
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
          <div class="alert alert-warn">${flash.error}</div>
        </g:if>

        <g:if test="${flash.message}">
          <div class="info alert-info" style="display: block">${flash.message}</div>
        </g:if>

        <g:if test='${emailSent}'>
          <div class="panel panel-default">
            <div class="panel-heading">Registration complete</div>
            <div class="panel-body">
              <g:if test="${noAddress}">
                <div class="alert alert-success" style="font-weight:bolder">
                  Your account has been created, but still needs to be activated by an administrator!
                </div>
              </g:if>
              <g:else>
                <div class="alert alert-success" style="font-weight:bolder">
                  <g:message code='spring.security.ui.register.sent'/>
                </div>
              </g:else>
            </div>
          </div>
        </g:if>
        <g:elseif test="${noTries}">
          <div class="panel panel-default">
            <div class="panel-heading">Registration Failed</div>
            <div class="panel-body">
              <div class="alert alert-danger" style="font-weight:bolder">
                <span style="padding:10px;">You incorrectly answered the security question too many times. Please try again later.</span>
              </div>
            </div>
          </div>
        </g:elseif>
        <g:else>
          <div class="panel panel-default">
            <div class="panel-heading">Register as a new user</div>
            <div class="panel-body">
              <g:form controller="register" action="register" class="form" role="form">
                <div class="form-group ${registerCommand.errors.hasFieldErrors('username') ? 'has-error' : ''}">
                  <label for="username">Username (<span style="color:red">*</span>)</label>
                  <input autocomplete="false" type="text" class="form-control" id="username" name="username" placeholder="Requested Username" value="${registerCommand.username}" style="width:50%"/>
                  <input type="text" name="phone" value="" hidden="true" />
                  <g:if test="${errors?.username?.size() > 0}">
                    <ul>
                      <g:each var="e" in="${errors.username}">
                        <li>${e.message}</li>
                      </g:each>
                    </ul>
                  </g:if>
                </div>
                <div class="form-group ${registerCommand.errors.hasFieldErrors('email') ? 'has-error' : ''}">
                  <label for="email">Email <i class="fas fa-info-circle" style="color:#008cba" title="You may register without providing an email, but an administrator will have to manually activate such an account. Also, without a provided email you will not be able to reset your password."></i></label>
                  <input type="email" class="form-control" id="email" name="email" placeholder="user@yourdomain.ac.uk" value="${registerCommand.email}"  style="width:50%"/>
                  <g:if test="${errors?.email?.size() > 0}">
                    <ul>
                      <g:each var="e" in="${errors.email}">
                        <li>${e.message}</li>
                      </g:each>
                    </ul>
                  </g:if>
                </div>
                <div class="form-group ${registerCommand.errors.hasFieldErrors('password') ? 'has-error' : ''}">
                  <label for="password">Password (<span style="color:red">*</span>)</label>
                  <input autocomplete="false" type="password" class="form-control" id="password" name="password" placeholder="password" value="${registerCommand.password}" style="width:50%" />
                  <g:if test="${errors?.password?.size() > 0}">
                    <ul>
                      <g:each var="e" in="${errors.password}">
                        <li>${e.message}</li>
                      </g:each>
                    </ul>
                  </g:if>
                </div>
                <div class="form-group ${registerCommand.errors.hasFieldErrors('password2') ? 'has-error' : ''}">
                  <label for="password2">Confirm Password (<span style="color:red">*</span>)</label>
                  <input autocomplete="false" type="password" class="form-control" id="password2" name="password2" placeholder="password" value="${registerCommand.password2}" style="width:50%" />
                  <g:if test="${errors?.password2?.size() > 0}">
                    <ul>
                      <g:each var="e" in="${errors.password2}">
                        <li>${e.message}</li>
                      </g:each>
                    </ul>
                  </g:if>
                </div>
                <div class="form-group ${secFailed ? 'has-error' : ''}">
                  <label for="botFilter">What is ${secQuestion}? (<span style="color:red">*</span>)</label>
                  <input autocomplete="false" type="text" class="form-control" id="botFilter" name="secAnswer" style="width:50%" />
                  <g:if test="${secFailed}">
                    <ul><li>Please answer this question correctly.</li></ul>
                  </g:if>
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
