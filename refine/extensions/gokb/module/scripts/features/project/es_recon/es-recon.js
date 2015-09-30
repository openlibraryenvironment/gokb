/**
 * This file provides the main object to interact with the Elastic Search back end.
 */

var ESRecon = (function($) {
  return {
    getTypes : function() {
      return GOKb.doCommand(
        "es-get-types",
        {},
        {}
      ); 
    }
  };
  
})(jQuery);