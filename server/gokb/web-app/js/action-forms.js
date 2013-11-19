if (typeof jQuery !== 'undefined') {
  (function($) {
    
    /**
     * Method that disables the form for actions.
     */
    function setActionFormStatus(status) {
      
      // The drop down.
      var dd = $('select#selectedBulkAction');
      
      // Prepend the default option.
      dd.prepend($('<option />', { 
        "value" : "",
        "text"  : status ? "-- Select an action to perform --" : "-- No actions available --"
      }).prop("selected", true));
      
      // Enable or disable the elements.
      $('select,input', $('#bulkActionControls')).prop('disabled', !status);
      
      // Always disable here as the on change should enable it once a none default option
      // has been selected.
      $('button', $('#bulkActionControls')).prop('disabled', true);
    }
    
    /**
     * Method that updates the available actions on a given select box.
     */
    function updateAvailableActions() {
      // alert("update actions");

      var allActionsAvailable = [];
        
      // Step through each checked box.
      $('input.obj-action-ck-box:checked').each(function(i) {
        var elem = $(this);
        if (i == 0) {
          var json = elem.attr('data-actns');
          
          // Set all actions available to this objects actions.
          allActionsAvailable = $.parseJSON(json);
        } else {
          var json = elem.attr('data-actns');
          
          var elementActions = $.parseJSON(json);
  
          // Filter the array using a callback that checks that this element actions contains
          // the object.
          allActionsAvailable = $.grep(allActionsAvailable, function(action, index) {
            var match = $.grep(elementActions, function (el, i) {
              return el.code == action.code && el.label == action.label;
            });
            return match.length > 0
          });
        }
      });
      
      // Edit the form button and list depending on availability of actions.  
      var opts = $('select#selectedBulkAction').prop("options");
      opts.length = 0;
      
      // Disable/enable the form elements depending on the actions available
      if (allActionsAvailable.length > 0) {
      
        // Add the options to the dropdown.
        $.each(allActionsAvailable, function (index, action) {
          opts[index] = new Option(action.label, action.code);
        });
        
        // Enable controls.
        setActionFormStatus(true);
        
      } else {
        // Disable controls.
        setActionFormStatus(false);
      }
    }
    
    // Once the page is ready fire our jQuery.
    $(document).ready( function() {
    
      // Add the submit handler to the "action" form to prompt for confirmation
      // On certain types of action.
      $("form.action-form button[type='submit'], form.action-form input[type='submit']").click(function(event) {
        
        // The button.
        var button = $(this);
        
        // Prevent the click bubbling through to eventually submitting the form.
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
              width: "30%",
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
      
      // On page load we need to update available actions.
      updateAvailableActions ();
      
      // Also bind the method to the change method. 
      $('input.obj-action-ck-box').change( updateAvailableActions );
      
      // Need to bind onchange listener to list.
      $('select#selectedBulkAction').change(function() {
        
        // The dropdown.
        var dd = $(this);
        
        // The selected index.
        var selected = this.selectedIndex;
        
        // Disable all buttons if the first option is the one selected. 
        dd.siblings('button').prop('disabled', (selected < 1));
      });
    });
    
  })(jQuery);
}