//Global namespace for GOKb functions.
window.gokb = {};

// Define some functions that can be used application-wide.
if (typeof jQuery !== 'undefined') {
  (function($) {

    gokb.dialog = function (opts) {
      var message = opts.message;
      var buttons = [];
      if ("buttons" in opts) {    	
        for (var button in opts.buttons) {
          
          // Go through each of the buttons.
          buttons.push(
            opts.buttons[button]
          );
        }
      }

      // Create the dialog.
      return bootbox.dialog(message, buttons, opts);
    };

    /**
     * Show a confirmation box.
     */
    gokb.confirm = function (confirmCallback, message, confirmText, cancelText, cancelCallback) {
      
      // Set the defaults.
      if (typeof message == 'undefined') {
        message = "Are you sure?";
      }
      if (typeof confirmText == 'undefined') {
        confirmText = "Yes";
      }
      if (typeof cancelText == 'undefined') {
        cancelText = "No";
      }
      if (typeof cancelCallback == 'undefined') {
        cancelCallback = false;
      }
      
      // Add the message.
      var options = {
        "message" : message,
        "buttons" : {
          "Confirm" : {
            "label": confirmText,
            "class": "btn-success",
            "callback": confirmCallback
          },
          "No": {
            "label": cancelText,
            "class": "btn-danger",
            "callback": cancelCallback
          }
        }
      };

      // Default to empty object.      
      return gokb.dialog (options);
    };
    
    // Add some default behaviours we wish to define application wide.
    $('.confirm-click').click(function(e) {
      
      // The target.
      var target = $(this);
      
      if (target.hasClass('confirm-click-confirmed')) {
        
        // This action has been confirmed.
        target.removeClass('confirm-click-confirmed');
        
        // If we are here then the action was confirmed.
        var href = target.attr("href");
        if (href) {
          window.location.href = href;
        }
        
        // Do nothing just allow the click.
        
      } else {
        
        // Get the message attribute.
        var message = target.attr('data-confirm-message');
        
        // Prevent default as we need to confirm the action.
        e.preventDefault();
        
        // Now we need to ask the user to confirm.
        gokb.confirm (
          function () {
            // We need to refire the click event on the target after setting the class as a flag.
            target.addClass('confirm-click-confirmed');
            
            // Now refire the click.
            target.trigger('click', e);
          },
          target.attr('data-confirm-message'),
          target.attr('data-confirm-yes'),
          target.attr('data-confirm-no')
        );
          
          
      }
    });
  })(jQuery);
}