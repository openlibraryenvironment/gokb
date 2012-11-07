var GOKbMenuItems = [
  {
	  "id" : "gokb-menu-suggest",
	  label: "Suggest Transformations",
	  click: function() { 
		  GOKbExtension.handlers.suggest();
	  }
  },
  {
	  "id" : "gokb-menu-history",
	  label: "Show History",
	  click: function() { 
	  	GOKbExtension.handlers.getOperations();
	  }
  }
];

/**
 * GoKB Extension menu entry
 */
ExtensionBar.addExtensionMenu({
  "id" : "gokb-menu",
  "label" : "GOKb",
  "submenu" : GOKbMenuItems
});