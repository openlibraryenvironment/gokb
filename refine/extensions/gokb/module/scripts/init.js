var GOKbExtension = {
  // Server
  url : "http://localhost:8080/gokb/",
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
 * Helper method for sending data to GOKb service and acting on it.
 */
GOKbExtension.doCommand = function(command, params, callbacks) {
  callbacks = callbacks || {};

  params = params || {};

  var done = false;
  var dismissBusy = null;
  
  // Use the built in UI to show ajax in progress.
  Refine.setAjaxInProgress();

  // Do the post and check the returned JSON for error.
  $.post(
    "command/" + moduleName + "/" + command + "?" + $.param(params),
    body,
    function (o) {
    	done = true;
      if (dismissBusy) {
        dismissBusy();
      }

      Refine.clearAjaxInProgress();

      if (o.code == "error") {
        if ("onError" in callbacks) {
          try {
            callbacks.onError(o);
          } catch (e) {
            Refine.reportException(e);
          }
        } else {
          alert(o.message);
        }
      } else {
        if ("onDone" in callbacks) {
          try {
            callbacks.onDone(o);
          } catch (e) {
            Refine.reportException(e);
          }
        }
      }
    },
    "json"
  );

  // Check to see if AJAX post has completed yet.
  window.setTimeout(function() {
    if (!done) {
      dismissBusy = DialogSystem.showBusy();
    }
  }, 500);
};