<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: File Upload</title>
</head>
<body>

	<h1 class="page-header">File upload</h1>
	<div id="mainarea" class="panel panel-default">
		<div class="panel-body">
			<p>
				Use this form to upload any file to GOKb. After upload you will be able to link the file to components in the database. If GOKb can
        interpret the contents of your file (For example, if the file is recognised as an ONIX license file) then the system will
        attempt to create the corresponding system objects and link them to the uploaded file.
      </p>
      <p>
        Please note - The system only maintains a unique copy of each file. Repeat uploads will not be processed as new files.
      </p>
      <g:form action="processSubmission" method="post" enctype="multipart/form-data" params="${params}">
         
         <div class="input-group" >
         	 <span class="input-group-btn">
		         <span class="btn btn-default btn-file">
						    Browse <input type="file" id="submissionFile" name="submissionFile" onchange='$("#upload-file-info").html($(this).val());' />
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
</body>
</html>
