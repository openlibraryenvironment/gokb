var standardMenuItems = [
	{
	  "id" : "gokb-menu-suggest",
	  label: "Suggest Operations",
	  click: function() { 
		  GOKb.handlers.suggest();
	  }
	},
];

// Load the project metadata.
GOKb.doRefineCommand("core/get-project-metadata",{ project: theProject.id }, null,
  {
		onDone: function(data) {
			theProject.metadata = data;
			
			// Create menu items that are contextual.
			var cusMd = theProject.metadata.customMetadata;
			if (!cusMd.gokb || cusMd.gokb != true) {
				// It's not a GOKb project file, so add "Add to Repo" link.
				GOKb.noneGOKbProjectItems();
			} else {
				// It is already known to GOKb.
				GOKb.GOKbProjectItems();
			}
		}
  }
);

// Add menu Items for none-GOKb projects
GOKb.noneGOKbProjectItems = function() {
	GOKb.menuItems.unshift({
		"id" : "gokb-menu-add-to-repo",
	  label: "Check-in this project for the first time",
	  click: function() { 
		  GOKb.handlers.checkInWithProps();
	  }
	});
};

// Add menu Items for GOKb projects
GOKb.GOKbProjectItems = function() {
//	GOKb.menuItems.unshift({
//		"id" : "gokb-menu-ingest",
//	  label: "Check-in and ingest this project",
//	  click: function() { 
//		  GOKb.handlers.checkInWithProps();
//	  }
//	});
};

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