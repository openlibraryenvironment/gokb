/**
 * This is the feature that adds the elastic search reconciliation to the reconciliation types.
 */
GOKb.registerFeature (
    'ES Recon',
    {
      "include" : [
         'scripts/features/project/es_recon/es-recon.js',
         'scripts/features/project/es_recon/es-recon-panel.js',
       ],
       "require" : ['es-recon']
    },
    function($) {

      var view_uri = "resource/show/"

      ReconciliationManager.registerService({
        "name" : ESRecon.name,
        "ui" : { "handler" : "ReconESPanel" },
        "url" : GOKb.core.workspace.base_url,
        "view" : {
          "url" : GOKb.core.workspace.base_url + view_uri + "{{id}}"
        }
      });
    }
);