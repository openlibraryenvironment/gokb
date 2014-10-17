/*
 * This is a javascript helper for the PNotify library.
 */
 
/**
 * Create our notification object to help us with showing all kinds of notifications.
 */
Notification = function () {    
  this._stacks = {
    "system" : {
      dir1  : "down",
      dir2  : "left",
      _notification_defaults  : {
        type : "info",
      },
    },
    "validation" : {
      dir1      : "right",
      dir2      : "down",
      firstpos1 : 0,
      firstpos2 : 0,
      _notification_defaults : {
        hide    : false,
        buttons: {
          closer   : false,
          sticker  : false,
        }
      },
    },
  };
  
  this._notification_defaults = {
    animation: {
      effect_in   : 'fade',
      effect_out  : 'none',
    }
  };
  
  // Alert.
  this._old_alert = null;
  
  this.idMap = {};
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
  
  // First check the idMap to see if we need to show.
  if ("id" in notification) {
    
    // Grab the ID.
    var id = notification.id;
    
    // Check the map.
    if (id != null && id in this.idMap) {
      return this.idMap[id];
    }
  }
  
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
  
  var note = new PNotify (n);
  
  if ("id" in n) {
    // Add the id to the notification.
    note.text_container.attr("id", n.id);
    
    // Then add to the map.
    this.idMap[n.id] = note;
  }
  
  return note;
};

// Set the style property so PNotify knows to use the jQuery UI theme.
PNotify.prototype.options.styling = "jqueryui";

// And instansiate the object and add to the GOKb namespace.
GOKb.notify = new Notification();

if (typeof ProcessPanel !== 'undefined') {

  GOKb.hijackFunction (
    'ProcessPanel.prototype.showUndo',
    function(historyEntry, oldFunction) {

      // In this case we are not going to be running the original.
      // Just send through our new alert method instead.
      GOKb.notify.show({
        title   : "Data Updated",
        text    : historyEntry.description,
        type    : 'success',
        before_open : function (notice) {
          
          // Build the undo link.
          var undo = $('<span />').addClass('notification-action')
            .append($('<a />')
              .text('undo')
              .click(function(){
                Refine.postCoreProcess(
                    "undo-redo",
                    { undoID: historyEntry.id },
                    null,
                    { everythingChanged: true }
                );
                
                notice.remove();
              })
            )
          ;
          
          notice.text_container.append(undo);
        },
      });
    }
  );
}

// Hijack the default alert mechanism.
GOKb.hijackFunction(
  'window.alert',
  function(message, oldFunction) {
    GOKb.notify.show({
      text  : message,
      title : "System Message",
      hide  : false
    });
  }
);