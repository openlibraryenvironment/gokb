<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle"/>
    <title>GOKb</title>
  </head>
  <body>

   <div class="container-fluid">
     <div class="row-fluid">
       <h1>Gokb Integration API</h1>
       <ul>
         <li>
           <h2>Cross Reference Title</h2>
           <p>
             Tell the system what you know about a particular title (Specifically your local identifiers). This will enable GOKb to cross reference
             well known public identifiers with your local system identifiers. GOKb can then support coreferencing of your local identifiers, and identify
             titles using local proprietary naming schemes.
           </p>
           <p>Usage:
             Send a request to <g:link controller="integration" action="crossReferenceTitle"><g:createLink controller="integration" action="crossReferenceTitle"/></g:link> with the JSON Body
<code><pre> {
  title:'The title',
  identifiers:[
                   {'namespace':'ISBN', 'value':'1234-5678'},
                   {'namespace':'MyPrivateNamespace', 'value':'xx99xx88xx77'}
  ]
}</pre></code>
           </p>
           <p>Response:
             The server will return a json document as follows
<code><pre> {
  response:--code--
} </pre></code>
             where code can be one of <ul><li>OK -</li><li>No Match</li><li>Conflict</li></ul>
           </p>
         </li>
       </ul>
     </div>
   </div>
  
  </body>
</html>
