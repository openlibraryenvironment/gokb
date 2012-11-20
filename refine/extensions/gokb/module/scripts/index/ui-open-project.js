// Create GOKb Project UI
GOKb.ui.openProject = function (elmt) {
  var self = this;
	elmt.html(DOM.loadHTML("gokb", "scripts/index/ui-open-project.html"));
	this._elmt = elmt;
  this._elmts = DOM.bind(elmt);
  GOKb.api.getProjects(
    {},
	  {
	  	onDone : function (data) {
	  		
	  		var data = null;
	  		
	  		if ("result" in data && data.result.length > 0) {
	  			var head = ["", "Name", "Description", "State", "Last&nbsp;modified"];
	  			var body = [];
	  			
	    		// Add each project to the projects screen.
	  			$.each(data.result, function () {
	  				
	  				var row[];
	  				
	  				var checkoutLink = $('<a></a>')
		  	      .attr("title","Checkout this project from GOKb to work on it.")
		  	      .attr("href","")
		  	      .css("visibility", "hidden") 
		  	      .addClass("controls")
		  	      .text("Check Out")
		  	      .click(function(){
		  	      	// Do some stuff...
		  	      	
		  	        self.resize();
		  	      	return false;
		  	      })
		  	    ;
	  				
	  				// Add the link to the data.
	  				row.push(checkoutLink);
		  	    
	  				row.concat([
	  				  this.name,
	  				  this.description,
	  				  (this.locked ? "locked" : "unlocked"),
	  				  this.modified
	  				]);
	  				
	  				// Push the row to the body.
	  				body.push(row);
	  			});
	  			
	  			// Now we have the data create the table
		  		data = GOKb.toTable(head, body);

		  		// Add show/hide to controls
		  		$(tr, data).mouseenter(function() {
		  			$('.controls', this).css("visibility", "visible");
		      }).mouseleave(function() {
		  			$('.controls', this).css("visibility", "hidden");
		      });
	  		}
	  	}
		}
  );
};

// Allow resizing of this element.
GOKb.ui.openProject.prototype.resize = function() {
  var height = this._elmt.height();
  var width = this._elmt.width();
  this._elmts.gokbProjects
  .css("height", (height - DOM.getVPaddings(this._elmts.gokbProjects)) + "px");
};

// Push the to the action areas.
Refine.actionAreas.push({
  id: "gokb-projects",
  label: "GOKb Projects",
  uiClass: GOKb.ui.openProject
});