<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle"/>
    <title>GOKbo : About</title>
  </head>
  <body>

   <div class="container">
     <div class="row">
       <div class="well col-md-12">

         <h1>Application Info</h1>
         <table class="table table-bordered">
           <tr><td>Build Number</td><td> <g:meta name="app.buildNumber"/></td></tr>
           <tr><td>Build Profile</td><td> <g:meta name="app.buildProfile"/></td></tr>
           <tr><td>App version</td><td> <g:meta name="app.version"/></td></tr>
           <tr><td>Grails version</td><td> <g:meta name="app.grails.version"/></td></tr>
           <tr><td>Groovy version</td><td> ${GroovySystem.getVersion()}</td></tr>
           <tr><td>JVM version</td><td> ${System.getProperty('java.version')}</td></tr>
           <tr><td>Reloading active</td><td> ${grails.util.Environment.reloadingAgentEnabled}</td></tr>
           <tr><td>Build Date</td><td> <g:meta name="app.buildDate"/></td></tr>
         </table>
       </div>

     </div>
   </div>
  
  </body>
</html>
