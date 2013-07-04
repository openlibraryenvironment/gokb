var GOKb = {
  messageBusy : "Contacting GOKb",
  timeout : 60000, // 1 min timeout.
  handlers: {},
  globals: {},
	menuItems: [],
  ui: {},
  api : {},
  jqVersion : jQuery.fn.jquery.match(/(\d+\.\d+)/ig),
  refine:{},
  versionError : false,
  hijacked : [],
};

/**
 * Replace an existing function with your custom code.
 * The old function is appended to the arguments and therefore
 * can be called from within your new code where necessary.
 * 
 * Use the syntax:
 * function ([params...,] oldFunction) {
 * 	oldFunction.apply(this, arguments);
 * }
 * 
 * Using the apply method ensures that the old method's context
 * is correct.
 */
GOKb.hijackFunction = function(functionName, replacement) {
	
	// Save the old function so we can still use it in our new function.
	GOKb.hijacked[functionName] = eval(functionName);
	
	// New method...
	var repMeth = function() {
		// All arguments passed to this method will be passed to replacement.
		var args = [];
		for (i=0; i<arguments.length; i++){
			args[i] = arguments[i];
		}
		
		// Also pass the old method too.
		args.push(GOKb.hijacked[functionName]);
		
		// Then execute the replacement.
		return (replacement).apply(this, args);
	}
	
	// Generate source to replace old method with the new code.
	eval(functionName + " = " + repMeth.toString());
};

/**
 * Default callback object that displays an error if one was sent through.
 */
GOKb.defaultError = function (data) {
	
	if ("result" in data && "errorType" in data.result && data.result.errorType == "authError") {
		
		// Authentication error, do not show the error but instead show the login box.
		var login = GOKb.createDialog("Login to GOKb", "form_login");
		
		// Add the message if there is one.
		if ("message" in data && data.message && data.message != "") {
			$("fieldset", login.bindings.form).prepend (
			   $("<p />")
			     .attr("class", "error message")
			     .text(data.message)
			);
		}
		
		// Hide the footer as we don't want to have a close button here.
		login.bindings.dialogFooter.hide();
		
		// This is a work around to try and prevent the login box appearing behind a waiting box.
		window.setTimeout(function() {
			GOKb.showDialog(login);
	  }, 700);
		
		// Show the login box.
		return login;
		
	} else {
	
		var error = GOKb.createErrorDialog("Error");
		var msg;
		if  (data && ("message" in data) ) {
			msg = data.message;
			
			// Check for the special case version error.
			if ("result" in data && "errorType" in data.result && data.result.errorType == "versionError") {
				
				// Remove close button.
				error.bindings.closeButton.hide();
				
				GOKb.versionError = true;
			}
		} else {
			msg = "There was an error contacting the GOKb server.";
		}
		if (error) {
			
			error.bindings.dialogContent.html("<p>" + msg + "</p>");
			return GOKb.showDialog(error);
			
		}
	}
	
	// If we haven't returned anything then return then.
	return null;
};

/**
 * Set ajax in progress.
 */
GOKb.setAjaxInProgress = function() {
	
	// If defined on the refine object then use that...
	if (Refine.setAjaxInProgress) {
		Refine.setAjaxInProgress();
	} else {
		
		// Just add the class.
	  $(document.body).attr("ajax_in_progress", "true");
	}
};

/**
 * Clear ajax in progress.
 */
GOKb.clearAjaxInProgress = function() {
	// If defined on the refine object then use that...
	if (Refine.clearAjaxInProgress) {
		Refine.clearAjaxInProgress();
	} else {
		// Just add the class.
	  $(document.body).attr("ajax_in_progress", "false");
	}
};

/**
 * Report an exception from the system.
 */
GOKb.reportException = function(e) {
	
	if (Refine.reportException) {
		Refine.reportException(e);
		
	} else if (window.console) {
    console.log(e);
  }
};

/**
 * Helper method for dialog creation within this module.
 */
GOKb.createDialog = function(title, template) {
  var dialog_obj = $(DOM.loadHTML("gokb", "scripts/dialogs/main.html"));
  var dialog_bindings = DOM.bind(dialog_obj);
  
  // Set title if present.
  if (title) {
  	dialog_bindings.dialogHeader.text(title);
  }
  
  // Set the content of the dialog if a template was supplied.
  if (template) {
		var body_template = $(DOM.loadHTML("gokb", "scripts/dialogs/" + template + ".html")); 
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
GOKb.createErrorDialog = function(title, template) {
	
	if (!GOKb.versionError && !GOKb.globals.eDialogOpen) {
		
		// Temporary set to same as dialog.
		var error = GOKb.createDialog(title, template);
		error.html.addClass("error");
		error.bindings.closeButton.text("OK");
		
		// Add an onShow
		error.onShow = function () {
			
			// Just set the flag.
			GOKb.globals.eDialogOpen = true;
		};
		
		// On close clear the flag.
		error.onClose = function () {
			
			// Just set the flag.
			GOKb.globals.eDialogOpen = false;
		};
		
		return error;
	}
};

/**
 * Helper method for showing dialogs within this module.
 */
GOKb.showDialog = function(dialog) {
	
	// Run uniform on any form elements
  if (dialog.bindings.form) {
  	$("select, input, button, textarea", dialog.bindings.form).uniform();
  }
  
  // Open the dialog and record the level (Z-Axis) at which it is displayed.
  dialog.level = DialogSystem.showDialog(dialog.html);
  
  // Run any custom onShow code specified.
  if ("onShow" in dialog) {
  	
  	// Execute the onShow code
  	dialog.onShow (dialog);
  }
  
  // Add a close method to this dialog.
  dialog.close = function () {
  	DialogSystem.dismissUntil(dialog.level - 1);
  	
  	// Also fire the on close event.
  	if ("onClose" in dialog) {
    	
    	// Execute the onShow code
    	dialog.onClose (dialog);
    }
  }
  
  // Add the close method as the onClick of the close button.
  dialog.bindings.closeButton.click(dialog.close);
  return dialog;
};

/**
 * Helper method to show a "waiting" spinner while completing an AJAX task. 
 */
GOKb.ajaxWaiting = function (ajaxObj, message) {
	var done = false;
  var dismissBusy = null;
  
  // Use the built in UI to show AJAX in progress.
  GOKb.setAjaxInProgress();
  
  // Error callback.	
	var error = function ( jqXHR, status, errorThrown ) {
		
		done = true;
	  if (dismissBusy) {
	    dismissBusy();
	  }
	  
	  // Clear the progress spinner.
	  GOKb.clearAjaxInProgress();
		
		// Display an error message to the user.	    
	  GOKb.defaultError(JSON.parse( jqXHR.responseText ));
	}
	
	// Current success method.
	var currentSuccess = ajaxObj.success;
	var newSucessFunction = function (dataR) {
		
		// Clear the waiting window, here before the success handler to ensure the
		// new window is not closed as well as the waiting modal.
		done = true;
	  if (dismissBusy) {
	    dismissBusy();
	  }
	  GOKb.clearAjaxInProgress();
		
		// Fire our current success object afterwards.
		currentSuccess(dataR);
	};
	
	// Set success to our new function.
	ajaxObj.success = newSucessFunction;
	ajaxObj.error = error;
	
//	/*
//	 * Prior to jQuery 1.6 the ajax methods did not return an object to which we,
//	 * could attach the complete callback to by use of the always method.
//	 * 
//	 * So here we need to check the version and attach our callback to the initial
//	 * ajax object if we are using jQuery 1.5 or lower.
//	 */
//	if (GOKb.jqVersion > 1.5) {
//		
//		// Fire the ajax and attach the always function.
//	  $.ajax(ajaxObj)
//	  	.always(complete)
//	  ;
//	} else {
//		
//		// Set the complete method equal to our callback.
//		ajaxObj.complete = complete;
//		
		// Fire the ajax request.
		$.ajax(ajaxObj);
//	}
  
  // Show waiting message if function has not completed within a second.
  window.setTimeout(function() {
    if (!done) {
      dismissBusy = DialogSystem.showBusy(message);
    }
  }, 2000);
};

/**
 * Helper method for sending data to GOKb service and acting on it.
 * 
 * Callbacks should be contain at least an onDone property and can contain an onError
 * function. These callbacks will be triggered by the successful return of a JSON object
 * from the service. If the return has the property .code set to "error" then the onError
 * callback will be triggered,code otherwise the onDone is run. 
 */
GOKb.doCommand = function(command, params, data, callbacks, ajaxOpts) {
	
	return GOKb.doRefineCommand ("gokb/" + command, params, data, callbacks, ajaxOpts);
};

/**
 * Helper method to execute a command in the Refine backend
 */
GOKb.doRefineCommand = function(command, params, data, callbacks, ajaxOpts) {
	ajaxOpts = ajaxOpts || {};
	
	if (!GOKb.versionError) {
	
		var ajaxObj = $.extend(ajaxOpts, {
	  	cache 		: false,
	    url 			: "command/" + command + "?" + $.param(params), 
	    data 			: data,
	    timeout		: GOKb.timeout,
	    dataType 	: "json",
	    success	: function (dataR) {
	      if (dataR.code == "error") {
	        if ("onError" in callbacks) {
	          try {
	            callbacks.onError(dataR);
	          } catch (e) {
	          	GOKb.reportException(e);
	          }
	        } else {
	        	GOKb.defaultError(dataR);
	        }
	      } else {
	        if ("onDone" in callbacks) {
	          try {
	            callbacks.onDone(dataR);
	          } catch (e) {
	          	GOKb.reportException(e);
	          }
	        }
	      }
	    }
		});
		
		// Show default waiting message
		return GOKb.ajaxWaiting (ajaxObj);
	}
};


/**
 * Return a data-table jQuery object.
 */
GOKb.toTable = function (header, data, addStripe) {
	
	// Default stripe to true.
	addStripe = (typeof addStripe !== 'undefined' ? addStripe : true);
	
	// Create the header object.
	var head = $("<tr />");
	$.each(header, function() {
		
		// Append header element.
		var th = $("<th />").appendTo(head);
		if (this instanceof String || typeof this === 'string') {
			// Use the HTML method to allow us to include special HTML chars like
			// &nbsp;
			th.html(this.toString());
			
		} else {
			// Append each element
			$.each(this, function(){
				th.append(this);
			});
		}
	});	
	head = $("<thead />").append(head);
	
	// Create the tbody
	var body = $("<tbody />");
	var stripe = false;
	$.each(data, function() {
		var row = $("<tr />").appendTo(body);
		if (addStripe) {
			row.addClass( ( stripe ? "even" : "odd" ) );
			stripe = !stripe;
		}
		$.each(this, function() {
			// Append element.
			var td = $("<td />").appendTo(row);
			if (this instanceof String || typeof this === 'string') {
				td.html(this.toString());
				
			} else {
				// Append each element
				$.each(this, function(){
					td.append(this);
				});
			}
		});
	});
		
	// Create the table object and return.
	var table = $('<table class="data-table" cellpadding="0" cellspacing="0" border="0" />')
		.append(head)
		.append(body)
	;
	return table;
};

/**
 * Return an object with parameters of the project set. Including the custom ones.
 */
GOKb.projectDataAsParams = function (project) {
	var params = jQuery.extend({}, theProject.metadata.customMetadata, theProject.metadata);
	
	// Clean up by removing unneeded params.
	delete params.id;
	delete params.customMetadata;
	params.project = project.id;
	
	if (params['gokb-id']) params.projectID = params['gokb-id'];
	
	// Return.
	return params;
};

/**
 * Get ref data from GOKb
 */
GOKb.getRefData = function (type, callbacks, ajaxOpts) {
	GOKb.doCommand ("refdata", {"type" : type }, null, callbacks, ajaxOpts);
};

/**
 * Single value auto-complete.
 */
GOKb.autoComplete = function(elements, data) {
	elements.autocomplete({
		source: data,
	});
};

/**
 * Function to add multi-value auto-complete to the supplied jquery matches.
 */
GOKb.multiAutoComplete = function(elements, data, separator) {

	separator = separator || ",";
	
	// Split function to split at our separator.
	var split = function( val ) {
		return val.split( separator );
	};
	
	// Extract the last term in the list.
	var extractLast = function ( term ) {
		 return split( term ).pop();
	};
	
	elements.autocomplete({
		source: function( request, response ) {
			// delegate back to autocomplete, but extract the last term
			response( $.ui.autocomplete.filter(
			  data, extractLast( request.term ) ) );
		},
		focus: function() {
			// prevent value inserted on focus
			return false;
		},
		select: function( event, ui ) {
			var terms = split( this.value );
			// remove the current input
			terms.pop();
			
			if (ui && ui.item) {
				// add the selected item
				terms.push( ui.item.value );
			}
			
			// add placeholder to get the comma-and-space at the end
			terms.push( "" );
			this.value = terms.join( separator );
			return false;
		}
	});
};

/**
 * Check for access to API.
 */
(GOKb.checkIsUp = function() {
	
	// Just call with empty callbacks. If the api is not up there will be a timeout.
	// If the versions are wrong then the default error callback will be fired and the,
	// version missmatch reported to the user.
	GOKb.doCommand("isUp", {}, {}, {});
	
	if (!GOKb.versionError) {
		// Check again in 3 minutes.
		setTimeout(GOKb.checkIsUp, 180000);
	}
})();
