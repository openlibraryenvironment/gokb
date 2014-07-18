/*
 * Default project screen include.
 */

/**
 * Add an extra method toi the GOKb namespace to allow for case insensitive search for column.
 */ 
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


/*
 * The following methods are used to replace already existing methods within refine.
 * This allows us to extend or replace already existing functions while still having access
 * to the original from whichever version of refine is running.
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

// Replace the base resize method.
GOKb.hijackFunction (
  "resize",
  function (oldFunction) {
  	
  	// Widen the left panel by x pixels.
  	var pixels = 25;

    // Execute the old code.
    oldFunction.apply(this, arguments);
    
    // Get the left and right panels.
    var rp = ui.rightPanelDiv;
    var lp = ui.leftPanelDiv;
    
    // Increase the width of the left panel and decrease the right.
    lp.width (
      lp.width() + pixels
    );
    
    rp.width (
      rp.width() - pixels
    );
    
    // Get the right panel position.
    var rp_pos = rp.offset();
    rp_pos.left = rp_pos.left + pixels;
    
    rp.offset(rp_pos);
  }
);

// Replace the adjustTable method.
GOKb.hijackFunction (
  "DataTableView.prototype._adjustDataTables",
  function (oldFunction) {

    // Execute the old code.
    oldFunction.apply(this, arguments);
    
    // Get the 2 tables we need to compare.
    var dataTable = this._div.find('.data-table');
    var headerTable = this._div.find('.data-header-table');
    dataTable = dataTable[0];
    headerTable = headerTable[0];
    
    var dataTr = dataTable.rows[0];
    var headerTr = headerTable.rows[headerTable.rows.length - 1];

    for (var i = 1; i < headerTr.cells.length; i++) {
      var headerTd = $(headerTr.cells[i]);
      var dataTd = $(dataTr.cells[i + 2]);
      var htd_div = headerTd.find('> div').width("1%");
      var dtd_div = dataTd.find('> div').width("1%");
      var commonWidth = Math.max(
        headerTd.width(),
        dataTd.width()
      );
      	
      // Change the widths.
      htd_div.width(commonWidth);
      dtd_div.width(commonWidth);
    }
    
    // Make sure the scroll is correct.
    this._adjustDataTableScroll();
  }
);


// Replace the current CreateUpdate function with our own to update the validation panel.
GOKb.validationPanelRun = true;
GOKb.hijackFunction (
  'Refine.createUpdateFunction',
  function(options, onFinallyDone, oldFunction) {
  
    var functions = [oldFunction.apply(this, arguments)];
    
    // Push our update function to list of functions to be executed.
//    if (GOKb.validationPanelRun || options.everythingChanged || options.modelsChanged || options.rowsChanged || options.rowMetadataChanged || options.cellsChanged) {
      
      // If one of the above flags is true then we need to update the validation tab.
      // Passing in the previously added function.
      functions.unshift(function() {
        GOKb.validationPanel.update(functions[1]);
      });
      
      GOKb.validationPanelRun = false;
//    }
    
    // Execute our function.
    return functions[0];
  }
);

GOKb.hijackFunction (
  'Refine.setTitle',
  function(status, oldFunction) {

    // Run the original setTitle.
    oldFunction.apply(this, arguments);
    
    // We now need to add the current workspace title too.
    document.title = document.title + " using " + GOKb.workspace.name;
  }
);

GOKb.hijackFunction (
  'ProcessPanel.prototype.showUndo',
  function(historyEntry, oldFunction) {

    // In this case we are not going to be running the original.
    // Just send through our new alert method instead.
    GOKb.notify.show({
      title : "Data Updated",
      text : historyEntry.description,
      before_open : function (notice) {
        
        // Build the undo link.
        var undo = $('<span />').addClass('notification-action')
          .append($('<a />')
            .text('undo')
            .click(function(){
              Refine.postCoreProcess(
                  "undo-redo",
                  { undoID: historyEntry.id },
                  null,
                  { everythingChanged: true }
              );
              
              notice.remove();
            })
          )
        ;
        
        notice.text_container.append(undo);
      },
    });
  }
);

// Hijack the default alert mechanism.
GOKb.hijackFunction(
  'window.alert',
  function(message, oldFunction) {
    GOKb.notify.show({
      text  : message,
      title : "System Message",
      hide  : false
    });
  }
);