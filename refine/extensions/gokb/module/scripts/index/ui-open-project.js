// Create GOKb Project UI

GOKb.ui.projects = function (elmt) {
  var self = this;
	elmt.html(DOM.loadHTML("gokb", "scripts/index/ui-open-project.html"));
	this._elmt = elmt;
  this._elmts = DOM.bind(elmt);
  this._localProjects = {};
  
  $(document).ready(function(){
    
    // Testing.
    GOKb.doRefineCommand(
       "core/get-all-project-metadata",
       {},
       null,
       {
         onDone : function (localProjects) {
           
           if ("projects" in localProjects) self._localProjects = localProjects.projects;
           
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
                      
                      // Need to remove the links from the normal open-project tab.
                      for (var i = 0; i < Refine.actionAreas.length; i++) {
                        var actionArea = Refine.actionAreas[i];
                        if ("open-project" == actionArea.id) {
                          $('a[href*="' + this.localProjectID + '"]', actionArea.bodyElmt).each(function() {
                            var row = $(this).closest("tr");
                            var firstCell = row.children(":first");
                            firstCell.html("");
                            
                            // Remove all secondary controls too!
                            $('a.secondary', row).remove();
                            
                            // Add a rollover.
                            row.attr("title", "This is a GOKb project and can be managed through the GOKb tab.");
                          });
                        }
                      }
                      
                      var name = this.name;
                      if (self.isLocalProject(this)) {
                        // Name need to link to current local project.
                        name = $('<a />')
                          .attr('href', '/project?project=' + this.localProjectID)
                          .text(name)
                          .attr('title', 'Open project to make changes.')
                        ;
                      }
                      
                      var status = $('<span />').attr("id", "projectStatus" + this.id).attr("ref", this.id);
                      
                      // Set the status.
                      self.setStatus (status, this);
                      
                      // Add the row.
                      var row = [
                        self.getProjectControls(this),
                        name,
                        this.description,
                        status,
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
         }
       }
    );
    
    // Do the update regularly.
    self.regularlyUpdate(self);
    
    self.populateWorkspaces(self._elmts);
  });
};

// Load the available workspaces from refine.
GOKb.ui.projects.prototype.populateWorkspaces = function (elems) {
  
  // The list to which we are going to append.
  var list = $(elems.workspaces);
  
  // Clear the list first.
  list.empty();
  
  // Go through each of the workspaces.
  for (var i=0; i<GOKb.workspaces.length; i++) {
    
    // Selected?
    var selected = GOKb.current_ws == i;
    
    // Workspace.
    var ws = $("<option />", {
        value: i,
        text: GOKb.workspaces[i].name
      })
      
      // Set selected if the current value is set.
      .prop("selected", selected)
      .prop("disabled", !GOKb.workspaces[i].available)
    ;
    
    // Add each workspace to the list.
    list.append(
      ws
    );
  }
  
  // Apply the uniform plugin.
  list.uniform();
  
  // Add the onchange handler for the dropdown now.
  list.change(function(e){
    
    // Get the selected value.
    var val = $(this[this.selectedIndex]).val();
    
    GOKb.doCommand(
      "set-active-workspace",
      {ws: val},
      {},
      {
        onDone : function () {
          window.location.reload(true);
        }
      }
    );
  });
};

// Return a control link.
GOKb.ui.projects.prototype.createControlLink = function (project, loc, text, title) {
	return $('<a></a>')
		.attr("title",(title ? title : text))
		.attr("href", loc)
		.attr("rel", project.id)
		.addClass("control")
		.html(text)
	;
};

/**
 * Sets the status of the project based on the projData
 */
GOKb.ui.projects.prototype.setStatus = function (statusElem, projData) {
	var person = "Unknown";
	if ("lastCheckedOutBy" in projData && "displayName" in projData.lastCheckedOutBy) {
		person = projData.lastCheckedOutBy.displayName;
	}
	
	if ("lastCheckedOutBy" in projData && "email" in projData.lastCheckedOutBy) {
		person = '<a href="mailto:' + projData.lastCheckedOutBy.email + '">' + person + '</a>';
	}
	
	switch (projData.projectStatus.name) {
		case 'CHECKED_IN' :
			statusElem.html("Checked In (last checked out by " + person + ")");
			break;
			
		case 'CHECKED_OUT' :
			statusElem.html("Checked Out by " + person);
			break;
			
		case 'INGESTING' :
			
			// Set the class so we know to update this status.
			statusElem.addClass("ingesting");
			
			if ("progress" in projData && projData.progress != null) {
				// Being ingested...
				statusElem.html("Ingesting (" + projData.progress + "%)");
			} else {
				statusElem.html("Ingesting (progress unknown)");
			}
			break;
			
		case 'INGESTED' :
			statusElem.removeClass("ingesting");
			statusElem.html("Ingested by " + person + ".");
			break;
			
		case 'INGEST_FAILED' :
			statusElem.removeClass("ingesting");
			statusElem.html("Ingest attempted by " + person + " has failed");
			break;
			
		case 'PARTIALLY_INGESTED' :
			statusElem.removeClass("ingesting");
			statusElem.html("Partially Ingested by " + person);
		break;
	}
};

// Update the status of the project.
GOKb.ui.projects.prototype.updateStatus = function (statusElem) {
	var self = this;
	var status = $(statusElem);
	var id = status.attr('ref');
	GOKb.doCommand("projectIngestProgress", {projectID : id}, null, {
		onDone : function(data) {
			if ("result" in data && data.result.length == 1) {
				var project = data.result[0];
				self.setStatus (status, project);
				self.getProjectControls (project);
			}
		}
	});
};

// Check to see if the supplied GOKb project matches a local project.
// In other words is this project checked out by teh current user?
GOKb.ui.projects.prototype.isLocalProject = function(project) {
	return (project.localProjectID && project.localProjectID != 0 && this._localProjects[project.localProjectID] && this._localProjects[project.localProjectID].customMetadata["gokb-id"] == project.id);
};

GOKb.ui.projects.prototype.getProjectControls = function(project) {
	
	var controls = $('span.proj-controls-' + project.id);
	if (!controls.length) {
		controls = $('<span />').attr('class', 'proj-controls-' + project.id);
	}
	
	// Clear the controls first.
	controls.html("");
	
	var self = this;
	
	switch (project.projectStatus.name) {
		case 'CHECKED_IN' :
		case 'INGESTED' :
		case 'INGEST_FAILED' :
		case 'PARTIALLY_INGESTED' :
			controls.append (
			  this.createControlLink(
			    project,
			    'command/gokb/project-checkout?projectID=' + project.id,
			    "check&#45;out",
			    "Checkout this project from GOKb to work on it."
			  )
			);
			break;
		case 'CHECKED_OUT' :
			
			// Check if local project matches this project.
			if (self.isLocalProject(project)) {
				
				// Get project params...
				var theProject = self._localProjects[project.localProjectID];
				var params = {
			  	project 		: project.localProjectID,
			  	projectID		: project.id,
			  };
				
				// Check in link.
				controls.append(
				  this.createControlLink(
						project,
						'command/gokb/project-checkin?' + $.param($.extend({update : true, name	: theProject.name}, params)),
						"check&#45;in",
						"Check the current project into GOKb along with any changes that you have made."
				  )
				  
				).append(
	 			  $("<span>&nbsp;&nbsp;&nbsp;</span>")
	 			  
	 			).append(
				  this.createControlLink(
						project,
						'command/gokb/project-checkin?' + $.param(params),
						"cancel",
						"Check the current project into GOKb, but ignore any changes made."
				  )
				);
				
				
			}
			break;
		case 'INGESTING' :
			/** No functions **/
			break;
	}
	
	return controls;
};

/**
 * Check for and update statuses that require it.
 */
GOKb.ui.projects.prototype.regularlyUpdate = function (projArea) {
	
	$('.ingesting', projArea._elmts.projects).each(function() {
		projArea.updateStatus(this);
  });
	
	// Rerun the method every 5 seconds.
	setTimeout(
	  function() {
	  	// Do the update.
	  	projArea.regularlyUpdate(projArea);
	  },
	  5000
	);
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
