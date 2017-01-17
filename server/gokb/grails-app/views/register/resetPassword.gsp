<html>
<head>
  <meta name='layout' content='sb-admin'/>
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

        <g:form action='forgotPassword' name="forgotPasswordForm" autocomplete='off' class="form" role="form">
          <g:hiddenField name='t' value='${token}'/>
          <div class="sign-in">

          <br/>
          <h4><g:message code='spring.security.ui.resetPassword.description'/></h4>

          <div class="form-group">
            <label for="username">Password</label>
            <g:textField class="form-control" type="password" id="username"  name="password" size="25" />
          </div>

          <div class="form-group">
            <label for="username">Repeat Password</label>
            <g:textField class="form-control" type="password" id="username"  name="password2" size="25" />
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
