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
					<g:render contextPath="../apptemplates" template="messages"
						model="${ ["preMessage" : "There were errors when attempting to create the new component." ]}" />
					<g:render template="${displaytemplate.rendername}"
						contextPath="../apptemplates"
						model="${[d:displayobj, rd:refdata_properties, dtype:displayobjclassname_short]}" />
					<button id="save-btn" class="btn btn-default pull-right btn-sm">Create and Edit &gt;&gt;</button>
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
      		params[editable.attr("data-name")] = editable.text();
      	});
      	
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
    </asset:script>
</body>
</html>
