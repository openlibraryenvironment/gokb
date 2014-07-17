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
	
	switch (message.sub_type) {
		case 'missing_column' :
			
			// Suggest adding column or renaming column.
			opts = opts.concat (
			  [
			    {
			   		id 		: message.sub_type + "add",
			   		label : "Append a blank column",
			   		click : function() {
			   			ValidationPanel.messages.quickRes.addBlankColumn(message);
			   		}
			    },
			   	{
			   		id 		: message.sub_type + "rename",
			   		label : "Rename a column",
			   		click : function() {
			   			ValidationPanel.messages.quickRes.renameColumn(message);
			   		}
			    },
			  ]
			);
			
			break;
			
		case 'incorrect_column_name' :
			
			// Suggest adding column or renaming column.
			opts = opts.concat (
			  [
			   	{
			   		id 		: message.sub_type + "rename",
			   		label : "Rename this column to...",
			   		click : function() {
			   			ValidationPanel.messages.quickRes.renameColumnTo(message);
			   		}
			    },
			    {
			   		id 		: message.sub_type + "remove",
			   		label : "Remove this column",
			   		click : function() {
			   			ValidationPanel.messages.quickRes.removeColumn(message);
			   		}
			    },
			  ]
			);
			
			break;
		
		case 'date_invalid' :
			opts = opts.concat (
			  [
			    {
			  	  id 		: message.sub_type + "convert",
			  	  label : "Attempt automatic conversion",
			  	  click : function() {
			  		  ValidationPanel.messages.quickRes.transform(message);
			  	  }
			    },
			  ]
			);
		case 'data_invalid' :
			opts = opts.concat (
			  [
			   	{
			   		id 		: message.sub_type + "facet",
			   		label : "Create facet",
			   		click : function() {
			   			ValidationPanel.messages.quickRes.addFacet(message);
			   		}
			    },
			  ]
			);
			break;
	}
	
	return opts;
};

/**
 * Add blank column
 */
ValidationPanel.messages.quickRes.addBlankColumn = function (message) {
	
	// Create a blank column.
	GOKb.handlers.createBlankColumn ( message.col );
};

/**
 * Remove the invalid column.
 */
ValidationPanel.messages.quickRes.removeColumn = function (message) {
	
	// Remove the column the message has been raised for.
	Refine.postCoreProcess(
	  "remove-column", 
	  {
	  	columnName: message.col
	  },
	  null,
	  { modelsChanged: true }
	);
};

/**
 * Transform the data in this column using the data supplied.
 */
ValidationPanel.messages.quickRes.transform = function (message) {
	Refine.postCoreProcess(
	  "text-transform",
	  {
	  	columnName: GOKb.caseInsensitiveColumnName(message.col),
	  	expression: message.transformation,
	  	onError: 'keep-original',
	  	repeat: false,
	  	repeatCount: 0
	  },
	  null,
	  { cellsChanged: true }
	);
};


/**
 * Rename this column to a free text value.
 */
ValidationPanel.messages.quickRes.renameColumnTo = function (message) {
	
	// Rename the column that the message has been raised against to a free text value.
	var newColumnName = window.prompt("Enter new column name", message.col);
  if (newColumnName !== null) {
    Refine.postCoreProcess(
      "rename-column", 
      {
        oldColumnName: message.col,
        newColumnName: newColumnName
      },
      null,
      { modelsChanged: true }
    );
  }
};

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
      "columnName" 	: GOKb.caseInsensitiveColumnName(message.col), 
      "expression" 	: message.facetValue,
    	"omitBlank" : true
    }
  );
};