<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="main" />
<r:require modules="gokbstyle,bootstrap-popover" />
<title>GOKb File Upload</title>
</head>
<body>

  <div class="container-fluid">
    <div class="row-fluid">

      <div id="mainarea" class="span12">
        <div class="well">
          <h1>GOKb file upload</h1>
          <p>
            Use this form to upload any file to GOKb. After upload you will be able to link the file to components in the database. If GOKb can
            interpret the contents of your file (For example, if the file is regognised as an ONIX license file) then the system will
            attempt to create the corresponding system objects and link them to the uploaded file.
          </p>
          <p>
            Please note - The system only maintains a unique copy of each file. repeat uploads will not be processed as new files.
          </p>
          <g:form action="processSubmission" method="post" enctype="multipart/form-data" params="${params}">
            <dl>
              <div class="control-group">
                <dt>File to submit</dt>
                <dd><input type="file" id="submissionFile" name="submissionFile"/></dd>
              </div>

              <div class="control-group">
                <dt></dt>
                <dd><button type="submit" class="btn btn-primary">Upload</button></dd>
              </div>
            </dl>
          </g:form>
        </div>
      </div>

  </div>
</body>
</html>
