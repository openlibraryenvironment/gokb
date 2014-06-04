GOKb.contextMenu = {
  
  /**
   * The contextual target that should be set on show so that the menu option
   * getting focus doesn't interfere with the current element. 
   */
  target : null,
  getTarget : function () {
    return this.target;
  },
  setTarget : function (el) {
    this.target = $(el);
    return this.getTarget();
  },
  
  /**
   * Fired on menu show. Decides whether to show options or not. We should also set the target here as this
   * is fired on show.
   */
  disabledCallback : function (elements) {
    
    // Current element.
    var currentEl = this.setTarget( document.activeElement );
    
    var name = currentEl.prop("tagName");
    
    if (!currentEl.is('.select2-input')) {
      // If the current element is one of the supplied then return true.
      return jQuery.inArray( name, elements ) < 0;
    }
    
    return true;
  },
};

/**
 * The options object used to populate the menu and add callback functions.
 */
GOKb.contextMenu.options = function () {
  
  return {
    items: {
      "gokb-lookup": {
        name  : "GOKb Lookup", 
        disabled: function() {
          return GOKb.contextMenu.disabledCallback([
            "INPUT",
            "TEXTAREA"
          ]);
        },
        items : {
          "gokb-lookup-org" : {
            name: "Organisation",
            callback: function () {
              GOKb.handlers.lookup (
                GOKb.contextMenu.getTarget(),
                "org",
                ["variantNames.variantName"],
                ["variantNames.variantName"],
                "Lookup Organisation"
              );
            }
          },
          "gokb-lookup-package" : {
            name: "Package",
            callback: function () {
              GOKb.handlers.lookup (
                GOKb.contextMenu.getTarget(),
                "package",
                ["variantNames.variantName"],
                ["variantNames.variantName"],
                "Lookup Package",
                true
              );
            }
          },
          "gokb-lookup-platform" : {
            name: "Platform",
            callback: function () {
              GOKb.handlers.lookup (
                GOKb.contextMenu.getTarget(),
                "platform",
                ["variantNames.variantName"],
                ["variantNames.variantName"],
                "Lookup Platform"
              );
            },
          },
        },
      },
  //  "cut": {name: "Cut", icon: "cut"},
  //  "copy": {name: "Copy", icon: "copy"},
  //  "paste": {name: "Paste", icon: "paste"},
  //  "delete": {name: "Delete", icon: "delete"},
  //  "sep1": "---------",
  //  "quit": {name: "Quit", icon: "quit"}
    },
  };
};

// Add the context menu here.
$(document).ready(function(){
  $.contextMenu({
    selector : "body",
    zIndex: 100001,
    build: GOKb.contextMenu.options
  });
});