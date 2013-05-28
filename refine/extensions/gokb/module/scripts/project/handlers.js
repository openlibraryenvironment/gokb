/**
 * Handlers
 */

GOKb.handlers.suggest = function() {
	// Merge the meta-data and columns together.
	var params = {"project" : theProject.id};
	
	var dataStore = GOKb.forms.getDataStore();
	
	if ("project-properties_provider" in dataStore) {
		params['providerID'] = dataStore['project-properties_provider'];
	}
	
  // Post the columns to the service
  GOKb.doCommand (
    "rules-suggest",
    params,
    null,
    {
    	onDone : function (data) {
    		
    		// Create and show a dialog with the returned list attached.
    		var dialog = GOKb.createDialog("Suggested Rules", "suggest");
    		
    		if ("result" in data && data.result.length > 0) {
    		
	    		// Create data.
	    		var DTData = [];
	  			$.each(data.result, function () {
	  				DTData.push([this.description]);
	  			});
	  			
	  			// Create the Table.
	  			var table = GOKb.toTable (
	   			  ["Rule"],
	   			  DTData
	   			);
	  			
	  			// Add selection checkboxes
	  			table.selectableRows();
	    		
	    		table.appendTo(dialog.bindings.dialogContent);
	  			
	  			// Create an apply rules button
	  			$("<button>Apply Rules</button>").addClass("button").click(function() {
	  				
	  				// Get the indexes of the selected elements.
	  				var selected = table.selectableRows("getSelected");
	  				
	  				var confirmed = confirm("Are you sure you wish to apply these " + selected.length + " operations to your document?");
	  				
	  				if (confirmed) {
	  					
	  					var ops = [];
	  					
	  					// Get the selected rules from the data.
	  					$.each(selected, function () {
	  						var op = JSON.parse (data.result[Number(this)].ruleJson);
	  						ops.push(op);
	  	  			});
	  					
	  					// Apply the rules through the existing api method.
	  					Refine.postCoreProcess(
	  					  "apply-operations",
	  					  {},
	  					  { operations: JSON.stringify(ops) },
	  					  { everythingChanged: true },
	  					  {
	  					  	onDone: function(o) {
	  					  		if (o.code == "pending") {
	  					  			// Something might have already been done and so it's good to update.
	  					  			Refine.update({ everythingChanged: true });
	  					  		}
	  					  	}
	  					  }
	  					);
	  					
	  					// Close the dialog
	  					DialogSystem.dismissUntil(dialog.level - 1);
	  				}
	  			}).appendTo(
	  			  dialog.bindings.dialogFooter
	  			);
    		} else {
    			// Just output nothing found.
    			dialog.bindings.dialogContent.html("<p>No rule suggestions have been found for the current standing of this document.</p>");
    		}
    		
    		// Show the dialog.
    		GOKb.showDialog(dialog);
    	}
  	}
  );
};

// Display a list of operations applied to this project
GOKb.handlers.history = function() {
	GOKb.doRefineCommand("core/get-operations", {project: theProject.id}, null, function(data){
		var dialog = GOKb.createDialog("Applied Operations");
		if ("entries" in data && data.entries.length > 0) {
			
			// Build a JSON data object to display to the user.
			var DTDdata = [];
			$.each(data.entries, function () {
				if ("operation" in this) {
					
					// Include only operations.
					DTDdata.push([this.description]);
				}
			});
			
			// Create a table from the data.
			var table = GOKb.toTable (
			  ["Operation"],
			  DTDdata
			);
			
			// Append the table
			table.appendTo(dialog.bindings.dialogContent);
			
			// Add a button to send the data up to the GOKb server.
			$("<button>Send Operations</button>").addClass("button").click(function() {
				GOKb.doCommand(
				  "saveOperations",
				  {},
				  {
				  	// Entries.
				  	operations : JSON.stringify(data.entries)
				  },
				  {
				  	onDone : function () {
				  		
				  		// Close the dialog
	  					DialogSystem.dismissUntil(dialog.level - 1);
				    }
				  }
				);
			}).appendTo(
			  // Append to the footer.
			  dialog.bindings.dialogFooter
			);
	  } else {
	  	// Just output nothing found.
	  	dialog.bindings.dialogContent.html("<p>No operations have been applied yet.</p>");
	  }
		GOKb.showDialog(dialog);
	});
};

GOKb.handlers.estimateChanges = function () {
	
  // Get the current project ID
  var params = {"project" : theProject.id};
	
  // Post the columns to the service
  GOKb.doCommand (
    "project-estimate-changes",
    params,
    null,
    {
    	onDone : function (data) {
    		
    		if ("result" in data && "status" in data.result) {
    			
    			alert (data);
    			
    			var dialog = GOKb.createDialog("Applied Operations");
    			if ("entries" in data && data.entries.length > 0) {
    				
    				// Build a JSON data object to display to the user.
    				var DTDdata = [];
    				$.each(data.entries, function () {
    					if ("operation" in this) {
    						
    						// Include only operations.
    						DTDdata.push([this.description]);
    					}
    				});
    				
    				// Create a table from the data.
    				var table = GOKb.toTable (
    				  ["Operation"],
    				  DTDdata
    				);
    				
    				// Append the table
    				table.appendTo(dialog.bindings.dialogContent);
    			}
    		}
    		
    		if (onDoneFunc) {
    			onDoneFunc();
    		}
    	}
  	}
  );
}

GOKb.handlers.checkInWithProps = function(hiddenProperties) {
	
	// Get the dynamic form...
	GOKb.doCommand("getProjectProfileProperties", {}, {}, {
		onDone : function (data) {
			if ("result" in data) {
				
				// Try and build the form.
				var dialog = GOKb.createDialog("Project Properties");
				
				var form = GOKb.forms.build("project-properties", data.result, "/command/gokb/project-checkin");
				
				// Bind the form.
				dialog.bindings.dialogContent.append(form);
				$.extend(dialog.bindings, {"form" : form});
				
				// Add the project params to the footer
				hiddenProperties = hiddenProperties || {};
				var params = jQuery.extend({update : true}, GOKb.projectDataAsParams(theProject), hiddenProperties);
				
				// Add the hidden fields.
				GOKb.forms.paramsAsHiddenFields(
				  dialog.bindings.form,
				  dialog.bindings.form.bindings.footer,
				  params
				);
				
				// Change the submit button text to be check-in
				dialog.bindings.form.bindings.submit.attr("value", "Save and Check-in");
				
				// Rename close button to cancel.
				dialog.bindings.closeButton.text("Cancel");
				
				// Show the form.
				return GOKb.showDialog(dialog);
			}
		}
	});
};