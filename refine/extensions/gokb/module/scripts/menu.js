var GOKbMenuItems = [
  {
	  "id" : "gokb-menu-suggest",
	  label: "Suggest Transformations",
	  click: function() { 
		GOKbExtension.handlers.suggest();
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