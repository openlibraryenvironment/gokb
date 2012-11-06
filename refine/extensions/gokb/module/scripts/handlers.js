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
    		
    		// Create HTML list of returned items.
    		var list = $("<ul></ul>");
    		$.each(data.result, function() {
    			$("<li>")
    				.text(this.name)
    			.appendTo(list);
    		});
    		
    		// Create and show a dialog with the returned list attached.
    		var dialog = GOKbExtension.createDialog("Suggested Transformations", "suggest");
    		list.appendTo(dialog.bindings.dialogContent);
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
			ops.bindings.dialogContent.html(JSON.stringify(data.entries));
	  } else {
	  	ops.bindings.dialogContent.html("<p>No entries were found</p>");
	  }
		GOKbExtension.showDialog(ops);
	});
}