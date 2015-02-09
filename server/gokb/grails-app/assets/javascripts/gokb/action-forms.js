if (typeof jQuery !== 'undefined') {
  (function($) {

    /**
     * Method that disables the form for actions.
     */
    function setActionFormStatus(status) {

      // The drop down.
      var dd = $('ul.actions');

      var link = $("<a>", { text:"-- No actions available --",class:"selectedAction", href: "#"  });
      var listItem = $("<li>").append(link);
      dd.append(listItem);
      // Enable or disable the elements.
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
      var opts = $('ul.actions')
      opts.empty()
      // Disable/enable the form elements depending on the actions available
      if (allActionsAvailable.length > 0) {

        // Add the options to the dropdown.
        $.each(allActionsAvailable, function (index, action) {
           var link = $("<a>", { bulkaction:action.code,text:action.label,class:"selectedAction", href: "#"  });
           var listItem = $("<li>").append(link);
           $('ul.actions').append(listItem);
        });

        // Enable controls.
        // setActionFormStatus(true);

      } else {
        // Disable controls.
        setActionFormStatus(false);
      }
    }

    // Once the page is ready fire our jQuery.
    $(document).ready( function() {

      // Add the submit handler to the "action" form to prompt for confirmation
      // On certain types of action.
      $("ul.actions").click(function(event) {
        // The button.

        var link = $(event.target).closest("a.selectedAction");


        // Prevent the click bubbling through to eventually submitting the form.
        event.preventDefault();
        event.stopImmediatePropagation();
        
        if(link.length > 0){

          var text = link.text();
          // Selected option.
          // We need to confirm these actions.
          // Confirm.
          gokb.confirm (
            function() {
              // Submit the form that is attached to the dropdown.
              var form = link.closest("form")
              var workflowValue = $('<input>',{type:'hidden',name:'selectedBulkAction',value:link.attr('bulkaction')})
              workflowValue.appendTo(form);
              form.submit();
            },
            "Are you sure you with to perform the action " + link.text() + " for the selected resource(s)?",
            "Yes I am"
          );

          // Return false.
          return false;
        }
      });

      // On page load we need to update available actions.
      if ( $('ul.actions').length > 0 ) {
        updateAvailableActions ();
      }

      // Also bind the method to the change method. 
      $('input.obj-action-ck-box').change( updateAvailableActions );

    });

  })(jQuery);
}
