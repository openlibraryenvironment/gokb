var GOKbExtension = {
  // Server
  api : "http://localhost:8080/gokb/api/",
  messageBusy : "Contacting GOKb",
  timeout : 10000, // 10 seconds timeout.
  handlers: {}
};

/**
 * Helper method for dialog creation within this module.
 */
GOKbExtension.createDialog = function(title, template) {
  var dialog_obj = $(DOM.loadHTML("gokb", "scripts/dialogs/gokb_dialog.html"));
  var dialog_bindings = DOM.bind(dialog_obj);
  
  // Set title if present
  if (title) {
  	dialog_bindings.dialogHeader.text(title);
  }
  
  // Set the content of the dialog if a template was supplied.
  if (template) {
		var body_template = $(DOM.loadHTML("gokb", "scripts/dialogs/gokb_dialog_" + template + ".html")); 
		dialog_bindings.dialogLayout.prepend(body_template);
		
		// Add body template bindings.
		$.extend(dialog_bindings, DOM.bind(body_template), dialog_bindings);
  }
  
  // If there isn't a dialogContent div defined then add one here, don't forget the binding for future use.
  if ( !dialog_bindings.dialogContent ) {
		var dc = $("<div bind='dialogContent' id='dialog-content' ></div>");
		dialog_bindings.dialogLayout.append(dc);
		
		// Add dialog content bindings.
		$.extend(dialog_bindings, DOM.bind(dc), dialog_bindings);
  }
  
  var dialog = {
    html : dialog_obj,
    bindings : dialog_bindings
  };
  
  return dialog;
};

/**
 * Helper method for error dialog creation within this module.
 */
GOKbExtension.createErrorDialog = function(title, template) {
	
	// Temporary set to same as dialog.
	var error = GOKbExtension.createDialog(title, template);
	error.html.addClass("error");
	error.bindings.closeButton.text("OK");
	return error;
};

/**
 * Helper method for showing dialogs within this module.
 */
GOKbExtension.showDialog = function(dialog) {
  var level = DialogSystem.showDialog(dialog.html);
  dialog.bindings.closeButton.click(function() {
    DialogSystem.dismissUntil(level - 1);
  });
  dialog.level = level;
  return dialog;
};

/**
 * Helper method to show a "waiting" spinner while completing an AJAX task. 
 */
GOKbExtension.ajaxWaiting = function (jqXHR, message) {
	var done = false;
  var dismissBusy = null;
  
  // Use the built in UI to show AJAX in progress.
  Refine.setAjaxInProgress();

  // Add a complete function to remove the waiting box.
  jqXHR.complete(function (jqXHR, status) {
		done = true;
	  if (dismissBusy) {
	    dismissBusy();
	  }
	  Refine.clearAjaxInProgress();
	  
	  if (status == 'error' || status == 'timeout') {
	    // Display an error message to the user.
	    var error = GOKbExtension.createErrorDialog("Communications Error")
	    error.bindings.dialogContent.html("<p>There was an error contacting the GOKb server.</p>");
	    GOKbExtension.showDialog(error);
	  }
	});
  
  // Show waiting message if function has not completed.
  window.setTimeout(function() {
    if (!done) {
      dismissBusy = DialogSystem.showBusy(message);
    }
  }, 100);
  
  return jqXHR;
};

/**
 * Helper method for sending data to GOKb service and acting on it.
 * 
 * Callbacks should be contain at least an onDone property and can contain an onError
 * function. These callbacks will be triggered by the successful return of a JSON object
 * from the service. If the return has the property .code set to "error" then teh onError
 * callback will be triggered,code otherwise the onDone is run. 
 */
GOKbExtension.doCommand = function(command, params, data, callbacks) {
  callbacks = callbacks || {};
  params = params || {};

  // Do the post and check the returned JSON for error.
  var remote = $.ajax({
  	cache : false,
    url : GOKbExtension.api + command + "?" + $.param(params),
    timeout: GOKbExtension.timeout,
    success : function (data) {

      if (data.status == "error") {
        if ("onError" in callbacks) {
          try {
            callbacks.onError(data);
          } catch (e) {
            Refine.reportException(e);
          }
        } else {
          alert(data.message);
        }
      } else {
        if ("onDone" in callbacks) {
          try {
            callbacks.onDone(data);
          } catch (e) {
            Refine.reportException(e);
          }
        }
      }
    },
    dataType : "jsonp"
  });
  
  // Show the GOKb waiting message
  return GOKbExtension.ajaxWaiting (remote, GOKbExtension.messageBusy);
};

/**
 * Helper method to execute a command in the Refine backend
 */
GOKbExtension.doRefineCommand = function(command, params, data, callbacks) {
	
	// Show default waiting message
	return GOKbExtension.ajaxWaiting ($.getJSON(
    "command/" + command + "?" + $.param(params), 
    data,
    callbacks,
    "json"
  ));
};


/**
 * Return a data-table.
 */
GOKbExtension.toTable = function (header, data) {
	
	// Create the header object.
	var head = $("<tr />");
	$.each(header, function() {
		
		// Append header element.
		head.append(
		  $("<th />").text(this)
    );
	});	
	head = $("<thead />").append(head);
	
	// Create the tbody
	var body = $("<tbody />");
	var stripe = false;
	$.each(data, function() {
		var row = $("<tr />").appendTo(body).addClass( ( stripe ? "even" : "odd" ) );
		stripe = !stripe;
		$.each(this, function() {
			// Append element.
			row.append($("<td />").text(this));
		});
	});
		
	// Create the table object and return.
	var table = $('<table class="data-table" cellpadding="0" cellspacing="0" border="0" />')
		.append(head)
		.append(body)
	;
	return table;
};