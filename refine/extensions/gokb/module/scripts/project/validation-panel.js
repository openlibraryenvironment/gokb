/**
 * Validation panel constructor.
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
//    		    			$.extend (true, self.data.dataCheck, data.result)
    		    			$.merge( self.data.dataCheck.messages, data.result.messages )
//    		    			self.data["dataCheck"] = data.result;
    		    			
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
	
	// Add the errors and warnings.
	var errorMess = [];
	var warnMess = [];
	
	if ("md5Check" in data && "hashCheck" && data.md5Check) {
		if (data.md5Check.hashCheck == false) {
			
			// Add the warning.
			warnMess.push(["<span class='warning' >GOKb has detected that at this file may have been used to create another project.</span>", ""]);
		}
	}
  
  if ("dataCheck" in data) {
		
		if ("messages" in data.dataCheck) {
			
			// hasError.
			var hasError = false;
			
			$.each(data.dataCheck.messages, function() {
				
				// Get the message.
				var message = this;
				
				// The link to display the menu.
				var menuLink = $("<a class='button' href='javascript:{}' ><img src='images/right-arrow.png'></a>")
					.appendTo($("<div class='gokb-message-actions' />"))
					.click(function() {
						ValidationPanel.messages.getActions(message, $(this));
					});
				;
				
				if (message.type == "error") {
					// Push the data to the error table.
					errorMess.push(["<span class='error' >" + message.text + "</span>", menuLink]);
				} else {
					// Push the data to the error table.
					warnMess.push(["<span class='warning' >" + message.text + "</span>", menuLink]);
				}
				
			});
			
			// Set the header error count to the correct number. 
			self._tabHeader.html('Errors <span class="error count">' + errorMess.length + '</span> / <span class="warning count">' + warnMess.length + '</span>');
			
			// Clear the HTML first.
			elmts.validationContent.html("");
			
			// Append the table to the dialog...
			if (errorMess.length > 0) {
				
				// Error message table.
				var errorMessages = GOKb.toTable (
				  ["<span class='error' >Error messages</span>", ""],
				  errorMess
				).addClass("error");
				
				// Add the table.
				elmts.validationContent.append(errorMessages);
				hasError = true;
			}
			if (warnMess.length > 0) {

				// Warning message table.
				var warnMessages = GOKb.toTable (
	 			  ["<span class='warning' >Warning messages</span>", ""],
				  warnMess
				).addClass("warning");
				
				// Add the table.
				elmts.validationContent.append(warnMessages);
				hasError = true;
			}
		}
  	
  	if (hasError) {
			$('h1', elmts.panelContent).hide();
			
			if (errorMess.length == 0) {
				
				// There must only be warnings. We still need to allow the ingest to take place.
	  		elmts.validationContent
					.append($("<h1 />")
						.text("GOKb Validation Status"))
					.append($("<p />")
						.text("There are warnings against your project currently but these will not stop you from continuing to ingest."))
					.append("<p>To update any existing packages in GOKb with data in this file you " +
  			      " can choose to 'Update packages'</p>")
	  			.append(
	  			  $("<div>").attr("id", "gokb-ingest-button").append(
				  		$('<button />')
				  			.addClass("button")
				  			.text("Update packages")
				  			.click(function() {
				  				GOKb.handlers.estimateChanges(true);
				  			})
				  	)
			  	)
//			  	.append("<h1>Replacement update</h1><p>If you would like to retire existing packages "+
//			  	        "and create new ones based on this data then choose 'Replace packages'</p>")
//	  			.append(
//	  			  $("<div>").attr("id", "gokb-ingest-button").append(
//				  		$('<button />')
//				  			.addClass("button")
//				  			.text("Replace packages")
//				  			.click(function() {
//				  				GOKb.handlers.estimateChanges(false);
//				  			})
//				  	)
//			  	)
				;
  		}
  	} else {
  		
			// Set the header error count to the correct number.
  		self._tabHeader.html('Errors <span class="error count">0</span> / <span class="warning count">0</span>');
  		
  		elmts.validationContent
  			.html("<p>The current project has passed all validation rules. You now have 2 " +
  			      "choices of how to handle the data in this project.</p>")
  			.append("<h1>Incremental update</h1><p>To update any existing packages in GOKb with data in this file you " +
  			      " can choose to 'Update packages'</p>")
  			.append(
  			  $("<div>").attr("id", "gokb-ingest-button").append(
			  		$('<button />')
			  			.addClass("button")
			  			.text("Update packages")
			  			.click(function() {
			  				GOKb.handlers.estimateChanges(true);
			  			})
			  	)
		  	)
		  	.append("<h1>Replacement update</h1><p>If you would like to retire existing packages "+
		  	        "and create new ones based on this data then choose 'Replace packages'</p>")
  			.append(
  			  $("<div>").attr("id", "gokb-ingest-button").append(
			  		$('<button />')
			  			.addClass("button")
			  			.text("Replace packages")
			  			.click(function() {
			  				GOKb.handlers.estimateChanges(false);
			  			})
			  	)
		  	)
  		;
  		$('h1', elmts.panelContent).show();
  	}
  } else {
  	self._tabHeader.html('Errors <span class="count error">0</span>');
		elmts.validationContent.html("<p>Unable to contact the GOKb service for validation information..</p>");
  }

  // Resize the panel.
  this.resize();
};