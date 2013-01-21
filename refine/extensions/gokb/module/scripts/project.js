/**
 * Default project screen include.
 */

GOKb.currentInitUI = (initializeUI);

// Replace the initializeUI method.
initializeUI = function (uiState) {
	
	// Call the current method, but also add our extras.
	GOKb.currentInitUI(uiState);
	
	// The Validation tab.
	var vTab = $('<a>Validation <span class="error count">100</span></a>')
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