<html>
<head>
  <meta name='layout' content='register'/>
  <title><g:message code='spring.security.ui.resetPassword.title'/></title>
  <asset:stylesheet src="gokb/gokb-brand.css"/>
</head>

<body>
  <nav class="navbar navbar-inverse" id="primary-nav-bar" role="navigation" style="height:64px">
    <!-- Brand and toggle get grouped for better mobile display -->
    <a href="${grailsApplication.config.getProperty('gokb.uiUrl')}" class="navbar-brand"><g:message code="gokb.appname" default="GOKB" /></a>
  </nav>
  <div class="container">
    <div class="row">
      <div class='col-md-12'>
        &nbsp;
      </div>
    </div>
    <div class="row">
      <div class='col-md-12'>
        <g:if test="${flash.error}">
          <div class="alert alert-error" style="display: block;font-weight:500;font-size:1.1em;">${flash.error}</div>
        </g:if>

        <g:if test="${flash.message}">
          <div class="info alert-info" style="display: block">${flash.message}</div>
        </g:if>

        <g:if test="${success}">
          <button class="btn btn-success" href="${grailsApplication.config.getProperty('gokb.uiUrl')}">${message(code:'spring.security.ui.resetPassword.continue', locale:locale)}</button>
        </g:if>
        <g:elseif test="${token}">
          <g:form action='resetPasswordExt' name="forgotPasswordForm" autocomplete='off' class="form" role="form">
            <g:hiddenField name='t' value='${token}'/>
            <h1><g:message code='spring.security.ui.resetPassword.header'/></h1>

            <label for="password">
              ${message(code:'spring.security.ui.register.password.label', locale: locale)} (
                <span style="color:red">*</span>
              )
            </label>
            <div
              class="input-group input-group-sm ${resetPasswordCommand.errors.hasFieldErrors('password') ? 'has-error' : ''}"
              style="margin-bottom:12px;width:100%"
            >
              <input
                autocomplete="false"
                type="password"
                class="form-control"
                id="password"
                name="password"
                value="${resetPasswordCommand.password}"
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
              ${message(code:'spring.security.ui.resetPassword.repeat', locale: locale)} (
                <span style="color:red">*</span>
              )
            </label>
            <div
              class="input-group input-group-sm ${resetPasswordCommand.errors.hasFieldErrors('password2') ? 'has-error' : ''}"
              style="margin-bottom:12px;width:100%"
            >
              <input
                autocomplete="false"
                type="password"
                class="form-control"
                id="password2"
                name="password2"
                value="${resetPasswordCommand.password2}"
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

            <div class="form-group">
              <button type="submit" value="Submit" class="btn btn-success">
                ${message(code:'spring.security.ui.resetPassword.submit', locale:locale)}
              </button>
            </div>
          </g:form>
        </g:elseif>
      </div>
    </div>
  </div>


<script>
$(document).ready(function() {
  $('#password').focus();
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
