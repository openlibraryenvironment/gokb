/**
 * Handlers
 */

// Send of project meta-data and receive back a list of suggested transformations.
GOKbExtension.handlers.suggest = function() {
	// Merge the meta-data and columns together.
	var params = $.extend({}, theProject.metadata,{
  	columns : theProject.columnModel.columns,
  });
	
  // Post the columns to the service
  GOKbExtension.doCommand (
    "describe",
    params,
    {
    	onDone : function (data) {
    		
    		// Create data.
    		var DTDdata = [];
  			$.each(data.result, function () {
  				DTDdata.push([this.name]);
  			});
  			
  			// Create the Table.
  			var table = GOKbExtension.toTable (
   			  ["Rule"],
   			  DTDdata
   			);
    		
    		// Create and show a dialog with the returned list attached.
    		var dialog = GOKbExtension.createDialog("Suggested Transformations", "suggest");
    		table.appendTo(dialog.bindings.dialogContent);
    		GOKbExtension.showDialog(dialog);
    	}
  	}
  );
};

// Display a list of operations applied to this project
GOKbExtension.handlers.getOperations = function() {
	GOKbExtension.doRefineCommand("core/get-operations", {project: theProject.id}, function(data){
		if ("entries" in data) {
			var ops = GOKbExtension.createDialog("Workflow");
			
			// Build a JSON data object to display to the user.
			var DTDdata = [];
			$.each(data.entries, function () {
				if ("operation" in this) {
					
					// Include only operations.
					DTDdata.push([this.description, JSON.stringify(this.operation)]);
				}
			});
			
			// Create a table from the data.
			var table = GOKbExtension.toTable (
			  ["Description", "Operation"],
			  DTDdata
			);
			
			table.appendTo(ops.bindings.dialogContent);
	  } else {
	  	ops.bindings.dialogContent.html("<p>No entries were found</p>");
	  }
		GOKbExtension.showDialog(ops);
	});
}