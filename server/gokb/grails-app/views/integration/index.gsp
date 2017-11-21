<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: Integration API</title>
</head>
<body>
  <h1 class="page-header">GOKb Integration API</h1>
   <div id="mainarea" class="panel panel-default ${displayobj != null ? 'col-md-5 ' : ''}">
      <div class="panel-heading">
        <h3 class="panel-title">
          Cross Reference Title
        </h3>
      </div>
      <div class="panel-body">
        <p>Tell the system what you know about a particular title
          (Specifically your local identifiers). This will enable GOKb to
          cross reference well known public identifiers with your local system
          identifiers. GOKb can then support coreferencing of your local
          identifiers, and identify titles using local proprietary naming
          schemes.</p>
        <h4>Usage:</h4>
        <p>Send a request to
          <g:link controller="integration" action="crossReferenceTitle">
            <g:createLink controller="integration" action="crossReferenceTitle" />
          </g:link> with a JSON document containing the following:</p>
      </div>
      <!-- panel-body -->
      <table class="table table-bordered">
        <tr>
          <th>Property</th>
          <th>Mandatory?</th>
          <th>Description/Type</th>
        </tr>
        <tr>
          <td>title</td>
          <td>Yes</td>
          <td>String</td>
        </tr>
        <tr>
          <td>publisher</td>
          <td>No</td>
          <td>String</td>
        </tr>
        <tr>
          <td>identifiers</td>
          <td>Yes</td>
          <td>JSON Array of objects. Each object MUST have a type and
            value property</td>
        </tr>
      </table>

      <div class="panel-footer" >      
        <h4>Example:</h4>
        <pre>
          <code>
{
  title:'The title',
  publisher:'The title',
  identifiers:[
    {'type':'ISBN', 'value':'1234-5678'},
    {'type':'MyPrivateNamespace', 'value':'xx99xx88xx77'}
  ]
}
          </code>
        </pre>
  
        <p>The following CURL command will tell GOKb about the JUSP title for &quot;3 Biotech&quot;</p>
        <pre>
          <code>
curl -v --user user:pass -X POST -H "Content-Type: application/json" -d '{"title":"3 Biotech",identifiers:[{"type":"eissn","value":"2190-5738"},{"type":"jusp","value":"6416"}]}' <g:createLink  controller="integration" action="crossReferenceTitle" />
          </code>
        </pre>
        <h4>Response:</h4>
        <p>The server will return a JSON document as follows</p>
        <pre>
          <code>
{
  response:--code--
} 
          </code>
        </pre>
        <p>where <strong>code</strong> can be one of</p>
        <ul>
          <li>OK</li>
          <li>No Match</li>
          <li>Conflict</li>
        </ul>
      </div>

      <div class="panel-footer" >      
        <h4>Example:</h4>
        <pre>
          <code>
{
  title:'The title',
  publisher:'The title',
  identifiers:[
    {'type':'ISBN', 'value':'1234-5678'},
    {'type':'MyPrivateNamespace', 'value':'xx99xx88xx77'}
  ]
}
          </code>
        </pre>
  
        <p>The following CURL command will tell GOKb about the JUSP title for &quot;3 Biotech&quot;</p>
        <pre>
          <code>
curl -v --user user:pass -X POST -H "Content-Type: application/json" -d '{"title":"3 Biotech",identifiers:[{"type":"eissn","value":"2190-5738"},{"type":"jusp","value":"6416"}]}' <g:createLink  controller="integration" action="crossReferenceTitle" />
          </code>
        </pre>
        <h4>Response:</h4>
        <p>The server will return a JSON document as follows</p>
        <pre>
          <code>
{
  response:--code--
} 
          </code>
        </pre>
        <p>where <strong>code</strong> can be one of</p>
        <ul>
          <li>OK</li>
          <li>No Match</li>
          <li>Conflict</li>
        </ul>
      </div>
    </div>

   <div id="mainarea" class="panel panel-default ${displayobj != null ? 'col-md-5 ' : ''}">
      <div class="panel-heading">
        <h3 class="panel-title">
          Load Title List
        </h3>
      </div>
      <div class="panel-body">
        <p>Upload a tsv of title data formed as Title	p-ISSN	e-ISSN</p>
        <p>
          <g:form action="loadTitleList" enctype="multipart/form-data">
            <input type="file" id="titleFile" name="titleFile"/>
            <input type="submit"/>
          </g:form>
        </p>
      </div>
      <div class="panel-footer" >      
      </div>
    </div>
  </div>


</body>
</html>
