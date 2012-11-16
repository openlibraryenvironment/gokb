var GOKbMenuItems = [
  {
	  "id" : "gokb-menu-describe",
	  label: "Show Applied Operations",
	  click: function() { 
	  	GOKbExtension.handlers.history();
	  }
  },
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
  },
  {
	  "id" : "gokb-menu-fingerprint",
	  label: "Fingerprint",
	  click: function() {
	  	GOKbExtension.handlers.fingerprint();
	  }
  },
];

/**
 * GoKB Extension menu entry
 */
ExtensionBar.addExtensionMenu({
  "id" : "gokb-menu",
  "label" : "GOKb",
  "submenu" : GOKbMenuItems
});