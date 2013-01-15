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

         <table class="table table-striped">
           <caption>Active upload rules</caption>
           <thead>
             <tr>
               <th>Type</th>
               <th>Rule</th>
               <th>Parameters</th>
             </tr>
           </thead>
           <tbody>
             <g:each in="${rules}" var="r">
               <tr>
                 <td>${r.type}</td>
                 <td>${r.rule}</td>
                 <td>
                   <g:if test="${r.colnames}">
                     <g:each in="${r.colnames}" var="c">${c}</g:each>
                   </g:if>
                 </td>
               </tr>
             </g:each>
           </tbody>
         </table>
       </div>
     </div>
   </div>
  
  </body>
</html>
