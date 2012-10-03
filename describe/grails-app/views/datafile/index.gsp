<!doctype html>
<html>
  <head>
    <meta name="layout" content="bootstrap"/>
    <title>Grails Twitter Bootstrap Scaffolding</title>
  </head>

  <body>

    <div class="container">
      <g:form id="upload_new_doc_form" url="[controller:'datafile',action:'new']" method="post" enctype="multipart/form-data">
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
      -- Previous uploads paginated --
    </div>
  </body>
</html>
