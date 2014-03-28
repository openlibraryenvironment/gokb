(function ($) {
  
  $(document).ready(function(){
    
    // Each table that has checkboxes in the first cell of a row.
    $('table').each(function() {
      
      // Get the table.
      var table = $(this);
      
      // Grab the first cell checkboxes.
      var cbs = $('tr > td > input[type="checkbox"]:not([disabled])', table);
      
      // Check to see if the first cell in the row contains a checkbox.
      if (cbs.length > 0) {
      
        // Get the first header cell where we are to put the all checkbox.
        var first_header_cell = $('th:first', table);
        
        // Create the checkbox.
        var all_cb = $('<input type="checkbox" name="batch_on" value="none" class="batch-select-all" />')
          .appendTo(first_header_cell);
        
        // Create the link to flag we would like to select across all pages.
        var link = $('<a class="batch-all-toggle" />');
        var info_text = $('<span class="info-text" />');
        
        var toggleLink = function (val) {
          // Check the all cb value.
          if (val == "none") {
            
            // We are moving from page to all_pages
            all_cb.val("all");
            
            // Change the link text.
            link.text("Select this page only");
            
            // Set the guidance text.
            info_text.text ("All results selected");
            
          } else if (val == "all") {
            
            // We are moving from all to single page.
            all_cb.val("none");
            
            // Change the link text.
            link.text("Select results accross all pages");
            
            // Set the guidance text.
            info_text.text ("Current page of results selected");
          }
        }
        
        // On click toggle.
        link.click (function(e){
          toggleLink(all_cb.val());
        });
        
        // Set the link defaults.
        toggleLink("all");
        
        // Div to contain link and information on current selection.
        var info = $('<span class="batch-all-info" />')
          .appendTo(first_header_cell)
          .append(info_text)
          .append(" ")
          .append(link)
          .hide()
        ;
        
        // Add an on-change listener to our checkobox.
        all_cb.change(function(){
            
            // The checkbox.
            var me = $(this);
            
            // When the checkbox state changes we need to decide how to proceed.
            if (me.is(':checked')) {
              
              // Checked.
              cbs.prop("checked", true);
              
              // Display the info.
              info.show();
            } else {
              
              // Not checked.
              cbs.prop("checked", false);
              
              // Hide the info area.
              info.hide();
              
              // Ensure the correct values are set for when the link is next displayed.
              toggleLink("all");
            }
          })
        ;
      }
    });
  });
  
}) (jQuery);