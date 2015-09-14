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
        
        // Create the capture checkbox.
        var capture_check = $('<input type="checkbox" name="capture-edit" id="capture-edit" value="true" />');
        
        // Grab the editor and add the extra checkbox.
        var editor = $(".data-table-cell-editor")
          .append(
            $('<div class="data-table-cell-editor-action" />')
              .append(capture_check)
              .append($('<label for="capture-edit" />').text("Capture Edit"))
              .append($('<div class="data-table-cell-editor-key" bind="or_views_ctrlC">Ctrl+C</div>'))
          )
        ;
        
        // Grab the current click events for the buttons.
        var okAllClicks = $._data( elmts.okallButton.get(0) );
        
        // Add the change listener so we can show and hide the table of columns for the condition.
        capture_check.change(function(e){
          
          // The check box.
          var me = $(this);
          
          // Checked value.
          var checked = me.prop("checked");
          
          if (checked) {
          } else {
          }
        });
        
        // Bind the elements.
        var elmts = DOM.bind(editor);
        
        
        
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