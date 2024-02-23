<html>
<head>
  <meta name='layout' content='register'/>
  <title><g:message code='spring.security.ui.forgotPassword.title'/></title>
  <g:if test="${embed}">
    <asset:stylesheet src="gokb/gokb-brand.css"/>
  </g:if>
</head>

<body>
  <g:if test="${embed}">
    <nav class="navbar navbar-inverse" id="primary-nav-bar" role="navigation" style="height:64px">
  </g:if>
  <g:else>
    <nav class="navbar navbar-default" id="primary-nav-bar" role="navigation">
  </g:else>
    <div class="container">
      <!-- Brand and toggle get grouped for better mobile display -->
      <g:if test="${!embed}">
        <div class="navbar-brand" style="font-face:bold">
          <g:message code="gokb.appname" default="GOKb" />
        </div>
      </g:if>
    </div>
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
          <div class="alert alert-error" style="display: block">${flash.error}</div>
        </g:if>

        <g:if test="${flash.message}">
          <div class="info alert-info" style="display: block">${flash.message}</div>
        </g:if>


      <g:form action='forgotPassword' name="forgotPasswordForm" autocomplete='off' class="form" role="form">

        <g:if test='${emailSent}'>
          <br/>
          <g:message code='spring.security.ui.forgotPassword.sent'/>
        </g:if>

        <g:else>
          <br/>
          <h4><g:message code='spring.security.ui.forgotPassword.description'/></h4>

          <div class="form-group">
            <g:textField class="form-control" id="username"  placeholder="${message(code: 'spring.security.ui.register.username.label')}" name="username" size="25" />
          </div>

          <div class="form-group">
            <button type="submit" value="Submit" class="btn btn-default"><g:message code='spring.security.ui.forgotPassword.submit' /></button>
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
