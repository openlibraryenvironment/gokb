var GOKbMenuItems = [
  {
	  "id" : "gokb-menu-suggest",
	  label: "Suggest Operations",
	  click: function() { 
		  GOKbExtension.handlers.suggest();
	  }
  },
  {
	  "id" : "gokb-menu-history",
	  label: "Show Applied Operations",
	  click: function() { 
	  	GOKbExtension.handlers.history();
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