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
      			
      			// Add a validation message here.
      			self.data.dataCheck.messages.push(
      			  {"facetValue":grel,"text":"One or more title was not ingested during the last ingest.","col":"publicationtitle","facetName":"Un-ingested rows","type":"warning","sub_type":"data_invalid"}
      			);
      		}
      	}
      }
    );
  }  

  // Shared params.
  var params = {
  		md 			: JSON.stringify(theProject.metadata),
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
  
  // Check the data
  var data = self.data;
  
  if ("md5Check" in data && "hashCheck" && data.md5Check) {
    if (data.md5Check.hashCheck == false) {
      
      // Add the warning.
      warnMess.push(["<span class='warning' >GOKb has detected that at this file may have been used to create another project.</span>", ""]);
    }
  }
  
  // Clear it down.
  elmts.validationContent.html("");
  
  // Modify the context of the element.
  GOKb.notify.getStack('validation').context = elmts.validationContent;
  
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
      // Add the menu.
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
    },
  });
  
  // Add the message.
  GOKb.notify.show(m, 'validation');
};