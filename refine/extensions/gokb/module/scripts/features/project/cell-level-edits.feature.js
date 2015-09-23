/**
 * Cell Level Edits feature.
 */
(function($) {
  
  var settings = {
    defaultColumns : ['publicationtitle']
  };
  
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
        var defaults = [];
        
        var currentColName = Refine.cellIndexToColumn(self._cellIndex).name.toLowerCase();
        
        // Add each one of the required cols.
        $.each(cols, function() {
          
          // Grab the index of the cell in the current document.
          var localName = GOKb.caseInsensitiveColumnName (this);
          var localCol = Refine.columnNameToColumn (localName);
          
          if (localCol != null) {
            var localCell = theProject.rowModel.rows[self._rowIndex].cells[localCol.cellIndex];
            var localValue = localCell != null ? localCell.v : "";
            var lcLocalName = localName.toLowerCase()
            
            if (currentColName == lcLocalName || $.inArray( lcLocalName, settings.defaultColumns ) > -1) {
              defaults.push(colData.length);
            }
            
            // Separate array for the column name only.
            colData.push([localName, localValue]);
            DTData.push( [localName, $('<span class="grey" />').text(localValue == "" ? "<empty>" : localValue)] );
          }
        });
  
        // Create the Table.
        var table = GOKb.toTable (
          ["Column", "Equal to"],
          DTData
        );
  
        // Add selection checkboxes
        table.selectableRows({selected : defaults, checkAll: false});
        
        // Wrap in a table element.
        table = $('<div class="col-table" />')
          .append("<p>Select the columns to use as the conditions for this edit.<br /><strong>Note:</strong> The edit will be applied to all rows that match the criteria selected and not just this cell.</p>")
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
          
          elmts.okallButton.prop('disabled', capture);
        });
        
        elmts.okButton.prependEvent ('click', function(e) {
          if (capture) {
            
            // Apply will apply to all rows with matching criterion.
            // We should stop othe click handlers.
            e.stopImmediatePropagation();
            
            // Start with a copy of the text.
            var newValue = elmts.textarea.val();
            var type = elmts.typeSelect.val();
            if (type == "number") {
              newValue = parseFloat(newValue);
              if (isNaN(newValue)) {
                alert($.i18n._('core-views')["not-valid-number"]);
                return;
              }
            } else if (type == "boolean") {
              newValue = ("true" == newValue);
            } else if (type == "date") {
              newValue = Date.parse(newValue);
              if (!newValue) {
                newValue = DateTimeUtil.parseIso8601DateTime(newValue);
              }
              if (!newValue) {
                alert($.i18n._('core-views')["not-valid-date"]);
                return;
              }
              newValue = "'" + newValue.toString("yyyy-MM-ddTHH:mm:ssZ") + "'.toDate()";
            } else {
              // Assume string.
              newValue = "'" + newValue + "'";
            }
            
            // Every time a change is detected we should build up a GREL statement.
            // Grab each of the selected boxes.
            var checked = $('.cb-cell input[type=checkbox]:checked', table);
            
            // Only if checked items.
            if ( checked.length > 0) {
              
              var constructedGrel = "";
            
              // Go through each box and build up a list of columns.
              checked.each (function(i){
                
                // The index to lookup.
                var index = $(this).val();
                var colName = colData[index][0];
                var value = colData[index][1];
                
                // Statement segment.
                var seg = "if( isNotNull(gokbCaseInsensitiveCellLookup('" + colName + "')), ";
                
                if (value === "") {
                  // Test for blank.
                  seg += "or (isBlank(cells[gokbCaseInsensitiveCellLookup('" + colName + "')]), isBlank(cells[gokbCaseInsensitiveCellLookup('" + colName + "')].value ))"
                } else {
                  // Check for match.
                  seg += "and (isNotNull(cells[gokbCaseInsensitiveCellLookup('" + colName + "')]), cells[gokbCaseInsensitiveCellLookup('" + colName + "')].value == '" + value + "')"
                }
                
                seg += ", false)";
                
                if (i==0) {
                  
                  // First element should just be set as the entire statement.
                  constructedGrel = seg;
                } else {
                  
                  // We need to AND this clause with the previous one.
                  constructedGrel = "and (" + constructedGrel + ", " + seg + ")"
                }
              });
              
              // We should now have a GREL condition. We should set the value to text if a match is found otherwise we should leave as is.
              constructedGrel = 'if ( ' + constructedGrel + ', ' + newValue + ', value )';
              
              // Dismiss the box.
              MenuSystem.dismissAll();
              
              // Apply the grel.
              Refine.postCoreProcess(
                "text-transform",
                {
                  columnName: Refine.cellIndexToColumn(self._cellIndex).name,
                  expression: constructedGrel,
                  onError: 'keep-original',
                  repeat: false,
                  repeatCount: 0
                },
                null,
                { cellsChanged: true }
              );
            } else {
              // No selection made.
              GOKb.notify.show({
                text  : "You need to select at least one column to base the edit on.",
                title : "System Message"
              });
            }
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