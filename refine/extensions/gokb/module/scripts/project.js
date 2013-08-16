/**
 * Default project screen include.
 */
GOKb.hijackFunction (
  "initializeUI",
  function (uiState, oldFunction) {
  	
  	// Execute the old code.
  	oldFunction.apply(this, arguments);
  	
  	// The Validation tab.
  	var vTab = $('<a>Errors <span class="count">0</span></a>')
  	  .attr("id", "gokb-validation-tab")
  	  .attr("href", "#gokb-validation-panel")
  	  .appendTo($("<li />").appendTo( $('ul.ui-tabs-nav', ui.leftPanelTabs)) )
  	;
  	
  	// The validation panel.
  	var vPanel = $('<div>')
  	  .attr("id", "gokb-validation-panel")
  	  .appendTo(ui.leftPanelTabs)
  	;
  	
  	// Create the validation panel.
  	GOKb.validationPanel = new ValidationPanel(vPanel, vTab);
  	
  	// Remove tabs.
  	ui.leftPanelTabs.tabs( "destroy" );
  	
  	// Re-add the tabs.
  	ui.leftPanelTabs.tabs({ selected: 2 });
    resize();
    resizeTabs();
  	GOKb.validationPanel.resize();
  }
);

// Replace the current CreateUpdate function with our own to update the validation panel.
GOKb.hijackFunction (
  'Refine.createUpdateFunction',
  function(options, onFinallyDone, oldFunction) {
	
		var functions = [oldFunction.apply(this, arguments)];
		
		// Push our update function to list of functions to be executed.
//		if (options.everythingChanged || options.modelsChanged || options.rowsChanged || options.rowMetadataChanged || options.cellsChanged) {
			
			// If one of the above flags is true then we need to update the validation tab.
			// Passing in the previously added function.
			functions.unshift(function() {
				GOKb.validationPanel.update(functions[1]);
	    });
//		}
		
		// Execute our function.
		return functions[0];
  }
);

GOKb.caseInsensitiveColumnName = function (name) {
	var columns = theProject.columnModel.columns;
  for (var i = 0; i < columns.length; i++) {
    var column = columns[i];
    if (column.name.toLowerCase() == name.toLowerCase()) {
      return column.name;
    }
  }
  return name;
};