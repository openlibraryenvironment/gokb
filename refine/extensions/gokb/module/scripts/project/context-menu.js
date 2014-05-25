GOKb.contextMenu = {
  disabledCallback : function (elements) {
    
    // Current element.
    var currentEl = $(document.activeElement);
    var name = currentEl.prop("tagName");
    
    if (!currentEl.is('.select2-input')) {
      // If the current element is one of the supplied then return true.
      return jQuery.inArray( name, elements ) < 0;
    }
    
    return true;
  },
};

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
                $(document.activeElement),
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
                $(document.activeElement),
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
                $(document.activeElement),
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