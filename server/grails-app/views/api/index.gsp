<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <title>GOKb API</title>
  </head>
  <body>
    <div class="container">
      API Page
      <hr/>
      <h2>Describe Service</h2>
      <p>This form allows you to interact with the describe service provided <g:link controller="api" action="describe">here</g:link>. The describe service
      accepts a file attachment which is a JSON document describing an RIFF (Raw Input Format File). The service responds with a JSON document
      listing the rules the service believes should be applied to the RIFF to turn it into a RED (Rules Enhanced Data) file which meets the
      GOKb ingest format specification. RED files meeting this format are ingested using the <g:link controller="api" action="ingest">ingest</g:link> api.</p>
      <g:form controller="api" action="describe" method="POST">
        <input type="file" name="riff_definition"/>
        <input type="submit"/>
      </g:form>
      <h2>Ingest Service</h2>
      <p>Ingest a RED compliant JSON document</p>
    </div>
  </body>
</html>
