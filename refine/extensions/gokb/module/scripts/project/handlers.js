/**
 * Handlers
 */

// Send of project meta-data and receive back a list of suggested transformations.
GOKb.handlers.suggest = function() {
	// Merge the meta-data and columns together.
	var params = $.extend({}, theProject.metadata,{
  	columns : theProject.columnModel.columns,
  });
	
  // Post the columns to the service
  GOKb.doCommand (
    "describe",
    params,
    null,
    {
    	onDone : function (data) {
    		
    		// Create and show a dialog with the returned list attached.
    		var dialog = GOKb.createDialog("Suggested Operations", "suggest");
    		
    		if ("result" in data) {
    		
	    		// Create data.
	    		var DTData = [];
	  			$.each(data.result, function () {
	  				DTData.push([this.description]);
	  			});
	  			
	  			// Create the Table.
	  			var table = GOKb.toTable (
	   			  ["Operation"],
	   			  DTData
	   			);
	  			
	  			// Add selection checkboxes
	  			table.selectableRows();
	    		
	    		table.appendTo(dialog.bindings.dialogContent);
	  			
	  			// Create an apply rules button
	  			$("<button>Apply Operations</button>").addClass("button").click(function() {
	  				
	  				// Get the indexes of the selected elements.
	  				var selected = table.selectableRows("getSelected");
	  				
	  				var confirmed = confirm("Are you sure you wish to apply these " + selected.length + " operations to your document?");
	  				
	  				if (confirmed) {
	  					
	  					var ops = [];
	  					
	  					// Get the selected rules from the data.
	  					$.each(selected, function () {
	  						ops.push(data.result[Number(this)].operation);
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
    			dialog.bindings.dialogContent.html("<p>No operations have been applied yet.</p>");
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
}

/**
 * Prompt the user to check project properties and then check in the project.
 */

GOKb.handlers.checkInWithProps = function() {
	// Create the form to collect some basic data about this document.
	var dialog = GOKb.createDialog("Suggested Operations", "form_project_properties");
	
	// Change the location to send the data to project check-in.
	dialog.bindings.form.attr("action", "command/gokb/project-checkin");
	var params = jQuery.extend({update : true}, GOKb.projectDataAsParams(theProject));
	
	// Add the project params as hidden fields to the form.
	GOKb.paramsAsHiddenFields(dialog.bindings.form, params);
	
	// Change the submit button text to be check-in
	dialog.bindings.submit.attr("value", "Save and Check-in");
	
	// List of orgs :To be retrieved from GOKb.
//	var orgs = {org1 : "oranisation1",org2 : "oranisation2",org3 : "oranisation3"};
//	
//	// Add the organisations.
//	var orgList = $('#organisation', dialog.bindings.form);
//	$.each(orgs, function (value, display) {
//		orgList.append(
//		  $('<option />', {"value" : value})
//		  .text(display)
//		);
//	});
	
	// Rename close button to cancel.
	dialog.bindings.closeButton.text("Cancel");
	
	// Show the form.
	GOKb.showDialog(dialog);
}


/**
 * Check in the project for the first time and add to the repository.
 */
GOKb.handlers.addToRepo = function() {
	
	var params = jQuery.extend({update : true}, GOKb.projectDataAsParams(theProject));
	
	GOKb.doRefineCommand("gokb/project-checkin", params, null, 
	{
  	onDone : function (data) {
  		
  		// Redirect to the home page.
  		window.location = "/";
    }
  });
}