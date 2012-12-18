var standardMenuItems = [
//  {
//	  "id" : "gokb-menu-describe",
//	  label: "Describe Document",
//	  click: function() { 
//	  	GOKb.handlers.describe();
//	  }
//  },
//  {
//	  "id" : "gokb-menu-history",
//	  label: "Show Applied Operations",
//	  click: function() { 
//	  	GOKb.handlers.history();
//	  }
//  },
	{
	  "id" : "gokb-menu-suggest",
	  label: "Suggest Operations",
	  click: function() { 
		  GOKb.handlers.suggest();
	  }
	},
];

// Create menu items that are contextual.
//var cusMd = theProject.metadata.customMetadata;
//if (!cusMd.gokb || cusMd.gokb != true) {
	// It's not a GOKb project file, so add "Add to Repo" link.
	GOKb.menuItems.push({
		"id" : "gokb-menu-add-to-repo",
	  label: "Check-in this project for the first time",
	  click: function() { 
		  GOKb.handlers.addToRepo();
	  }
	});
//}

// Add the standard Items to the menu.
GOKb.menuItems = GOKb.menuItems.concat(standardMenuItems);

/**
 * GoKB Extension menu entry
 */
ExtensionBar.addExtensionMenu({
  "id" : "gokb-menu",
  "label" : "GOKb",
  "submenu" : GOKb.menuItems
});