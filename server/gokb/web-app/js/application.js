if (typeof jQuery !== 'undefined') {
  (function($) {
    $('#spinner').ajaxStart(function() {
      $(this).fadeIn();
    }).ajaxStop(function() {
      $(this).fadeOut();
    });
    
    // Add the submit handler to the "action" form to prompt for confirmation
    // On certain types of action.
    $("form.action-form button[type='submit'], form.action-form input[type='submit']").click(function(event) {
      
      // The button.
      var button = $(this);
      
      // Prevent the click.
      event.preventDefault();
      
      // Selected option.
      var opt = $('#selectedBulkAction option:selected');
      
      if (opt.attr('value').indexOf("method::") == 0) {
        
        // We need to confirm these actions.
        var text = opt.text();
        
        // Create a dialog.
        var dialog = $( "<div id='dialog-confirm' />" )
          .text(
            "Are you sure you with to perform the action " + text +
              " for the selected resource(s)?"
          )
        ;
        
        dialog
          .dialog({
            title : "Confrimation required",
            resizable: false,
            modal: true,
            buttons: {
              "Yes": function() {
                
                // Close the dialog.
                dialog.dialog( "close" );
                
                // Submit the form that is attached to the dropdown.
                button.closest("form").submit();
                
              },
              "No": function() {
                
                // Just close...
                dialog.dialog( "close" );
              }
            }
          }
        ).dialog("open");
      }
      
      // Return false.
      return false;
    });
  })(jQuery);
}
