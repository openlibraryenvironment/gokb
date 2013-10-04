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

        <div id="mainarea" class="span12">
          <div id="msg"></div>
          <div class="well">
            <g:if test="${displaytemplate != null}">
              <g:if test="${displaytemplate.type=='staticgsp'}">
		<g:render template="${displaytemplate.rendername}" contextPath="../apptemplates" model="${[d:displayobj, rd:refdata_properties, dtype:displayobjclassname_short]}"/>
                <button id="save-btn" class="btn btn-primary">Save Record</button>
              </g:if>
            </g:if>
          </div>
        </div>
      </div>
    </div>

    <script type="text/javascript">

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
