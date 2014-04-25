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
           <h2>Cross Reference Title</h2>
           <p>
             Tell the system what you know about a particular title (Specifically your local identifiers). This will enable GOKb to cross reference
             well known public identifiers with your local system identifiers. GOKb can then support coreferencing of your local identifiers, and identify
             titles using local proprietary naming schemes.
           </p>
           <p>Usage:
             Send a request to <g:link controller="integration" action="crossReferenceTitle"><g:createLink controller="integration" action="crossReferenceTitle"/></g:link> with a json document containing the following: <table class="table table-bordered">
  <tr> <th>Property</th><th>Mandatory?</th><th>Description/Type</th></tr>
  <tr> <td>title</td>       <td>Yes</td> <td>String</td> </tr>
  <tr> <td>publisher</td>   <td>No</td>  <td>String</td> </tr>
  <tr> <td>identifiers</td> <td>Yes</td> <td>JSON Array of objects. Each object MUST have a type and value property</td> </tr>
</table>
Example:
<code><pre> {
  title:'The title',
  publisher:'The title',
  identifiers:[
                   {'type':'ISBN', 'value':'1234-5678'},
                   {'type':'MyPrivateNamespace', 'value':'xx99xx88xx77'}
  ]
}</pre></code>

The following CURL command will tell GoKB about the JUSP title for "3 Biotech"
<code><pre>
curl -v --user user:pass -X POST -H "Content-Type: application/json" -d '{"title":"3 Biotech",identifiers:[{"type":"eissn","value":"2190-5738"},{"type":"jusp","value":"6416"}]}' <g:createLink controller="integration" action="crossReferenceTitle"/>
</pre></code>
           </p>
           <p>Response:
             The server will return a json document as follows
<code><pre> {
  response:--code--
} </pre></code>
             where code can be one of <ul><li>OK -</li><li>No Match</li><li>Conflict</li></ul>
           </p>
     </div>
   </div>
  
  </body>
</html>
