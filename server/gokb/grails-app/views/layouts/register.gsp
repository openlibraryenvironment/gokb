<html>

<head>

<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />

<title><g:layoutTitle default='User Registration'/></title>

<link rel="shortcut icon" href="${resource(dir:'images',file:'favicon.ico')}" type="image/x-icon"/>

   <r:require modules="register,gokbstyle,bootstrap-popover"/>
   <r:layoutResources/>

<g:layoutHead/>

</head>

<body>

<s2ui:layoutResources module='register' />

<g:layoutBody/>


<%--
<g:javascript src='jquery/jquery.jgrowl.js' plugin='spring-security-ui'/>
<g:javascript src='jquery/jquery.checkbox.js' plugin='spring-security-ui'/>
<g:javascript src='spring-security-ui.js' plugin='spring-security-ui'/>
--%>

<s2ui:showFlash/>

</body>
</html>
