GOKb.menuItems = GOKb.menuItems.concat([
  {
	  "id" : "gokb-menu-describe",
	  label: "Describe Document",
	  click: function() { 
	  	GOKb.handlers.describe();
	  }
  },
  {
	  "id" : "gokb-menu-suggest",
	  label: "Suggest Operations",
	  click: function() { 
		  GOKb.handlers.suggest();
	  }
  },
  {
	  "id" : "gokb-menu-history",
	  label: "Show Applied Operations",
	  click: function() { 
	  	GOKb.handlers.history();
	  }
  },
  {
	  "id" : "gokb-menu-fingerprint",
	  label: "Fingerprint",
	  click: function() {
	  	GOKb.handlers.fingerprint();
	  }
  },
]);

/**
 * GoKB Extension menu entry
 */
ExtensionBar.addExtensionMenu({
  "id" : "gokb-menu",
  "label" : "GOKb",
  "submenu" : GOKb.menuItems
});