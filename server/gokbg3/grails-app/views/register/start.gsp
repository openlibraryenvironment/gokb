<html>
<head>
  <meta name='layout' content='register'/>
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
            <div class="panel-heading">${message(code: 'registration.complete.label', default: 'Registration complete')}</div>
            <div class="panel-body">
              <g:if test="${noAddress}">
                <div class="alert alert-primary" style="font-weight:bolder">
                  ${message(code:'registration.pending')}
                </div>
              </g:if>
              <g:else>
                <div class="alert alert-primary" style="font-weight:bolder">
                  <g:message code='spring.security.ui.register.sent'/>
                </div>
              </g:else>
            </div>
          </div>
        </g:if>
        <g:elseif test="${noTries}">
          <div class="panel panel-default">
            <div class="panel-heading">${message(code:'registration.failed.label')}</div>
            <div class="panel-body">
              <div class="alert alert-primary" style="font-weight:bolder">
                <span style="padding:10px;">${message(code:'registration.questionFailed')}</span>
              </div>
            </div>
          </div>
        </g:elseif>
        <g:else>
          <div class="panel panel-default">
            <div class="panel-heading"><b>GOKb</b> – ${message(code:'spring.security.ui.register.header', locale: locale)}</div>
            <div class="panel-body">
              <g:form controller="register" action="start" class="form" role="form" params="[embed:true, lang: locale]">
                <div class="form-group ${registerCommand.errors.hasFieldErrors('email') ? 'has-error' : ''}">
                  <label for="email">${message(code:'spring.security.ui.register.email.label', locale: locale)} <i class="fas fa-info-circle" style="color:#008cba" title="${message(code:'registration.email.info', locale:locale)}"></i></label>
                  <input type="email" class="form-control" id="email" name="email" value="${registerCommand.email}" style="width:50%"/>
                  <g:if test="${errors?.email?.size() > 0}">
                    <ul>
                      <g:each var="e" in="${errors.email}">
                        <li>${e.message}</li>
                      </g:each>
                    </ul>
                  </g:if>
                </div>
                <div class="form-group ${registerCommand.errors.hasFieldErrors('username') ? 'has-error' : ''}">
                  <label for="username">${message(code:'spring.security.ui.register.username.label', locale: locale)} (<span style="color:red">*</span>)</label>
                  <input autocomplete="false" type="text" class="form-control" id="username" name="username" value="${registerCommand.username}" style="width:50%"/>
                  <input type="text" name="phone" value="" hidden="true" />
                  <g:if test="${errors?.username?.size() > 0}">
                    <ul>
                      <g:each var="e" in="${errors.username}">
                        <li>${e.message}</li>
                      </g:each>
                    </ul>
                  </g:if>
                </div>
                <div class="form-group ${registerCommand.errors.hasFieldErrors('password') ? 'has-error' : ''}">
                  <label for="password">${message(code:'spring.security.ui.register.password.label', locale: locale)} (<span style="color:red">*</span>)</label>
                  <input autocomplete="false" type="password" class="form-control" id="password" name="password" value="${registerCommand.password}" style="width:50%" />
                  <g:if test="${errors?.password?.size() > 0}">
                    <ul>
                      <g:each var="e" in="${errors.password}">
                        <li>${e.message}</li>
                      </g:each>
                    </ul>
                  </g:if>
                </div>
                <div class="form-group ${registerCommand.errors.hasFieldErrors('password2') ? 'has-error' : ''}">
                  <label for="password2">${message(code:'spring.security.ui.register.password2.label', locale: locale)} (<span style="color:red">*</span>)</label>
                  <input autocomplete="false" type="password" class="form-control" id="password2" name="password2" value="${registerCommand.password2}" style="width:50%" />
                  <g:if test="${errors?.password2?.size() > 0}">
                    <ul>
                      <g:each var="e" in="${errors.password2}">
                        <li>${e.message}</li>
                      </g:each>
                    </ul>
                  </g:if>
                </div>
                <div class="form-group ${secFailed ? 'has-error' : ''}">
                  <label for="botFilter">${message(code:'spring.security.ui.register.mathCheck.label', args:[secQuestion], locale: locale)} (<span style="color:red">*</span>)</label>
                  <input autocomplete="false" type="text" class="form-control" id="botFilter" name="secAnswer" style="width:50%" />
                  <g:if test="${secFailed}">
                    <ul><li>${message(code:'spring.security.ui.register.mathCheck.error', locale: locale)}</li></ul>
                  </g:if>
                </div>
                <div class="form-group">
                  <label for="submit"></label>
                  <button type="submit" class="btn btn-default">${message(code:'spring.security.ui.register.submit', locale: locale)}</button>
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
