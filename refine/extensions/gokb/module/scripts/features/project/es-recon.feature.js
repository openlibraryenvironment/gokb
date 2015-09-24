/**
 * This is the feature that adds the elastic search reconciliation to the reconciliation types.
 */
GOKb.registerFeature (
  'ES Recon',
  {
    "include" : ['scripts/features/project/es_recon/es-recon-panel.js'],
    "require" : ['es-recon']
  }, 
  function($) {
    ReconciliationManager.customServices.push({
      "name" : "Elastic Search",
      "ui" : { "handler" : "ReconESPanel" }
    });
  }
);