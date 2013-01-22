function ValidationPanel(div, tabHeader) {
  this._div = div;
  this._tabHeader = tabHeader;
  this.update();
}

ValidationPanel.prototype.resize = function() {
  var body = this._div.find(".validation-panel-body");
  
  if (body && body.length > 0) {

	  var bodyPaddings = body.outerHeight(true) - body.height();
	  body.height((this._div.height() - bodyPaddings) + "px");
	  body[0].scrollTop = body[0].offsetHeight;
  }
};

ValidationPanel.prototype.update = function(onDoneFunc) {
  var self = this;
  
  // Set the _data attribute to the data.
  var params = {"project" : theProject.id};
	
  // Post the columns to the service
  GOKb.doCommand (
    "project-validate",
    params,
    null,
    {
    	onDone : function (data) {
    		
    		if ("result" in data && "status" in data.result) {
    			
    			self.data = data.result;
    			
    		  // Then render.
    		  self._render();
    		}
    		
    		if (onDoneFunc) {
    			onDoneFunc();
    		}
    	}
  	}
  );
};

ValidationPanel.prototype._render = function() {
  var self = this;

  // Reload the HTML 
  self._div.empty().unbind().html(DOM.loadHTML("gokb", "scripts/project/validation-panel.html"));

  // Bind the elements.
  var elmts = DOM.bind(this._div);
  
  // Check the data
  var data = self.data;
  if ("status" in data) {
  	if (!data.status) {
  		// invalid doc.
  		var tData = [];
  		if ("messages" in data) {
  			$.each(data.messages, function() {
  				tData.push([this.text]);
  			});
  			
  			var table = GOKb.toTable (
  			  ["Error messages"],
  			  tData
  			);
  			
  			// Set the header error count to the correct number. 
  			self._tabHeader.html('Errors <span class="error count">' + tData.length + '</span>');
  			
  			// Append the table to the dialog...
  			elmts.validationContent.html(table);
  			$('h1', elmts.panelContent).hide();
  		}
  	} else {
  		
			// Set the header error count to the correct number.
  		self._tabHeader.html('Errors <span class="count">0</span>');
  		elmts.validationContent
  			.html("<p>The current project has passed all validation rules.</p>")
  			.append(
  			  $('<p />').append(
			  		$('<a />')
			  			.attr("href", "#")
			  			.text("Begin ingest process")
			  			.click(function() {
			  				var dialog = GOKb.handlers.checkInWithProps();
			  				dialog.bindings.submit.attr("value", "Save and begin ingest");
			  				alert (JSON.stringify(theProject));
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