GOKb.menuItems = GOKb.menuItems.concat([
  /** comment switch for prototype menu ** /  
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
  /*/
  
	
  {
	  "id" : "gokb-menu-suggest",
	  label: "Suggest Operations",
	  click: function() { 
		  GOKb.handlers.suggest();
	  }
  },

  /**/
]);

/**
 * GoKB Extension menu entry
 */
ExtensionBar.addExtensionMenu({
  "id" : "gokb-menu",
  "label" : "GOKb",
  "submenu" : GOKb.menuItems
});