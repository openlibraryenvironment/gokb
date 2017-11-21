<html>
  <head>
    <meta name="layout" content="sb-admin" />
    <title>GOKb Direct Ingest Service</title>
  </head>
  <body>
    <h1 class="page-header">Direct File Ingest :: ${ip.name}</h1>
    <div class="col-md-12" >
  	  <div id="mainarea" class="panel panel-default">
        <div class="panel-heading">
					<h3 class="panel-title">Ingest Latest Revision Of File</h3>
				</div>
        <div class="panel-body">
          <g:form controller="ingest" action="profile" id="${ip.id}" method="post" enctype="multipart/form-data" params="${params}">
            <div class="input-group" >
              <span class="input-group-btn">
                <span class="btn btn-default btn-file">
                  Browse <input type="file" id="submissionFile" name="submissionFile"  onchange='$("#upload-file-info").html($(this).val());' />
                </span>
              </span>
              <span class='form-control' id="upload-file-info"><label for="submissionFile" >Select a file...</label></span>
              <span class="input-group-btn">
                <button type="submit" class="btn btn-primary">Upload</button>
              </span>
            </div>
          </g:form>
        </div>
      </div>
      <div id="mainarea" class="panel panel-default">
        <div class="panel-heading">
					<h3 class="panel-title">Ingest History</h3>
				</div>
    		<div class="panel-body">
        </div>
      </div>
    </div>
  </body>
</html>
