/**
 * Messages namespace for validation panel.
 */
ValidationPanel.messages = {
	quickRes : {}
};

/**
 * Get the actions menu for the message supplied.
 */
ValidationPanel.messages.getActions = function (message, elem) {
	
	// Create the menu to show.
	var menu = [];
	
	// Check for quick resolve links
	var qr = ValidationPanel.messages.quickRes.options(message);
	if (qr.length > 0) {
		menu.push({
	  	"id" : "message-quick-reolution",
	  	label: "Quick Resolution",
      submenu: qr,
	  });
	}
	
	// Do the actual menu creation.
	MenuSystem.createAndShowStandardMenu(
	  menu,
	  elem,
	  { width: "120px", horizontal: true }
	);
};

/**
 * Get quick resolution options.
 */
ValidationPanel.messages.quickRes.options = function (message) {
	
	var opts = [];
	
	switch (message.type) {
		case 'missing_column' :
			
			// Suggest adding column or renaming column.
			opts = opts.concat (
			  [
			   	{
			   		id 		: message.type + "rename",
			   		label : "Rename a column",
			   		click : function() {
			   			ValidationPanel.messages.quickRes.renameColumn(message);
			   		}
			    },
			  ]
			);
			
			break;
			
		case 'data_invalid' :
		  // No quick suggestions yet.
			opts = opts.concat (
			  [
			   	{
			   		id 		: message.type + "facet",
			   		label : "Create facet",
			   		click : function() {
			   			ValidationPanel.messages.quickRes.addFacet(message)
			   		}
			    },
			  ]
			);
			break;
	}
	
	return opts;
}

/**
 * Rename a column to the required name.
 */
ValidationPanel.messages.quickRes.renameColumn = function(message) {
	// Create a pop-up to allow the user to select a column to rename.
	var dialog = GOKb.createDialog('Quick Resolution - Rename to "' + message.col + '"');
	
	var form = GOKb.forms.build(
	  "qr-resolve", 
	  [
		  {
	    	type : "fieldset",
	    	children : [
		  		{
		  		  type 			: 'legend',
		  		  text 			: 'Choose column to rename'
		  		},
		  		{
		  		  label			: 'Rename column',
		  		  type			: 'select',
		  		  name			: 'column',
		  		  children	: GOKb.forms.getColumnsAsListOptions(),
		  		},
	  	  ],
		  },
		],
		function () {
	  	
	  	// Read the old name from the form.
	  	var name = $('#column', dialog.bindings.form).val();
	  	
	  	if (name) {
		  	// Do the rename.
				Refine.postCoreProcess(
					"rename-column", 
					{
					  oldColumnName: name,
					  newColumnName: message.col
					},
					null,
					{ modelsChanged: true }
				);
	  	}
	  
	  	// Close the dialog.
	  	dialog.close();
	  	
	  	// Don't allow the submit afterwards.
		  return false;
	  }
	);
	
	// Bind the form.
	dialog.bindings.dialogContent.append(form);
	$.extend(dialog.bindings, {"form" : form});
	
	// Change the submit button text to be rename
	dialog.bindings.form.bindings.submit.attr("value", "Rename");
	
	// Rename close button to cancel.
	dialog.bindings.closeButton.text("Cancel");
	
	// Show the form.
	return GOKb.showDialog(dialog);
};

/**
 * Rename a column to the required name.
 */
ValidationPanel.messages.quickRes.addFacet = function(message) {

	// Add the facet.
  ui.browsingEngine.addFacet(
    'list',
    {
      "name" 				: message.facetName,
      "columnName" 	: message.col, 
      "expression" 	: message.facetValue,
    	"omitBlank" : true
    }
  );
};