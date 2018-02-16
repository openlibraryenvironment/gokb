<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <title>GOKb : Coreference Service</title>
  </head>
  <body>

   <div class="container">
     <div class="row">
       <p>
         <h2>Coreference Search Service</h2>
         Search for objects in the knowledgebase. Parameters are as follows:
         <ul>
           <li><h3>Target</h3>The target collection. Currently allowed values are <ul><li>tipp</li><li>platform</li><li>package</li></ul></li>
           <li>When target is "tipp" the following additional properties are available
             <ul>
               <li>cp - Restrict to tipps where the content provider is ? - Example
                     <g:form method="get" action="search"><input type="hidden" name="target" value="tipp"/>Content Provider:<input type="text" name="cp"/></g:form>
               </li>
             </ul>
         </ul>
       </p>
       
     </div>
   </div>
  
  </body>
</html>
