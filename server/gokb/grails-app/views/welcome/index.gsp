<!DOCTYPE html>
<html>
  <head>
    <meta name='layout' content='register'/>
    <r:require modules="gokbstyle"/>
    <title>GOKb</title>
    <script type="text/javascript" src="http://www.google.com/jsapi"></script>
  </head>
  <body>

   <div class="container">
     <div class="row">
       Stats
       Headers...
     </div>
   </div>

   <br/>&nbsp;<br/>
   <h3>Package Additions</h3>
   <div id="packageAdditions">
   </div>
  

   <br/>&nbsp;<br/>
   <h3>Title Additions</h3>
   <div id="titleAdditions">
   </div>

   <br/>&nbsp;<br/>
   <h3>Organisation Additions</h3>
   <div id="orgAdditions">
   </div>


    <gvisualization:lineCoreChart elementId="titleAdditions" width="${400}" height="${240}" title="Title Additions" columns="${colHeads1}" data="${titleAdditionData}" />
    <gvisualization:lineCoreChart elementId="packageAdditions" width="${400}" height="${240}" title="Package Additions" columns="${colHeads1}" data="${packageAdditionData}" />
    <gvisualization:lineCoreChart elementId="orgAdditions" width="${400}" height="${240}" title="Org Additions" columns="${colHeads1}" data="${orgAdditionData}" />

  </body>
</html>
