var GoKBExtension = { handlers: {} };

/**
 * Helper method for dialog creation within this module.
 */
GoKBExtension.createDialog = function(title, template) {
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
}

/**
 * Helper method for showing dialogs within this module.
 */
GoKBExtension.showDialog = function(dialog) {
  var level = DialogSystem.showDialog(dialog.html);
  dialog.bindings.closeButton.click(function() {
    DialogSystem.dismissUntil(level - 1);
  });
  dialog.level = level;
  return dialog;
}

/**
 * Handlers
 */
GoKBExtension.handlers.suggest = function() {
  var dialog = GoKBExtension.createDialog("Suggested Transformations", "suggest");
  
  // Append some content.
  dialog.bindings.dialogContent.html("<ul><li>One suggestion</li></ul>");
  GoKBExtension.showDialog(dialog);
};