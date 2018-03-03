<!DOCTYPE html>
<html>
  <head>
    <title>Grails Runtime Exception</title>
    <meta name="layout" content="public"/>
    <link rel="stylesheet" href="${resource(dir: 'css', file: 'errors.css')}" type="text/css">
  </head>
  <body>
    <g:renderException exception="${exception}" />
  </body>
</html>
