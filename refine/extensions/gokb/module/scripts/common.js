var GOKb = {
  messageBusy : "Contacting GOKb",
  workspace : {},
  workspaces : [],
  current_ws : 0,
  timeout : 1800000, // 3 minute.
  handlers: {},
  globals: {},
  menuItems: [],
  ui: {},
  api : {},
  jqVersion : jQuery.fn.jquery.match(/(\d+\.\d+)/ig),
  refine:{},
  versionError : false,
  hijacked : [],
  core_update : false
};

/**
 * Replace an existing function with your custom code.
 * The old function is appended to the arguments and therefore
 * can be called from within your new code where necessary.
 * 
 * Use the syntax:
 * function ([params...,] oldFunction) {
 *   oldFunction.apply(this, arguments);
 * }
 * 
 * Using the apply method ensures that the old method's context
 * is correct.
 */
GOKb.hijackFunction = function(functionName, replacement) {
  
  // Regex for code stripping.
  var STRIP_COMMENTS = /((\/\/.*$)|(\/\*[\s\S]*?\*\/))/mg;
  var ARGUMENT_NAMES = /([^\s,]+)/g;
  
  // Method that we use to extract the list of parameters expected by the original method.
  var getArgs = function (func) {
    var fnStr = func.toString().replace(STRIP_COMMENTS, '');
    var result = fnStr.slice(fnStr.indexOf('(')+1, fnStr.indexOf(')')).match(ARGUMENT_NAMES);
    if(result === null)
       result = [];
    return result;
  };
  
  // Get the function.
  var func = eval(functionName);
  
  // Get the expected parameters list.
  var orig_args = getArgs (func);
  
  // Save the old function so we can still use it in our new function.
  GOKb.hijacked[functionName] = func;
  
  // New method...
  var repMeth = function() {
    // All arguments passed to this method will be passed to replacement.
    var args = [];
    for (var i=0; i<arguments.length; i++){
      args[i] = arguments[i];
    }
    
    // Ensure we pad out the arg list with nulls to match the original list.
    for (var i=(args.length); i<orig_args.length; i++){
      args[i] = null;
    }
    
    // Also pass the old method too.
    args.push(GOKb.hijacked[functionName]);
    
    // Then execute the replacement.
    return (replacement).apply(this, args);
  };
  
  // Generate source to replace old method with the new code.
  eval(functionName + " = " + repMeth.toString());
};

/**
 * Default callback object that displays an error if one was sent through.
 */
GOKb.defaultError = function (data) {
  
  if (!GOKb.versionError && "result" in data && "errorType" in data.result && data.result.errorType == "authError") {
    
    // Authentication error, do not show the error but instead show the login box.
    var login = GOKb.createDialog("Login to " + GOKb.workspace.name, "form_login");
    
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
    $("select,input,button,textarea",	dialog.bindings.form)
      .not(".none-uniform")
      .uniform();
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
  };
  
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
  };
  
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
  
  // As of jQuery 1.9 success, error and complete have been removed.
  // We should now call the fail, done and always methods on the returned object instead.
  delete ajaxObj.success;
  delete ajaxObj.error;
  
  // Set the callbacks.
  var deferred = $.ajax(ajaxObj)
    .done ( newSucessFunction )
    .fail ( error )
  ;
  
  // Show waiting message if function has not completed within a second.
  window.setTimeout(function() {
    if (!done) {
      dismissBusy = DialogSystem.showBusy(message);
    }
  }, 2000);
  
  // Return the deferred.
  return deferred;
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
 * Helper for GOKb processes that need to refresh the data after complete. 
 */
GOKb.postProcess = function(command, params, body, updateOptions, callbacks) {
  Refine.postProcess("gokb", command, params, body, updateOptions, callbacks);
};

/**
 * Helper method to execute a command in the Refine backend
 */
GOKb.doRefineCommand = function(command, params, data, callbacks, ajaxOpts) {
  ajaxOpts = ajaxOpts || {};
  
  if (!GOKb.versionError) {
  
    var ajaxObj = $.extend(ajaxOpts, {
      cache     : false,
      url       : "command/" + command + "?" + $.param(params), 
      data       : data,
      timeout    : GOKb.timeout,
      dataType   : "json",
      success  : function (dataR) {
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
        if (this instanceof jQuery || this instanceof Array) {
          $.each(this, function() {
            td.append(this);
          });
        }
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
 * Get Lookup
 */
GOKb.getComponentLookup = function (term, extraParams, callbacks, ajaxOpts) {
  
  // Extra params.
  var params = $.extend({}, extraParams, {"term" : term });
  
  // Fire the method.
  GOKb.doCommand ("lookup", params, null, callbacks, ajaxOpts);
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
GOKb.multiAutoComplete = function (elements, data, separator) {

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
 * Lookup needs to be bound to click and focus of something.
 * So here we'll create a hidden container and then programatically show and hide the popup.
 */
GOKb.lookupCont = null;
GOKb.lookup = null;
GOKb.lookupEventBound = false;
GOKb.getLookup = function (el, location, callback, quickCreate, title) {
  
  // Try and get the container.
  if (GOKb.lookupCont == null) {
    
    // Create the new container and add to the body.
    GOKb.lookupCont = $('<div />');
    GOKb.lookupCont.appendTo($("body"));
    GOKb.lookupCont.hide();
  }
    
  // Let's now set a add a lookup. With blank set of data.
  GOKb.lookupCont.lookup({
  });
    
  // The customised lookup object.
  GOKb.lookup = {
    _lookup : GOKb.lookupCont.data("ui-lookup") ?  
      GOKb.lookupCont.data("ui-lookup") :
      GOKb.lookupCont.data("lookup"),
    _original_renderer : null,
    _quickCreate : null,
    _el : el,
    open : function (renderer) {
      this._lookup._open();
      
      if (quickCreate != false) {
      	
      	if (this._quickCreate == null) {
      		
      		var _self = this;
      		
      		// Reference for the autocomplete.
      		var ac = $(_self._lookup._autocomplete[0]);
      		
      		// Create the button.
      		_self._quickCreate = $("<button />")
		  			.prop('disabled', true)
      		  .text ("Create New")
      		  .click(function(){
      	  		var val = ac.val();
      	  		
      	  		if (val && val != "") {
	      	  		// On click we need to confirm the creation.
	      	  		var r=confirm("Are you sure you wish to create \"" + val + "\"");
	      	  		if (r==true) {
	      	  		  // Try and create the new item.
	      	  		  GOKb.doCommand(
	      	  		    "quickCreate",
	      	  		    {},
	      	  		    {
	      	  		      "qq_type" : quickCreate,
	      	  		      "name": val
	      	  		    },
	      	  		    {
	      	  		      "onDone" : function (data) {
	      	  		        // Run the callback and then close the dialog.
	      	  		        callback({"label": data.result, "value": data.result}, _self._el);
	      	  		        
	      	  		        // Close the lookup.
	      	  		        GOKb.lookup.close();
	      	  		      }
	      	  		    }
	      	  		  );
	      	  		}

	      	  		// Just do nothing.
      	  		}
	      	  });
      		
      		ac.on('input', function() {
    		  	if ($(this).val() && $(this).val() != "") {
    		  		// Enable the create button.
    		  		_self._quickCreate.prop('disabled', false);
    		  	} else {
    		  		// Disable the button.
    		  		_self._quickCreate.prop('disabled', true);
    		  	}
    		  });
      	
	      	// Append a button for the quick creation.
      		ac.after (
	      	  _self._quickCreate
	      	);
      	}
      } else {
      	// False. Remove the button.
        if (this._quickCreate) {
        	this._quickCreate.remove();
        	this._quickCreate = null;
        }
      }
      
      // Bind a search event listener here to clear the list.
      if (!GOKb.lookupEventBound ) {
        this._lookup._autocomplete.on( "autocompletesearch", function( event, ui ) {
          $(".ui-lookup-results ul").empty();
        });
        
        // Set the flag.
        GOKb.lookupEventBound = true;
      }
      
      // Get the data for the autocomplete box.
      var auto_complete_data = this._lookup._autocomplete.data('ui-autocomplete');
      
      if (!auto_complete_data) auto_complete_data = this._lookup._autocomplete.data('autocomplete');
      
      if (this._original_renderer == null) {
        
        // Save the original renderer the first time we call this method.
        this._original_renderer = auto_complete_data._renderItem;
      }
      
      // If the renderer is blank then just set to the original.
      if (renderer == undefined || !renderer) {
        
        // Restore original.
        auto_complete_data._renderItem = this._original_renderer;
        
      } else {
        
        // Set to our custom handler.
        auto_complete_data._renderItem = renderer;
      }
    },
    close : function () {
      // Destroy the lookup and set to null for recreation.
      GOKb.lookup._lookup.destroy();
      GOKb.lookup._lookup = null;
    },
    setCallback : function (cb) {
      _self = this;
      _self._lookup.options.select = function (item) {
        cb(item, _self._el);
      };
    },
    setSource : function (source) {
      if ("_autocomplete" in this._lookup) {
        
        // Change the source for the auto complete.
        this._lookup._autocomplete.autocomplete ('option', 'source', source);
        
      } else {
        
        // Set the initial source.
        this._lookup.options.source = source;
      }
    },
    val : function () {
      return this._lookup.options.value;
    }
  };
  
  // Listen for the close event.
  GOKb.lookup._lookup._dialog.on("dialogclose", function( event, ui ) {
   
    // Close the whole widget when the dialog is closed.
    GOKb.lookup.close();
  });
  
  // Set the Z-Index here.
  GOKb.lookup._lookup._dialog.dialog('option', { stack: false, zIndex:100000 });
  
  // If there is a title here then we should change the title.
  if (title) {
    GOKb.lookup._lookup._dialog.dialog('option', 'title', title );
  }

  // We should now have a lookup box.
  GOKb.lookup.setSource(location);
  GOKb.lookup.setCallback(callback);
  
  return GOKb.lookup;
};

/**
 * Add an escape function to the regexp.
 */ 
RegExp.escape = function(text) {
  return text.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, "\\$&");
};

/**
 * Load the available workspaces from refine.
 */
GOKb.populateWorkspaces = function () {
  
  // Get the workspaces.
  GOKb.doCommand(
    "get-workspaces",
    {},
    {},
    {
      onDone : function (data) {
        if ("workspaces" in data && "current" in data) {
          
          // Add the current workspace pointer.
          GOKb.current_ws = data.current;
          GOKb.workspaces = data.workspaces;
          GOKb.workspace=data.workspaces[data.current];
        }
      }
    }
  );
};

/**
 * This will poll for any changes from GOKb.
 */
GOKb.getCoreData = function () {
  
  // Set update in progress.
  GOKb.core_update = true;
  
  // Get the workspaces.
  GOKb.doCommand(
    "get-coredata",
    {},
    {},
    { onDone : GOKb.updateWithCoreData }
  ).done(function(){
    GOKb.core_update = false;
  });
};

/**
 * Act on data been sent from GOKb module backend.
 */
GOKb.updateWithCoreData = function (data) {
  
  GOKb.core = data;
  
  // Add any system notifications.
  $.each(data['notification-stacks']['system'], function(){
    GOKb.notify.show(this, "system");
  });
};

