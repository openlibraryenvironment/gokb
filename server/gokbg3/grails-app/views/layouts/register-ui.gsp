<html>

<head>

<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
<title><g:layoutTitle default='User Registration' /></title>

<link rel="shortcut icon" href="${resource(dir: 'images', file: 'favicon.ico')}" type="image/x-icon">
<g:layoutHead />
<asset:script> var contextPath="${grailsApplication.config.server.contextPath ?: ''}"; </asset:script>
<asset:javascript src="gokb/application.grass.js" />
<asset:stylesheet src="gokb/sb-admin-2.css"/>
<asset:stylesheet src="gokb/themes/${ grailsApplication.config.gokb.theme }/theme.css"/>
<asset:stylesheet src="gokb/application.css"/>

<g:layoutHead />

</head>

<body>

	<g:layoutBody />

	<s2ui:showFlash />

</body>
</html>
