<html>
<head>
  <meta name='layout' content='public'/>
  <title>Password Reset</title>
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

        <g:form action='resetPassword' name="forgotPasswordForm" autocomplete='off' class="form" role="form">
          <g:hiddenField name='t' value='${token}'/>
          <div class="sign-in">

          <br/>
          <h4><g:message code='spring.security.ui.resetPassword.description'/></h4>

          <div class="form-group">
            <label for="password">Password</label>
            <g:passwordField class="form-control" type="password" id="password"  name="password" size="25" />
          </div>

          <div class="form-group">
            <label for="password2">Repeat Password</label>
            <g:passwordField class="form-control" type="password" id="password2"  name="password2" size="25" />
          </div>

          <div class="form-group">
            <button type="submit" value="Submit" class="btn btn-success">Reset Password...</button>
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
