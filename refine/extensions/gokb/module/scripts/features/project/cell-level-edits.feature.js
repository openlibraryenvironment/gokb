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
        
        // Create a table of columns that have required rules in the GOKb server.
        var DTData = [["title"]];
  
        // Create the Table.
        var table = GOKb.toTable (
          ["Column Name"],
          DTData
        );
  
        // Add selection checkboxes
        table.selectableRows();
        
        // Wrap in a table element.
        table = $('<div class="col-table" />').append(table).hide();
        
        // Grab the editor and add the extra checkbox.
        var editor = $(".data-table-cell-editor")
          .append(
            $('<div class="data-table-cell-editor-action" />')
              .append($('<input bind="captureCheck" type="checkbox" name="capture-edit" id="capture-edit" value="true" />'))
              .append($('<label for="capture-edit" />').text("Capture Edit"))
              .append($('<div class="data-table-cell-editor-key" bind="or_views_ctrl1">Ctrl+1</div>'))
              .append(table)
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