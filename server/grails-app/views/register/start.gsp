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
            <div class="panel-heading">
              ${message(code: 'registration.complete.label', default: 'Registration complete')}
            </div>
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
            <div class="panel-heading">
              ${message(code:'registration.failed.label')}
            </div>
            <div class="panel-body">
              <div class="alert alert-primary" style="font-weight:bolder">
                <span style="padding:10px;">${message(code:'registration.questionFailed')}</span>
              </div>
            </div>
          </div>
        </g:elseif>
        <g:else>
          <div class="panel panel-default">
            <div class="panel-heading">
              <b>GOKb</b> – ${message(code:'spring.security.ui.register.header', locale: locale)}
            </div>
            <div class="panel-body">
              <g:form
                controller="register"
                action="start"
                class="form"
                role="form"
                params="[embed:true, lang: locale]"
              >
                <label for="email">
                  ${message(code:'spring.security.ui.register.email.label', locale: locale)}
                  <i
                    class="fas fa-info-circle"
                    style="color:#008cba"
                    title="${message(code:'registration.email.info', locale:locale)}"
                  >
                  </i>
                </label>
                <div
                  class="input-group input-group-sm ${registerCommand.errors.hasFieldErrors('email') ? 'has-error' : ''}"
                  style="margin-bottom:12px;width:100%"
                >
                  <input
                    type="email"
                    class="form-control"
                    id="email"
                    name="email"
                    value="${registerCommand.email}"
                  />
                  <g:if test="${errors?.email?.size() > 0}">
                    <ul>
                      <g:each var="e" in="${errors.email}">
                        <li>${e.message}</li>
                      </g:each>
                    </ul>
                  </g:if>
                </div>
                <label for="username">
                  ${message(code:'spring.security.ui.register.username.label', locale: locale)} (
                    <span style="color:red">*</span>
                  )
                </label>
                <div
                  class="input-group input-group-sm ${registerCommand.errors.hasFieldErrors('username') ? 'has-error' : ''}"
                  style="margin-bottom:12px;width:100%"
                >
                  <input
                    autocomplete="false"
                    type="text"
                    class="form-control"
                    id="username"
                    name="username"
                    value="${registerCommand.username}"
                  />
                  <input type="text" name="phone" value="" hidden="true" />
                  <g:if test="${errors?.username?.size() > 0}">
                    <ul>
                      <g:each var="e" in="${errors.username}">
                        <li>${e.message}</li>
                      </g:each>
                    </ul>
                  </g:if>
                </div>
                <label for="password">
                  ${message(code:'spring.security.ui.register.password.label', locale: locale)} (
                    <span style="color:red">*</span>
                  )
                </label>
                <div
                  class="input-group input-group-sm ${registerCommand.errors.hasFieldErrors('password') ? 'has-error' : ''}"
                  style="margin-bottom:12px;width:100%"
                >
                  <input
                    autocomplete="false"
                    type="password"
                    class="form-control"
                    id="password"
                    name="password"
                    value="${registerCommand.password}"
                  />
                  <span class="input-group-btn">
                    <button
                      class="btn btn-default"
                      id="showPassword"
                      type="button"
                    >
                      <i
                        class="fas fa-eye"
                        id="showPasswordIcon"
                        title="${message(code:'registration.password.toggle', locale:locale)}"
                      >
                      </i>
                    </button>
                  </span>
                  <g:if test="${errors?.password?.size() > 0}">
                    <ul>
                      <g:each var="e" in="${errors.password}">
                        <li>${e.message}</li>
                      </g:each>
                    </ul>
                  </g:if>
                </div>
                <label for="password2">
                  ${message(code:'spring.security.ui.register.password2.label', locale: locale)} (
                    <span style="color:red">*</span>
                  )
                </label>
                <div
                  class="input-group input-group-sm ${registerCommand.errors.hasFieldErrors('password2') ? 'has-error' : ''}"
                  style="margin-bottom:12px;width:100%"
                >
                  <input
                    autocomplete="false"
                    type="password"
                    class="form-control"
                    id="password2"
                    name="password2"
                    value="${registerCommand.password2}"
                  />
                  <span class="input-group-btn">
                    <button
                      class="btn btn-default"
                      id="showPassword2"
                      type="button"
                    >
                      <i
                        class="fas fa-eye"
                        id="showPassword2Icon"
                        title="${message(code:'registration.password.toggle', locale:locale)}"
                      >
                      </i>
                    </button>
                  </span>
                  <g:if test="${errors?.password2?.size() > 0}">
                    <ul>
                      <g:each var="e" in="${errors.password2}">
                        <li>${e.message}</li>
                      </g:each>
                    </ul>
                  </g:if>
                </div>
                <label for="botFilter">
                  ${message(code:'spring.security.ui.register.mathCheck.label', args:[secQuestion], locale: locale)} (
                    <span style="color:red">*</span>
                  )
                </label>
                <div
                  class="input-group input-group-sm ${secFailed ? 'has-error' : ''}"
                  style="margin-bottom:12px"
                >
                  <input autocomplete="false" type="text" class="form-control" id="botFilter" name="secAnswer" />
                  <g:if test="${secFailed}">
                    <ul>
                      <li>${message(code:'spring.security.ui.register.mathCheck.error', locale: locale)}</li>
                    </ul>
                  </g:if>
                </div>
                <div
                  class="input-group input-group-sm ${agrFailed ? 'has-error' : ''}"
                  style="margin-bottom:12px"
                >
                  <input
                    type="checkbox"
                    id="agreement"
                    name="agreement"
                  />
                  <label for="agreement">
                    ${message(code:'spring.security.ui.register.agreement.label', locale: locale)} (
                      <span style="color:red">*</span>
                    )
                  </label>
                  <g:if test="${agrFailed}">
                    <ul>
                      <li>${message(code:'spring.security.ui.register.agreement.error', locale: locale)}</li>
                    </ul>
                  </g:if>
                </div>
                <div class="input-group">
                  <label for="submit"></label>
                  <button type="submit" class="btn btn-default">
                    ${message(code:'spring.security.ui.register.submit', locale: locale)}
                  </button>
                </div>
              </g:form>
            </div>
          </div>
        </g:else>
      </div>
    </div>
  </div>

<script>
$(document).ready(function() {
  $('#email').focus();
});

const togglePassword = document.querySelector('#showPassword');
const passwordIcon = document.querySelector('#showPasswordIcon');
const password = document.querySelector('#password');

togglePassword.addEventListener('click', () => {
    // Toggle the type attribute using
    // getAttribure() method
    const type = password.getAttribute('type') === 'password' ? 'text' : 'password';
    password.setAttribute('type', type);
    // Toggle the eye and bi-eye icon
    passwordIcon.classList.toggle('fa-eye');
    passwordIcon.classList.toggle('fa-eye-slash');
});

const togglePasswordTwo = document.querySelector('#showPassword2');
const passwordTwoIcon = document.querySelector('#showPassword2Icon');
const passwordTwo = document.querySelector('#password2');

togglePasswordTwo.addEventListener('click', () => {
    // Toggle the type attribute using
    // getAttribure() method
    const type = passwordTwo.getAttribute('type') === 'password' ? 'text' : 'password';
    passwordTwo.setAttribute('type', type);
    // Toggle the eye and bi-eye icon
    passwordTwoIcon.classList.toggle('fa-eye');
    passwordTwoIcon.classList.toggle('fa-eye-slash');
});
</script>
</body>
</html>