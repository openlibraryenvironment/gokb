<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: Create New ${displayobj?.getNiceName() ?: 'Component'}</title>
</head>
<body>
  <h1 class="page-header">
          Create New ${displayobj?.getNiceName() ?: 'Component'}
  </h1>
  <div id="mainarea" class="panel panel-default">
    <div class="panel-body">
      <g:if test="${displaytemplate != null}">
        <g:if test="${displaytemplate.type=='staticgsp'}">
          <g:if test="${displaytemplate.noCreate}">
            <div id="content">
              <div style="padding:20px">
                <span class="alert alert-danger" style="font-weight:bold;">Components of this type cannot be created in a standalone context.</span>
              </div>
            </div>
          </g:if>
          <g:else>
            <g:set var="preMsg" value="${flash.error ? 'There were errors when attempting to create the new component.' : ''}" />
            <g:render template="/apptemplates/messages"
                      model="${ ["preMessage" : preMsg ]}" />
            <g:render template="/apptemplates/${displaytemplate.rendername}"
                      model="${[d:displayobj, rd:refdata_properties, dtype:displayobjclassname_short]}" />
            <button id="save-btn" class="btn btn-default pull-right btn-sm">Create and Edit &gt;&gt;</button>
          </g:else>
        </g:if>
      </g:if>
    </div>
  </div>

  <asset:script type="text/javascript">

      $('#save-btn').click(function() {
      
        // Build a list of params.
        var params = {};
        $('span.editable').not('.editable-empty').each (function(){
            var editable = $(this);

            // Add the parameter to the params object.
            var eVal = editable.editable('getValue', true)

            params[editable.attr("data-name")] = eVal ? eVal : editable.text();
        });

        $('a.editable').not('.editable-empty').each (function(){
            var editable = $(this);

            params[editable.attr("data-name")] = editable.attr('target-id');
        })

      	// Now we have the params let's submit them to the controller.
      	var jqxhr = $.post( "${createLink(controller:'create', action: 'process', params:[cls:params.tmpl])}", params )
					.done(function(data) {
             // var msg = 'New user created! Now editables work in regular way.';
             // $('#msg').addClass('alert-success').removeClass('alert-error').html(msg).show();
             // $('#save-btn').hide(); 
             window.location = data.uri;
					})
					.fail(function(data) {
            var msg = '';
            if(data.errors) {                //validation error
              $.each(data.errors, function(k, v) { msg += k+": "+v+"<br>"; });  
            } else if(data.responseText) {   //ajax error
              msg = data.responseText; 
            }
            $('#msg').removeClass('alert-success').addClass('alert-error').html(msg).show();
          })
				;
      });

      var hash = window.location.hash;
      hash && $('ul.nav a[href="' + hash + '"]').tab('show');

      $('.nav-tabs > li > a').not('.disabled').click(function (e) {
        $(this).tab('show');
        var scrollmem = $('body').scrollTop();
        console.log("scrollTop");
        window.location.hash = this.hash;
        $('html,body').scrollTop(scrollmem);
      });

    </asset:script>
</body>
</html>
