<html>
<head>
  <meta name='layout' content='public'/>
  <title><g:message code='spring.security.ui.resetPassword.title'/></title>
  <asset:stylesheet src="gokb/gokb-brand.css"/>
</head>

<body>
  <nav class="navbar navbar-default" id="primary-nav-bar" role="navigation" style="height:64px">
    <div class="container">
      <!-- Brand and toggle get grouped for better mobile display -->
      <div class="navbar-brand" style="font-face:bold">
        <g:message code="gokb.appname" default="GOKb" />
      </div>
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

        <g:form action='resetPassword' name="forgotPasswordForm" autocomplete='off' class="form" role="form">
          <g:hiddenField name='t' value='${token}'/>
          <div class="sign-in">

          <br/>
          <h4><g:message code='spring.security.ui.resetPassword.description'/></h4>

          <div class="form-group">
            <label for="password">${message(code:'spring.security.ui.resetPassword.new', locale:locale)}</label>
            <g:passwordField class="form-control" type="password" id="password"  name="password" size="25" />
          </div>

          <div class="form-group">
            <label for="password2">${message(code:'spring.security.ui.resetPassword.repeat', locale:locale)}</label>
            <g:passwordField class="form-control" type="password" id="password2"  name="password2" size="25" />
          </div>

          <div class="form-group">
            <button type="submit" value="Submit" class="btn btn-success">
              ${message(code:'spring.security.ui.resetPassword.submit', locale:locale)}
            </button>
          </div>

        </div>
      </g:form>
    </div>


<script>
$(document).ready(function() {
  $('#password').focus();
});
</script>

</body>
</html>
