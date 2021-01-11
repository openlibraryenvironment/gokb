<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="public" />
<title>GOKb: OAI Interface</title>
</head>
<body>

   <div class="container">
       <div class="row">
         <div class="col-md-12">
            <h1 class="page-header">OAI Record Synchronization</h1>

            <div id="mainarea" class="panel panel-default">
                <div class="panel-heading">
                    <h3 class="panel-title">OAI Options</h3>
                </div>
                <div class="panel-body">
                    <ul>
                        <li><g:link controller='oai' action='index' id='packages' params="${[verb:'Identify']}">Identify Packages</g:link></li>
                        <li><g:link controller='oai' action='index' id='packages' params="${[verb:'ListRecords',metadataPrefix:'gokb']}">Get [full] Packages</g:link></li>
                        <li><g:link controller='oai' action='index' id='packages' params="${[verb:'ListRecords',metadataPrefix:'oai_dc']}">Get [oai_dc] Packages</g:link></li>
                        <li><g:link controller='oai' action='index' id='tipps' params="${[verb:'Identify']}">Identify TIPPs</g:link></li>
                        <li><g:link controller='oai' action='index' id='tipps' params="${[verb:'ListRecords',metadataPrefix:'gokb']}">Get [full] TIPPs</g:link></li>
                        <li><g:link controller='oai' action='index' id='tipps' params="${[verb:'ListRecords',metadataPrefix:'oai_dc']}">Get [oai_dc] TIPPs</g:link></li>
                        <li><g:link controller='oai' action='index' id='titles' params="${[verb:'Identify']}">Identify Titles</g:link></li>
                        <li><g:link controller='oai' action='index' id='titles' params="${[verb:'ListRecords',metadataPrefix:'gokb']}">Get [full] Titles</g:link></li>
                        <li><g:link controller='oai' action='index' id='titles' params="${[verb:'ListRecords',metadataPrefix:'oai_dc']}">Get [oai_dc] Titles</g:link></li>
                        <li><g:link controller='oai' action='index' id='orgs' params="${[verb:'Identify']}">Identify Orgs</g:link></li>
                        <li><g:link controller='oai' action='index' id='orgs' params="${[verb:'ListRecords',metadataPrefix:'gokb']}">Get [full] Orgs</g:link></li>
                        <li><g:link controller='oai' action='index' id='orgs' params="${[verb:'ListRecords',metadataPrefix:'oai_dc']}">Get [oai_dc] Orgs</g:link></li>
                        <li><g:link controller='oai' action='index' id='orgs' params="${[verb:'Identify']}">Identify Platforms</g:link></li>
                        <li><g:link controller='oai' action='index' id='platforms' params="${[verb:'ListRecords',metadataPrefix:'gokb']}">Get [full] Platforms</g:link></li>
                        <li><g:link controller='oai' action='index' id='platforms' params="${[verb:'ListRecords',metadataPrefix:'oai_dc']}">Get [oai_dc] Platforms</g:link></li>
                    </ul>
                </div>
            </div>
          </div>
        </div>
      </div>
</body>
</html>

