var GoKBMenuItems = [
  {
	  "id" : "GoKB/file-info",
	  label: "Suggest Transformations",
	  click: function() { 
	    GoKBExtension.handlers.suggest();
	  }
  }
];

/**
 * GoKB Extension menu entry
 */
ExtensionBar.addExtensionMenu({
  "id" : "gobk-button-suggest",
  "label" : "Suggest Transformations",
  "submenu" : GoKBMenuItems
});