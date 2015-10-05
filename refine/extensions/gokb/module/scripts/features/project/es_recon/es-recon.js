/**
 * This file provides the main object to interact with the Elastic Search back end.
 */

var ESRecon = (function($) {
  return {
    getTypes : function() {
      
      return GOKb.doCommand(
        "es-get-types"
      );
    },
    recon : function(columnName, config) {
      
      // Merge into defaults.
      config = $.extend(true, {
        mode: "gokb/ElasticSearch",
      }, (config || {}));
      
      return Refine.postCoreProcess(
        "reconcile",
        {},
        {
          "columnName"  : columnName,
          "config"      : JSON.stringify(config)
        },
        { cellsChanged: true, columnStatsChanged: true }
      );
    }
  };
  
})(jQuery);