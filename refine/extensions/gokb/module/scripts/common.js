var GOKb = {
	version : 0.3,
  messageBusy : "Contacting GOKb",
  timeout : 60000, // 1 min timeout.
  handlers: {},
	menuItems: [],
  ui: {},
  jqVersion : jQuery.fn.jquery.match(/(\d+\.\d+)/ig),
  api:{
  	url : "http://gokb.k-int.com/gokb/api/"
  },
  refine:{},
};

/**
 * Default callback object that displays an error if one was sent through.
 */

GOKb.defaultError = function (data) {
	var error = GOKb.createErrorDialog("Error");
	var msg;
	if  (data && "message" in data ) {
		msg = data.message;
	} else {
		msg = "There was an error contacting the GOKb server.";
	}
	error.bindings.dialogContent.html("<p>" + msg + "</p>");
  return GOKb.showDialog(error);
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
  
  // Set title if present
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
	
	// Temporary set to same as dialog.
	var error = GOKb.createDialog(title, template);
	error.html.addClass("error");
	error.bindings.closeButton.text("OK");
	return error;
};

/**
 * Helper method for showing dialogs within this module.
 */
GOKb.showDialog = function(dialog) {
	
	// Run uniform on any form elements
  if (dialog.bindings.form) {
  	$("select, input, button, textarea", dialog.bindings.form).uniform();
  }
  
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
GOKb.ajaxWaiting = function (ajaxObj, message) {
	var done = false;
  var dismissBusy = null;
  
  ajaxObj.beforeSend = function (request)
  {
    request.setRequestHeader("GOKb-version", GOKb.version);
  };
  
  // Use the built in UI to show AJAX in progress.
  GOKb.setAjaxInProgress();
  
  // Complete callback.
  var complete = function (jqXHR, status) {
		done = true;
	  if (dismissBusy) {
	    dismissBusy();
	  }
	  GOKb.clearAjaxInProgress();
	  
	  if (status == 'error' || status == 'timeout') {
	    // Display an error message to the user.	    
	    GOKb.defaultError();
	  }
	};
	
	/*
	 * Prior to jQuery 1.6 the ajax methods did not return an object to which we,
	 * could attach the complete callback to by use of the always method.
	 * 
	 * So here we need to check the version and attach our callback to the initial
	 * ajax object if we are using jQuery 1.5 or lower.
	 */
  
	if (GOKb.jqVersion > 1.5) {
		
		// Fire the ajax and attach the always function.
	  $.ajax(ajaxObj)
	  	.always(complete)
	  ;
	} else {
		
		// Set the complete method equal to our callback.
		ajaxObj.complete = complete;
		
		// fire the ajax request.
		$.ajax(ajaxObj);
	}
  
  // Show waiting message if function has not completed within half a second.
  window.setTimeout(function() {
    if (!done) {
      dismissBusy = DialogSystem.showBusy(message);
    }
  }, 500);
};

/**
 * Helper method for sending data to GOKb service and acting on it.
 * 
 * Callbacks should be contain at least an onDone property and can contain an onError
 * function. These callbacks will be triggered by the successful return of a JSON object
 * from the service. If the return has the property .code set to "error" then the onError
 * callback will be triggered,code otherwise the onDone is run. 
 */
GOKb.doCommand = function(command, params, data, callbacks) {
  callbacks = callbacks || {};
  params = params || {};

  var ajaxObj = {
  	cache : false,
    url : GOKb.api.url + command + "?" + $.param(params),
    timeout: GOKb.timeout,
    data : data,
    success : function (dataR) {

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
    },
    dataType : "jsonp"
  };
  
  // Show the GOKb waiting message
  return GOKb.ajaxWaiting (ajaxObj, GOKb.messageBusy);
};

/**
 * Helper method to execute a command in the Refine backend
 */
GOKb.doRefineCommand = function(command, params, data, callbacks) {
	
	var ajaxObj = {
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
	};
	
	// Show default waiting message
	return GOKb.ajaxWaiting (ajaxObj);
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
	
	// Return.
	return params;
};

/**
 * Add the parameters object as a series of hidden fields to the form.
 */
GOKb.paramsAsHiddenFields = function (form, params) {
	for(var key in params) {
		form.append(
		  $("<input />")
		    .attr('type', 'hidden')
		    .attr('name', key)
		    .attr('value', params[key])
		);
	}
}

/**
 * Check versions match every minute.
 */
//(GOKb.checkVersion = function() {
//  // do some stuff
//  setTimeout(GOKb.checkVersion, 60000);
//})();