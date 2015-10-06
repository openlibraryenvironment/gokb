/**
 * This file provides the main object to interact with the Elastic Search back end.
 */
var ESRecon = (function($) {
  
  // Declare any local vars.
  var name = "GOKb";
  
  // Setup work here.
  GOKb.hijackFunction (
    'DataTableCellUI.prototype._render',
    function(oldFunction) {
      
      // Run the original.
      oldFunction.apply(this, arguments);
      
      // Grab the cell.
      var cell = this._cell;
      
      // Grab the Cell and check to see if it is a recon value.
      if (cell && "v" in cell && cell.v !== null && cell.r) {
        
        // Grab the service that was used to create the Recon values.
        var r = cell.r;
        var service = (r.service) ? ReconciliationManager.getServiceFromUrl(r.service) : null;
        
        // If this is a Recon from our module then we should remove the create new link.
        if (service && "name" in service && service.name === name) {
          var create_new = $('.data-table-cell-content > .data-table-recon-candidates > .data-table-recon-candidate:last-child', this._td);
          create_new.remove();
        }
      }
    }
  );
  
  // Public API...
  return {
    "name" : (name),
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