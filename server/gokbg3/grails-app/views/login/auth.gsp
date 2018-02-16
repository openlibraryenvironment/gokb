<html>
<head>
<title><g:message code="springSecurity.login.title" /></title>
<meta name='layout' content='sb-admin' />
</head>

<body>

	<h1 class="page-header">Sign in</h1>
	<div id="mainarea" class="panel panel-default">
		<div class="panel-heading">
			<h3 class="panel-title">User details</h3>
		</div>
                <g:if test="${params.login_error == '1' && flash.message}">
                  <div class="panel-body">
                    <div class="alert alert-danger">
                      ${flash.message}
                    </div>
                  </div>
                </g:if>
                <g:else>
                  <div class="panel-body">
                          <p>Use the form below to sign into GOKb. If you don't have an account yet, please create one by using the register link in the top menu.</p>
                  </div>
                </g:else>
		<div class="panel-footer clearfix" >
			<form action='${postUrl}' method='POST' id='loginForm'
				class='form-horizontal col-md-6 col-md-offset-3' autocomplete='off' role="form">

				<div class="form-group">
					<label class="control-label" for='username'><g:message
							code="springSecurity.login.username.label" />:</label>
					<input type='text'
						class='form-control' name='j_username' id='username' />
				</div>

				<div class="form-group">
					<label class="control-label" for='password'><g:message
							code="springSecurity.login.password.label" />:</label> <input
						type='password' class='form-control' name='j_password'
						id='password' />
				</div>
				
				<p>Have you <g:link controller="register" action="forgotPassword" >forgotten your password?</g:link></p>

				<div class="form-group" id="remember_me_holder">
					<input type='checkbox' class='chk' name='${rememberMeParameter}'
						id='remember_me'
						<g:if test='${hasCookie}'>checked='checked'</g:if> /> <label
						for='remember_me'><g:message
							code="springSecurity.login.remember.me.label" /></label>
				</div>
				<div class="form-group pull-right" >
					<button type='submit' class="btn btn-default btn-sm" id="submit" >${message(code: "springSecurity.login.button")}</button>
				</div>
			</form>
		</div>
	</div>
</body>
</html>
