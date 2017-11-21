<!doctype html>
<html>
  <head>
    <meta name="layout" content="bootstrap"/>
    <title>Grails Twitter Bootstrap Scaffolding</title>
  </head>

  <body>

    <div class="container">
      <g:form id="upload_new_doc_form" url="[controller:'datafile',action:'upload']" method="post" enctype="multipart/form-data">
        <dl>
          <dt>
            <label>Ingest New Knowledge Base File:</label>
          </dt>
          <dd>
            <input type="file" name="upload_file" />
          </dd>
        </dl>
        <input type="Submit"/>
      </g:form>
    </div>
      
    <div class="container">
      <table  class="table table-striped table-bordered">
        <thead>
          <tr>
            <th>Original Filename</th>
            <th>Uploaded On</th>
            <th>Checksum</th>
          </tr>
        </thead>
        <tbody>
          <g:each in="${filepage}" var="file">
            <tr>
              <td><g:link controller="datafile" action="identification" id="${file.id}">${file.filename}</g:link></td>
              <td>${file.uploadTimestamp}</td>
              <td>${file.md5sum}</td>
            </tr>
          </g:each>
        </tbody>
      </table>
    </div>

  </body>
</html>
