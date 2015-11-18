/**
 * Macros feature.
 */

GOKb.registerFeature ('Macros', { 'require' : ['macros'] }, function($) {
  
  alert("Loaded");
  var macro_text = $("<span />");
  
  GOKb.contextMenu.addConfig({
    
    "gokb-apply-macro": {
      name  : "Apply Macro",
      callback: function () {
        GOKb.handlers.lookup (
          GOKb.contextMenu.getTarget(),
          "macro",
          ["description:Description", "tags.value:Tag"],
          ["description:Description", "tags.value:Tag", "refineTransformations"],
          "Lookup Macro"
          
        ).setCallback(function (item, actEl) {
          var json;

          try {
            json = item.refineTransformations;
            json = JSON.parse(json);
          } catch (e) {
            alert($.i18n._('core-project')["json-invalid"]+".");
            return;
          }

          Refine.postCoreProcess(
              "apply-operations",
              {},
              { operations: JSON.stringify(json) },
              { everythingChanged: true }
          );
          
        });
      }
    }
  });
});