<html>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
	<meta name="layout" content="none"/>
	<title><g:message code="default.page.title.label" default="File List" /></title>
    <style type="text/css">
        * {margin: 0;padding: 0;}
        body {font-size: 100.01%;font-family: Arial, sans-serif;color: #333;background: #f8f8f8;padding: 10px;}
        h1 {color: #363;font-size: 1.2em;margin: .5em 0;}
        p, pre, li {margin: 0 0 .5em 0;list-style: square;}
        ul {margin: 1em;}
        pre {background: #eee;border: 1px solid #999;padding: .5em;margin: .5em;font-size: .9em;}
        a {color: #369;font-size: .8em;}
        div.error{background: #ff0000;margin: 10px;}
    </style>
</head>
<body>
<br/><strong>
	<g:message code="default.page.body.detail" default="Please click on the links below to view detailed reports:" />
</strong><br/><br/>
<g:if test="${errorMessage}"><div class="error">${errorMessage}</div></g:if>
<g:if test="${showBackLink}">
	<div id="backLink">
		<a class="showReportLink" href="${createLink(action: 'index', params: [filePath: prevLocation])}">
			<g:message code="default.link.back.label" default="Back" />
		</a>
	</div>
</g:if>
<div id="mainContainer">
    <div id="left-container" style="width:30%; float:left; overflow: auto;">
        <g:render template="/file/fileList" model="[locations:locations]" plugin='fileViewer'/>
    </div>
    <div id="right-container" style="width:69%; float:right;border:1px black solid;padding:5px;">
        <g:render template="/file/fileDetails" model="[fileContents: fileContents, filePath: filePath]"
                  plugin='fileViewer'/>
    </div>
</div>
</body>
</html>