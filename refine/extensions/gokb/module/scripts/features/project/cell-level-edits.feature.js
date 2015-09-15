/**
 * Cell Level Edits feature.
 */
(function($) {
  
  // Wrap in a getCoreData call to ensure that we have the data to test if the
  // feature exists on the server we are connected to.
  GOKb.getCoreData().done(function(){
    
    if (GOKb.hasFeature('cell-level-edits')) {
      
      // Extend the protoype of the CellUI and add a new method for capturing the cell level edits.
      DataTableCellUI.prototype._extendEdit = function(elmt) {
        
        var _self = this;
        
        // Create a table of columns that have required rules in the GOKb server.
        var cols = GOKb.serverInfo()['required-cols'];
        
        var DTData = [];
        var colData = [];
        
        // Add each one of the required cols.
        $.each(cols, function() {
          
          // Grab the index of the cell in the current document.
          var localName = GOKb.caseInsensitiveColumnName (this);
          var localIndex = Refine.columnNameToColumnIndex (localName);
          var localCell = theProject.rowModel.rows[self._rowIndex].cells[localIndex];
          var localValue = localCell != null ? localCell.v : "";
          
          if (localIndex > -1) {
            
            // Separate array for the column name only.
            colData.push([localName, localValue]);
            DTData.push( [localName, $('<span class="grey" />').text(localValue)] );
          }
        });
  
        // Create the Table.
        var table = GOKb.toTable (
          ["Column", "Equal to"],
          DTData
        );
  
        // Add selection checkboxes
        table.selectableRows();
        
        var constructedGrel = "";
        
        // Wrap in a table element.
        table = $('<div class="col-table" />')
          .append("<p>Select the columns to use as the conditions for this edit.</p>")
          .append(table)
          .hide()
        ;
        
        // Grab the editor and add the table.
        var editor = $(".data-table-cell-editor")
          .append(table)
        ;
        
        // Append the action...
        $('#data-table-cell-editor-actions', editor)
          .append(
            $('<div class="data-table-cell-editor-action" />')
              .append($('<input bind="captureCheck" type="checkbox" name="capture-edit" id="capture-edit" value="true" />'))
              .append($('<label for="capture-edit" />').text("Capture Edit"))
              .append($('<div class="data-table-cell-editor-key" bind="or_views_ctrl1">Ctrl+1</div>'))
          )
        ;
        
        // Bind the elements.
        var elmts = DOM.bind(editor);
        
        // The variable that holds the checked value.
        var capture = false;
        
        // Add the change listener so we can show and hide the table of columns for the condition.
        elmts.captureCheck.change(function(e){
          
          // The check box.
          var me = $(this);
          
          // Checked value.
          capture = me.prop("checked");
          
          // Change the visibility of the table.
          table.toggle();
        });
        
        elmts.okButton.prependEvent ('click', function(e) {          
          if (capture) {
            // Run the custom capture of single cell edit.
            alert ("Single cell capture.");
            e.stopImmediatePropagation();
          }
        });
        
        elmts.okallButton.prependEvent ('click', function(e) {          
          if (capture) {
            // Run the custom capture of single cell edit.
            alert ("Multiple cell capture.");
            e.stopImmediatePropagation();
          }
        });
        
        // Also... We should add a key event handler for our new capture element.
        elmts.textarea
          .keydown(function(evt) {
            if (!evt.shiftKey && evt.ctrlKey && evt.keyCode == 49) {
              elmts.captureCheck.trigger('click');
            }
          })
        ;
        
        // The text...
        var text = elmts.textarea.text();
        
        // Add the change listener to the table.
        table.change(function(e) {
          
          constructedGrel = "";
          
          // Every time a change is detected we should build up a GREL statement.
          // Grab each of the selected boxes.
          var checked = $('.cb-cell input[type=checkbox]:checked', this);
          
          // Only if checked items.
          if ( checked.length > 0) {
          
            // Go through each box and build up a list of columns.
            checked.each (function(i){
              
              // The index to lookup.
              var index = $(this).val();
              var colName = colData[index][0];
              var value = colData[index][1];
              
              // Statement segment.
              var seg = ' if( isNotNull(gokbCaseInsensitiveCellLookup("' + colName + '")), ';
              
              // If the value is blank then we should use the isBlank() GREL method.
              if (value === "") {
                seg += 'isBlank(cells[gokbCaseInsensitiveCellLookup("' + colName + '")].value)'
              } else {
                seg += '"' + value + '"==cells[gokbCaseInsensitiveCellLookup("' + colName + '")].value';
              }
              
              seg += ', false) ';
              
              // If we have more than one clause we need to chain them into multiple ands.
              if (i > 0) {
                constructedGrel = ' and( ' + seg + ', ' + constructedGrel + ' ) ';
              } else {
                constructedGrel = seg;
              }
            });
            
            // We now have a grel condition...
            // We can now build the rest.
            constructedGrel = 'if ( ' + constructedGrel + ', "' + text + '", value )';
            
            alert (constructedGrel);
          }
        });
      }
      
      // Extend the original function call and call our new method afterwards.
      GOKb.hijackFunction(
        'DataTableCellUI.prototype._startEdit',
        function(elmt, oldFunction) {
          
          // Run the original.
          oldFunction.apply(this, arguments);
          this._extendEdit(elmt);
        }
      );
    }
  });
})(jQuery);