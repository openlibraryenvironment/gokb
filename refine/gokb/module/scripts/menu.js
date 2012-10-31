/**
 * Suggest Transformation button
 */
ExtensionBar.addExtensionMenu({
  "id" : "gobk-button-suggest",
  "label" : "Suggest Transformations",
  "submenu" : [
    {
      "id" : "GoKB/file-info",
      label: "Edit GoKB Ingest Properties",
      click: function() { 
        GoKBExtension.handlers.suggest();
      }
    }
  ]
});