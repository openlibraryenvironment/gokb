/**
 * Default project screen include.
 */
GOKb.kidnapFunction ((initializeUI), "initializeUI");

// Replace the initializeUI method.
initializeUI = function (uiState) {
	
	// Call the old method.
	GOKb.runKidnappedFunction('initializeUI', uiState);
	
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
};

// Replace the current CreateUpdate function.
GOKb.currentCreateUpdateFunction = (Refine.createUpdateFunction);

Refine.createUpdateFunction = function(options, onFinallyDone) {
	
	var functions = [GOKb.currentCreateUpdateFunction(options, onFinallyDone)];
	
	// Start by running the original method.
	
	// Push our update function to list of functions to be executed.
	if (options.everythingChanged || options.modelsChanged || options.rowsChanged || options.rowMetadataChanged || options.cellsChanged || options.engineChanged) {
		
		functions.unshift(function() {
			GOKb.validationPanel.update(functions[1]);
    });
	}
	
	// Execute our first function.
	return functions[0];
};