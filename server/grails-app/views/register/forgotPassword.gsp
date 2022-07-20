<html>
<head>
  <meta name='layout' content='sb-admin'/>
  <title><g:message code='spring.security.ui.forgotPassword.title'/></title>
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


      <g:form action='forgotPassword' name="forgotPasswordForm" autocomplete='off' class="form" role="form">

        <g:if test='${emailSent}'>
          <br/>
          <g:message code='spring.security.ui.forgotPassword.sent'/>
        </g:if>

        <g:else>
          <br/>
          <h4><g:message code='spring.security.ui.forgotPassword.description'/></h4>

          <div class="form-group">
            <label for="username">Username</label>
            <g:textField class="form-control" id="username"  placeholder="Requested Username" name="username" size="25" />
          </div>

          <div class="form-group">
            <button type="submit" value="Submit" class="btn btn-success">Send Password Recovery Email...</button>
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
