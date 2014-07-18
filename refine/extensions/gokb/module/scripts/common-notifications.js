/*
 * This is a javascript helper for the PNotify library.
 */


// Self executing function.
(function($){
  
  /**
   * Create our notification object to help us with showing all kinds of notifications.
   */
  Notification = function () {    
    this._stacks = {
      "system" : {
        "dir1": "down",
        "dir2": "left",
      },
      "validation" : {
        "dir1": "down",
        "dir2": "right",
      },
//      "validation-warning" : {
//        "dir1": "down",
//        "dir2": "down",
//        "push": "top",
//        "_notification_defaults" : {
//          animation: 'fade',
//          addclass: "stack-topleft",
//        }
//      },
    };
    
    this._notification_defaults = {
      animation: 'fade',
    };
  };
  
  /**
   * Get the named stack.
   * @param stack_name
   * @returns The stack object.
   */
  Notification.prototype.getStack = function (stack_name) {
    
    // If the stack already exists...
    if (stack_name in this._stacks) {
      
      // Just return it.
      return this._stacks[stack_name];
    }

    // Create a new stack.
    var s = {};
    this._stacks[stack_name] = s;
    return s;
  };

  /**
   * Show the notification, adding it to the supplied stack, or the default stack if one isn't supplied.
   * @param notification
   * @param stack_name
   * @returns {PNotify} The PNotify instance for the notification.
   */
  Notification.prototype.show = function (notification, stack_name) {
    
    // Global message defaults.
    var n = $.extend({}, this._notification_defaults);
    
    // Add the notification to the stack.
    if (stack_name != undefined) {
      var stack = this.getStack(stack_name);
      
      // Mix in the stack message defaults.
      if ("_notification_defaults" in stack) {
        $.extend(n, stack['_notification_defaults']);
      }
      
      // Also set the stack.
      n['stack'] = stack;
    }
      
    // Add supplied notification object.
    $.extend(n, notification);
    
    return new PNotify (n);
  };

  // Set the style property so PNotify knows to use the jQuery UI theme.
  PNotify.prototype.options.styling = "jqueryui";
  
  // And instansiate the object and add to the GOKb namespace.
  GOKb.notify = new Notification();
  
})(jQuery);