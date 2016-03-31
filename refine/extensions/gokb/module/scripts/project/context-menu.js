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
  _niceNames : null,
  niceNames : function () {
    if (GOKb.contextMenu._niceNames === null) {
      GOKb.contextMenu._niceNames = GOKb.isCapable("macros");
    }
    
    return GOKb.contextMenu._niceNames;
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
  
  /**
   * Menu options used to build the menu.
   */
  option_lists : [{
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
            
            var lookupProps;
            if (GOKb.contextMenu.niceNames()) {
              lookupProps = ["variantNames.variantName:Variant"];
            } else {
              lookupProps = ["variantNames.variantName"];
            }
            
            GOKb.handlers.lookup (
              GOKb.contextMenu.getTarget(),
              "org",
              lookupProps,
              lookupProps,
              "Lookup Organisation"
            );
          }
        },
        "gokb-lookup-package" : {
          name: "Package",
          callback: function () {
            
            var lookupProps;
            if (GOKb.contextMenu.niceNames()) {
              lookupProps = ["variantNames.variantName:Variant"];
            } else {
              lookupProps = ["variantNames.variantName"];
            }
            
            GOKb.handlers.lookup (
              GOKb.contextMenu.getTarget(),
              "package",
              lookupProps,
              lookupProps,
              "Lookup Package",
              true
            );
          }
        },
        "gokb-lookup-platform" : {
          name: "Platform",
          callback: function () {

            var lookupProps;
            if (GOKb.contextMenu.niceNames()) {
              lookupProps = ["variantNames.variantName:Variant"];
            } else {
              lookupProps = ["variantNames.variantName"];
            }
            
            GOKb.handlers.lookup (
              GOKb.contextMenu.getTarget(),
              "platform",
              lookupProps,
              lookupProps,
              "Lookup Platform"
            );
          },
        },
        "gokb-lookup-imprint" : {
          name: "Imprint",
          callback: function () {
            
            var lookupProps;
            if (GOKb.contextMenu.niceNames()) {
              lookupProps = ["variantNames.variantName:Variant"];
            } else {
              lookupProps = ["variantNames.variantName"];
            }
            
            GOKb.handlers.lookup (
              GOKb.contextMenu.getTarget(),
              "imprint",
              lookupProps,
              lookupProps,
              "Lookup Imprint"
            );
          },
        },
      },
    },
  }],
  
  /**
   * Method to push new options.
   */
  addConfig : function (opts) {
    this.option_lists.push(opts);
  }
};

/**
 * The options object used to populate the menu and add callback functions.
 * This is the default item list.
 */
GOKb.contextMenu.options = function () {
  
  var opts = {};
  $.each(GOKb.contextMenu.option_lists, function(){
    $.extend(true, opts, this);
  });
  
  return {
    items: opts
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