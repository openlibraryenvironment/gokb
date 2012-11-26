// Create GOKb Project UI

GOKb.ui.projects = function (elmt) {
  var self = this;
	elmt.html(DOM.loadHTML("gokb", "scripts/index/ui-open-project.html"));
	this._elmt = elmt;
  this._elmts = DOM.bind(elmt);
  
  // Get the projects list from GOKb.
  GOKb.api.getProjects(
    { checkedIn : "True" },
	  {
    	onDone : function (data) {
	  		
	  		if ("result" in data && data.result.length > 0) {
	  			var head = ["", "Name", "Description", "State", "Last&nbsp;modified"];
	  			var body = [];
	  			
	    		// Add each project to the projects screen.
	  			$.each(data.result, function () {
	  				
	  				// Add the row.
	  				var row = [
	  				  self.getProjectControls(this),
	  				  this.name,
	  				  this.description,
	  				  this.checkedIn ? "Checked In" : "Checked Out by " + this.checkedOutBy,
	  				  formatRelativeDate(this.modified)
	  				];
	  				
	  				// Push the row to the body.
	  				body.push(row);
	  			});
	  			
	  			// Now we have the data create the table.
		  		var table = GOKb.toTable(head, body, false);

		  		// Add show/hide to controls.
		  		$("tr", table).mouseenter(function() {
		  			$('.control', this).css("visibility", "visible");
		      }).mouseleave(function() {
		  			$('.control', this).css("visibility", "hidden");
		      });
		  		
		  		// Write the table as the contents of the main window.
		  		self._elmts.projects.html(table);

			  	// Default to this action area.
			  	Refine.selectActionArea("gokb");
	  		}
	  	}
		}
  );
};

GOKb.ui.projects.prototype.createControlLink = function (project, loc, text, title) {
	return $('<a></a>')
		.attr("title",(title ? title : text))
		.attr("href", loc)
		.attr("rel", project.id)
		.css("visibility", "hidden")
		.addClass("control")
		.text(text)
	;
};

GOKb.ui.projects.prototype.getProjectControls = function(project) {
	
	var createControlL
	
	var controls = [];
	var self = this;
	
	// If the project is checked in add the check-out link.
	if (project.checkedIn) {
		controls.push(
		  this.createControlLink(
		    project,
		    '#' + project.id,
		    "Check-Out",
		    "Checkout this project from GOKb to work on it."
		  )
			.click(function(event) {
				
				// Stop the anchor moving to a different location.
				event.preventDefault();
				
				// Create checkout dialog.
				var dialog = GOKb.createDialog("Checkout GOKb project", "form_project_checkout");
				
				// Set the value of the ProjectID field.
				dialog.bindings.projectID.val($(this).attr('rel'));
				
				// Rename close button to cancel.
				dialog.bindings.closeButton.text("Cancel");
				
				// Show dialog.
				GOKb.showDialog(dialog);
			})
		);
	} else {
		
		// Add the link to open the project.
//		createControlLink(
//			project,
//			'/project?project=',
//			"Open",
//			"Open this project in refine to make changes."
//	  )
	}
	
	return controls;
};

// Resize called to ensure all elements are correctly positioned.
GOKb.ui.projects.prototype.resize = function() {
  var height = this._elmt.height();
  var width = this._elmt.width();
  var controlsHeight = this._elmts.controls.outerHeight();
  this._elmts.controls
		.css("width", (width - DOM.getHPaddings(this._elmts.controls)) + "px");
  
  this._elmts.projects
  	.css("height", (height - controlsHeight - DOM.getVPaddings(this._elmts.projects)) + "px");
  
};

// Push the to the action areas.
Refine.actionAreas.push({
  id: "gokb",
  label: "GOKb",
  uiClass: GOKb.ui.projects
});