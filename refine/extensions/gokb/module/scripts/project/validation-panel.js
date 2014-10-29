/**
 * Validation panel constructor.
 * @param div
 * @param tabHeader
 */
function ValidationPanel(div, tabHeader) {
  this._div = div;
  this._tabHeader = tabHeader;
//  this.update();
}

/**
 * Resize this panel to fit it's contents.
 */
ValidationPanel.prototype.resize = function() {
  var body = this._div.find(".validation-panel-body");
  
  if (body && body.length > 0) {

	  var bodyPaddings = body.outerHeight(true) - body.height();
	  body.height((this._div.height() - bodyPaddings) + "px");
	  body[0].scrollTop = body[0].offsetHeight;
  }
};

/**
 * Update the panel data.
 * @param onDoneFunc
 */
ValidationPanel.prototype.update = function(onDoneFunc) {
  var self = this;
  
  // Clear the data.
  self.data = {
  	dataCheck : {
  		messages : []
  	}
  };
  
  // Need to check for any skipped titles.
  if ("gokb" in theProject.metadata.customMetadata && theProject.metadata.customMetadata.gokb == true) {
  	
  	// This has been associated with GOKb and will have a GOKb project id. We now need to check
  	// for skipped titles.
  	var id = theProject.metadata.customMetadata['gokb-id'];
  	GOKb.doCommand (
      "checkSkippedTitles",
      {project: id},
      null,
      {
      	onDone : function (data) {
      		
      		if ("result" in data && data.result.length > 0) {
      			// (cells['publicationtitle'].value + cells['package.name'].value).match('\\QAfrican and Asian StudiesBrill:Master:2013\\E|\\QAfrican DiasporaBrill:Master:2013\\E') != null
      			var grel = "(cells[gokbCaseInsensitiveCellLookup('publicationtitle')].value + cells[gokbCaseInsensitiveCellLookup('package.name')].value).match('\\\\Q";
      			for (var i=0; i<data.result.length;i++) {
      				grel += (i > 0 ? '\\\\E|\\\\Q' : "") + data.result[i];
      			}
      			
      			// Close the statement.
      			grel += "\\\\E') != null";
      			
      			self.data.dataCheck.messages.push({
              type        : "notice",
              title       : "Source file check",
              text        : "One or more title was not ingested during the last ingest.",
              facetValue  : grel,
              col         : "publicationtitle",
              facetName   : "Un-ingested rows",
              type        : "notice",
              sub_type    : "data_invalid"
            });
      		}
      	}
      }
    );
  }  

  // Shared params.
  var params = {
  		hash		: JSON.stringify(theProject.metadata.customMetadata.hash),
  		project : theProject.id
  };
  
  // Check the MD5.
  GOKb.doCommand (
    "checkMD5",
    params,
    null,
    {
    	onDone : function (data) {
    		
    		if ("result" in data) {
    			
    			// Set the md5 part of the data.
    			self.data["md5Check"] = data.result;
    			
    			// Post the column data to the service.
    		  GOKb.doCommand (
    		    "project-validate",
    		    params,
    		    null,
    		    {
    		    	onDone : function (data) {
    		    		
    		    		if ("result" in data && "status" in data.result) {
    		    			
    		    			// Merge the results into the existing object.
    		    			$.merge( self.data.dataCheck.messages, data.result.messages );
    		    			
    		    		  // Then render.
    		    		  self._render();
    		    		}
    		    		
    		    		if (onDoneFunc) {
    		    			onDoneFunc();
    		    		}
    		    	}
    		  	}
    		  );
    		}
    	}
  	}
  );
};

/**
 * Render this panel.
 */
ValidationPanel.prototype._render = function() {
  var self = this;

  // Reload the HTML 
  self._div.empty().unbind().html(DOM.loadHTML("gokb", "scripts/project/validation-panel.html"));

  // Bind the elements.
  var elmts = DOM.bind(this._div);
  
  // Clear it down.
  elmts.validationContent.html("");
  
  // Modify the context of the element.
  GOKb.notify.getStack('validation').context = elmts.validationContent;
  
  // Check the data
  var data = self.data;
  
  if ("md5Check" in data && "hashCheck" && data.md5Check) {
    if (data.md5Check.hashCheck == false) {
      self.showMessage({
        type  : "notice",
        title : "Source file check",
        text  : "GOKb has detected that this file may have been used to create another project."
      });
    }
  }
    
  var errors = 0, warnings = 0;
  
  if ("dataCheck" in data) {
    if ("messages" in data.dataCheck) {
      
      // Handle the errors first.
      $.each(data.dataCheck.messages, function() {
        
        // Push to the stack.
        if (this.type == 'error') {
          self.showMessage(this);
          errors ++;
        }
      });
      
      // If we have no errors at this point then we can add the ingest note.
      if (errors == 0) {
        
        // Show a message allowing ingest.
        var ingest_note = {
          title : "Project valid",
          noMenu : true
        };
        
        if (data.dataCheck.messages.length > 0) {
          // Warnings in project.
          $.extend(ingest_note, {
            type: "info",
            text: "There are warnings for this project, but these will not stop you from continuing to update GOKb with the data in this project."
          });
        } else {
          // No warnings.
          $.extend(ingest_note, {
            type: "success",
            text: "There are no warnings or errors for this project. You can now update GOKb with the data in this project."
          });
        }
        
        // Add the confirmation buttons to trigger an ingest.
        ingest_note.confirm = {
          confirm: true,
          buttons: [{
            text: 'Update GOKb',
            addClass: 'button',
            click: function(notice) {
              // Modify the notice to prevent further clicking.
//              notice.update({
//                text: 'Beginning update process...',
//                confirm: {
//                  confirm: false
//                }
//              });
              
              // Fire the data change estimates.
              GOKb.handlers.estimateChanges(true);
            }
          }]
        };
        
        // Fix issue with only setting 1 button.
        ingest_note.before_init = function(opts) {
          // Remove the last element.
          opts.confirm.buttons = opts.confirm.buttons.splice(0,opts.confirm.buttons.length - 1);
        };
        
        // Show the notification now.
        self.showMessage(ingest_note);
      }
      
      // Handle the warnings next.
      $.each(data.dataCheck.messages, function() {
        
        // Push to the stack.
//        if (this.type != 'error') {
//          self.showMessage($.extend(this, {
//            buttons: {
//              closer    : true,
//              sticker   : false,
//            }
//          }));
//        }
        
        if (this.type != 'error') {
          self.showMessage(this);
          warnings ++;
        }
      });
    }
  }
  
  // Add the counters.
  $("#gokb-validation-tab .count").html("<span class='error'>" + errors + "</span>/<span class='warning'>" + warnings + "</span>");
};

/**
 * Show a single message on the validation panel. 
 * @param message
 */
ValidationPanel.prototype.showMessage = function (message) {
  // The link to display the menu.
  
  // Add the defaults to the message.
  var m = $.extend({}, message, {
    title: message.col,
    before_open : function (notice) {
      // Add the menu if needed.
      
      if (!("noMenu" in message) || !message.noMenu) {
        var menuLink = $("<div class='gokb-message-actions' />").append(
          $("<span class='ui-icon ui-icon-wrench' title='Menu'></span>")
          
        ).click(function() {
          ValidationPanel.messages.getActions(message, $(this));
          
        }).hide();
        
        notice.container.prepend(menuLink);
        
        // Add the mouseover listener.
        notice.elem.on({
          "mouseenter": function(e){
            // Show the button.
            menuLink.show();
          },
          "mouseleave": function(e){
            // Show the button.
            menuLink.hide();
          }
        });
      }
    }
  });
  
  // Add the message to the validation stack..
  GOKb.notify.show(m, 'validation');
};