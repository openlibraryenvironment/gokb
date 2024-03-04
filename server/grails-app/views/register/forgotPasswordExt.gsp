<html>
<head>
  <meta name='layout' content='register'/>
  <title><g:message code='spring.security.ui.forgotPassword.title'/></title>
  <g:if test="${embed}">
    <asset:stylesheet src="gokb/gokb-brand.css"/>
  </g:if>
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


      <g:form action='forgotPasswordExt' name="forgotPasswordForm" autocomplete='off' class="form" role="form">

        <g:if test='${emailSent}'>
          <br/>
          <g:message code='spring.security.ui.forgotPassword.sent'/>
        </g:if>

        <g:else>
          <br/>
          <h4><g:message code='spring.security.ui.forgotPassword.description'/></h4>

          <label for="username"  style="margin-top:20px">
            ${message(code:'spring.security.ui.register.username.label', locale: locale)} (
              <span style="color:red">*</span>
            )
          </label>
          <div class="form-group">
            <g:textField class="form-control" id="username"  placeholder="${message(code: 'spring.security.ui.register.username.label', locale: locale)}" name="username" size="25" />
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

          <div class="form-group" style="margin-top:20px">
            <button type="submit" value="Submit" class="btn btn-default">
              <g:message code='spring.security.ui.forgotPassword.submit' />
            </button>
          </div>

        </g:else>

      </g:form>
    </div>
  </div>
</div>

<script>
$(document).ready(function() {
  $('#username').focus();
});
</script>

</body>
</html>
