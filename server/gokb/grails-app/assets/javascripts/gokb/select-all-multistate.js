
  
  var TEXT_CURRENT_PAGE_SELECTED = "<strong>Current page</strong> selected";
  var LINK_CURRENT_PAGE_SELECTED = "Select all pages";
  var TEXT_ALL_PAGES_SELECTED = "<strong>All pages</strong> selected";
  var LINK_ALL_PAGES_SELECTED = "Select this page only";
  
  $(document).ready(function(){
    
    // Each table that has checkboxes in the first cell of a row.
    $('table').not(".no-select-all").each(function() {
      
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
          .appendTo(first_header_cell)
        ;
        
        // Create the link to flag we would like to select across all pages.
        var link = $('<a href="" class="batch-all-toggle" />');
        var info_text = $('<span class="info-text" />');
        
        var toggleLink = function (val) {
          // Check the all cb value.
          if (val == "none") {
            
            // We are moving from page to all_pages
            all_cb.val("all");
            
            // Change the link text.
            link.html (LINK_ALL_PAGES_SELECTED);
            
            // Set the guidance text.
            info_text.html (TEXT_ALL_PAGES_SELECTED);
            
          } else if (val == "all") {
            
            // We are moving from all to single page.
            all_cb.val("none");
            
            // Change the link text.
            link.html (LINK_CURRENT_PAGE_SELECTED);
            
            // Set the guidance text.
            info_text.html(TEXT_CURRENT_PAGE_SELECTED);
          }
        }
        
        // On click toggle.
        link.click (function(e){
          e.preventDefault();
          e.stopImmediatePropagation();
          toggleLink(all_cb.val());
        });
        
        // Set the link defaults.
        toggleLink("all");
        
        // Div to contain link and information on current selection.
        var info = $('.batch-all-info');
        
        if (info.length ==0) {
          // Create the div and insert just before the table.
          info = $('<div class="batch-all-info" />')
            .insertBefore(table);
        }
        info
          .append(info_text)
          .append(" (")
          .append(link)
          .append(")")
          .hide()
        ;
        
        // Add an on-change listener to our checkobox.
        all_cb.change(function(){
            
          // The checkbox.
          var me = $(this);
          
          // When the checkbox state changes we need to decide how to proceed.
          if (me.is(':checked')) {
            
            // Filter for unchecked elements and call click to make 
            cbs.not(":checked").click();
            
            // Display the info.
            info.show();
          } else {
            
            // Filter for unchecked elements and call click to make 
            cbs.filter(":checked").click();
            
            // Hide the info area.
            info.hide();
            
            // Ensure the correct values are set for when the link is next displayed.
            toggleLink("all");
          }
        });
        
        // Add a class to the header cell.
        first_header_cell.addClass("header-select-all");
      }
    });
  });