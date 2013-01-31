// Replace the _startEdit function on the DataTableCellUI object.

GOKb.hijackFunction(
  'DataTableCellUI.prototype._startEdit',
  function(elmt, oldFunction) {
  	
  	// Run the original.
  	oldFunction.apply(this, arguments);
  	
  	// Get the current column.
//  	var column = Refine.cellIndexToColumn(this._cellIndex);
//  	
//  	// If the current column name is the title one then add here.
//  	if (column.name == "publication_title") {
//  		// We are in the correct column, add the auto-complete.
//  		GOKb.multiAutoComplete (
//  		  $(".data-table-cell-editor-editor"),
//  		  ["a test", "second one"],
//  		  "->"
//  		);
//  	}
  }
);