<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle"/>
    <title>GOKb::OAI Interface</title>
  </head>
  <body>
    <div class="container-fluid">
      <div class="row-fluid">
        <div class="span12 hero well">
          OAI Record Synchronization
        </div>
      </div>
      <div class="row-fluid">
        <div class="span12">
          <ul>
            <li><g:link controller='oai' action='index' id='packages' params="${[verb:'Identify']}">Identify Packages</g:link></li>
            <li><g:link controller='oai' action='index' id='packages' params="${[verb:'ListRecords',metadataPrefix:'gokb']}">Get [full] Packages</g:link></li>
            <li><g:link controller='oai' action='index' id='packages' params="${[verb:'ListRecords',metadataPrefix:'oai_dc']}">Get [oai_dc] Packages</g:link></li>
          </ul>
        </div>
      </div>
   </div>
  </body>
</html>

