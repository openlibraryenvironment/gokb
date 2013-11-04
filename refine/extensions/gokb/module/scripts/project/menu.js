var standardMenuItems = [
	{
	  "id" : "gokb-menu-suggest",
	  label: "Suggest Operations",
	  click: function() { 
		  GOKb.handlers.suggest();
	  }
	},
//	{
//		"id" : "gokb-menu-estimate-changes",
//	  label: "Estimate Data Changes",
//	  click: function() { 
//		  GOKb.handlers.estimateChanges();
//	  }
//	}
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

/**
 * Add to the data table "All" column menu.
 */
DataTableView.extendMenu(function(dataTableView, menu) {
	
	// Find the row/-column menus.
	var col_menu = null;
	var row_menu = null;
	for (var i=0; i<menu.length && !(col_menu && row_menu); i++) {
		if (menu[i].id == 'core/edit-rows') {
			row_menu = menu[i].submenu;
		} else if (menu[i].id == 'core/edit-columns') {
			col_menu = menu[i].submenu;
		}
	}
	
	// Default to just the main menu.
	if (!col_menu) col_menu = menu;
	if (!row_menu) row_menu = menu;
	
	// Add any Menu items here.
	row_menu.push(
		{
	    id: "gokb-add-row",
	    "label": "Prepend Rows",
	    "click": function() {
	    	GOKb.handlers.addRows();
	    }
	  }
//		,
//	  {
//	  	id: "gokb-trim-all",
//	  	"label": "Trim all whitespace",
//	  	"click": function () {
//	  		GOKb.handlers.trimData();
//	  	}
//	  }
	);
	
	col_menu.push(
		{
		  id: "gokb-add-column",
		  "label": "Add Column",
		  "click": function() {
		  	var name = window.prompt("Enter a column name title", "");
		  	if (name && name != "") {
		  		GOKb.handlers.createBlankColumn(name);
		  	}
	    }
	  }
	);
});