if (typeof jQuery !== 'undefined') {
  (function($) {

    /**
     * Method that disables the list of actions.
     */
    function setActionListEmpty() {

      // The list.
      var actionsList = $('ul.actions');

      var link = $("<a>", { text:"-- No actions available --",class:"selectedAction", href: "#"  });
      var listItem = $("<li>").append(link);
      actionsList.append(listItem);
    }

    /**
     * Method that updates the available actions on a the action list
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

      // Add the available actions to the list of actions
      var actionsList = $('ul.actions')
      actionsList.empty()
      // Disable/enable the form elements depending on the actions available
      if (allActionsAvailable.length > 0) {

        // Add the options to the list.
        $.each(allActionsAvailable, function (index, action) {
           var link = $("<a>", { bulkaction:action.code,text:action.label,class:"selectedAction", href: "#"  });
           var listItem = $("<li>").append(link);
           actionsList.append(listItem);
        });

      } else {
        // No actions available disable controls.
        setActionListEmpty();
      }
    }

    // Once the page is ready fire our jQuery.
    $(document).ready( function() {

      // Add the submit handler to the "action" form to prompt for confirmation
      // On certain types of action.
      $("ul.actions").click(function(event) {
        // The button.

        var selectedLink = $(event.target).closest("a.selectedAction");


        // Prevent the click bubbling through to eventually submitting the form.
        event.preventDefault();
        event.stopImmediatePropagation();
        
        if(selectedLink.length > 0){
          // Selected option.
          // We need to confirm these actions.
          // Confirm.
          gokb.confirm (
            function() {
              // Submit the form that is attached to the dropdown.
              var form = selectedLink.closest(".navbar").siblings('form.action-form');
              var workflowValue = $('<input>',{type:'hidden',name:'selectedBulkAction',value:selectedLink.attr('bulkaction')})
              workflowValue.appendTo(form);
              form.submit();
            },
            "Are you sure you with to perform the action " + selectedLink.text() + " for the selected resource(s)?",
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
