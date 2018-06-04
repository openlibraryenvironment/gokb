<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="sb-admin"/>
    <title>Grails Runtime Exception</title>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'errors.css')}" type="text/css">
  </head>
  <body>
    <g:renderException exception="${exception}" />
  </body>
</html>
