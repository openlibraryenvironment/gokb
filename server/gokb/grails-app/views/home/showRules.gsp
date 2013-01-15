<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle"/>
    <title>GOKbo : Show Rules</title>
  </head>
  <body>

   <div class="container-fluid">
     <div class="row-fluid">
       <div id="sidebar" class="span12">
         Rules..
         <g:each in="rules" var="r">
           ${r}
         </g:each>
       </div>
     </div>
   </div>
  
  </body>
</html>
