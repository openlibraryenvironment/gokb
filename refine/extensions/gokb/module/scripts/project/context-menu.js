GOKb.contextMenu = {
	applyRules : function (menu, name) {
		$.each(GOKb.contextMenu.rules[name], function(){
			menu.applyrule(this);
		});
	},
	disableMenu : function () {
		var currentTag = $(document.activeElement)[0].tagName;
		switch (currentTag) {
			case "INPUT" :
			case "TEXTAREA" :
				
				return true;
				
			default :
				// Disable the menu by default.
				return false;
		}
	},
	disableOptions : function (menu) {
		var currentTag = $(document.activeElement)[0].tagName;
		switch (currentTag) {
			case "INPUT" :
			case "TEXTAREA" :
				GOKb.contextMenu.applyRules(menu, 'enable-lookup');
				break;
				
			default :
				// We are in an element that allows text entry.
				GOKb.contextMenu.applyRules(menu, 'disable-lookup');
				break;
		}
	},
};

GOKb.contextMenu.rules = {
	"disable-lookup" : [
	  { disable: true, items: ["gokb-lookup"] }
	],
	"enable-lookup" : [
	  { disable: false, items: ["gokb-lookup", "gokb-lookup-org"] }
	]
};

GOKb.contextMenu.options = {
  width: 150,
  items: [
	  {
	  	text: "GOKb Lookup",
//	  	icon: "",
	  	alias: "gokb-lookup",
	  	type:"group",
	  	width: 150,
	  	items:[
	  	  {
	  	  	text: "Organisation",
//	  	  	icon: "",
	  	  	alias: "gokb-lookup-org",
	  	  	action: function () {
	  	  		GOKb.handlers.lookup ("org");
	  	  	}
	  	  }
      ]
    },
  ],
	onShow: GOKb.contextMenu.disableOptions,
	onContextMenu: GOKb.contextMenu.disableMenu,
};

// Add the context menu here.
$(document).ready(function(){
	$("body").contextmenu(GOKb.contextMenu.options);
});