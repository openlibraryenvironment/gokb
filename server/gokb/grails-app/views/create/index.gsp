<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle,bootstrap-popover"/>
    <title>GOKb</title>
  </head>
  <body>

    <div class="container-fluid">
      <div class="row-fluid">
        <div id="sidebar" class="span2">
          <div class="well sidebar-nav">
            <ul class="nav nav-list">
              <li class="nav-header">Create New..</li>
              <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Package']}">Package</g:link></li>
              <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Org']}">Org</g:link></li>
              <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.Platform']}">Platform</g:link></li>
              <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.TitleInstance']}">Title</g:link></li>
              <li><g:link controller="create" action="index" params="${[tmpl:'org.gokb.cred.RefdataCategory']}">Refdata Category</g:link></li>
            </ul>
          </div><!--/.well -->
        </div><!--/span-->

        <div id="mainarea" class="span10">
          <div id="msg"/>
          <div class="well">
            <g:if test="${displaytemplate != null}">
              <g:if test="${displaytemplate.type=='staticgsp'}">
                <g:render template="${displaytemplate.rendername}" contextPath="../apptemplates" model="${[d:displayobj]}"/>

                <button id="save-btn" class="btn btn-primary">Save Record</button>
              </g:if>
            </g:if>
          </div>
        </div>


      </div>
    </div>

    <script language="JavaScript">

      $('#save-btn').click(function() {
          $('.editable').editable('submit', {   //call submit
              url: "${createLink(controller:'create', action: 'process', params:[cls:params.tmpl])}",
              ajaxOptions: {
                dataType: 'json' //assuming json response
              },  
              success: function(data) {
                // var msg = 'New user created! Now editables work in regular way.';
                // $('#msg').addClass('alert-success').removeClass('alert-error').html(msg).show();
                // $('#save-btn').hide(); 
                window.location = data.uri;
              },
              error: function(data) {
                var msg = '';
                if(data.errors) {                //validation error
                  $.each(data.errors, function(k, v) { msg += k+": "+v+"<br>"; });  
                } else if(data.responseText) {   //ajax error
                  msg = data.responseText; 
                }
                $('#msg').removeClass('alert-success').addClass('alert-error').html(msg).show();
              }
          }); 
      });
    </script>
  </body>
</html>
