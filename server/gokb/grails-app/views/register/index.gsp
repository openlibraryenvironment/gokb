

<html>

<head>
  <meta name='layout' content='register'/>
  <title><g:message code='spring.security.ui.register.title'/></title>
</head>

<body>

   <div class="navbar navbar-fixed-top">
     <div class="navbar-header">
       <div class="container">
         <a class="navbar-brand" href="#">GOKb</a>
         <div class="nav-collapse">
           <ul class="nav">
           </ul>
         </div>
       </div>
     </div>
   </div>

<div class="navbar-push"></div>
&nbsp;<br/>




<div class="container">
  <div class="row"
    <div class='col-md-12'>


<s2ui:form width='650' height='300' elementId='loginFormContainer'
           titleCode='spring.security.ui.register.description' center='true'>

<g:form action='register' name='registerForm'>

	<g:if test='${emailSent}'>
	<br/>
	<g:message code='spring.security.ui.register.sent'/>
	</g:if>
	<g:else>

	<br/>

	<table>
	<tbody>

		<s2ui:textFieldRow name='username' labelCode='user.username.label' bean="${command}"
                         size='40' labelCodeDefault='Username' value="${command.username}"/>

		<s2ui:textFieldRow name='email' bean="${command}" value="${command.email}"
		                   size='40' labelCode='user.email.label' labelCodeDefault='E-mail'/>

		<s2ui:passwordFieldRow name='password' labelCode='user.password.label' bean="${command}"
                             size='40' labelCodeDefault='Password' value="${command.password}"/>

		<s2ui:passwordFieldRow name='password2' labelCode='user.password2.label' bean="${command}"
                             size='40' labelCodeDefault='Password (again)' value="${command.password2}"/>

	</tbody>
	</table>

	<s2ui:submitButton elementId='create' form='registerForm' messageCode='spring.security.ui.register.submit'/>

	</g:else>

</g:form>

</s2ui:form>
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
