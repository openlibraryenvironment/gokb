/**
 * Validation panel constructor.
 */	
function ValidationPanel(div, tabHeader) {
  this._div = div;
  this._tabHeader = tabHeader;
  this.update();
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
  if ("status" in data) {
  	if (!data.status) {
  		// invalid doc.
  		var tData = [];
  		if ("messages" in data) {
  			$.each(data.messages, function() {
  				
  				// Get the message.
  				var message = this;
  				
  				// The link to display the menu.
  				var menuLink = $("<a class='button' href='javascript:{}' ><img src='images/right-arrow.png'></a>")
  					.appendTo($("<div class='gokb-message-actions' />"))
  					.click(function() {
  						ValidationPanel.messages.getActions(message, $(this));
  					});
  				;
  				
  				// Push the data to the table.
  				tData.push([message.text, menuLink]);
  			});
  			
  			var table = GOKb.toTable (
  			  ["Error messages", ""],
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
			  				GOKb.handlers.checkInWithProps({ingest : true});
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

/**
 * Messages namespace for validation panel.
 */
ValidationPanel.messages = {};

/**
 * Quick Resolution - Rename
 */
ValidationPanel.messages.renameColumn = function (message) {
	
	// Show a list of all the columns.
	var opts = [];
	var cols = theProject.columnModel.columns;
	for (i=0; i<cols.length; i++) {
		var col = cols[i];
		var index = col.cellIndex;
		var renameCol = function () {
			if (message !== null && message.col) {
	      Refine.postCoreProcess(
	        "rename-column", 
	        {
	          oldColumnName: cols[index].name,
	          newColumnName: message.col
	        },
	        null,
	        { modelsChanged: true }
	      );
	    }
		};
		opts.push({
			id 		: "col-list-" + index,
			label : col.name,
			click : renameCol
		});
	}
	
	return opts;
};

/**
 * Get quick resolution options.
 */
ValidationPanel.messages.getQR = function (message) {
	
	var opts = [];
	
	switch (message.type) {
		case 'missing_column' :
			
			// Suggest adding column or renaming column.
			opts = opts.concat (
			  [
			   	{
			   		id 		: message.type + "rename",
			   		label : "Rename a column",
			   		submenu: ValidationPanel.messages.renameColumn(message),
			    },
			    {
			   		id 		: message.type + "create",
			   		label : "Create a column",
			    },
			  ]
			);
			
			break;
	}
	
	return opts;
}

/**
 * Get the actions menu for the message supplied.
 */
ValidationPanel.messages.getActions = function (message, elem) {
	
	// Create the menu to show.
	var menu = [];
	
	// Check for quick resolve links
	var qr = ValidationPanel.messages.getQR(message);
	if (qr.length > 0) {
		menu.push({
	  	"id" : "message-quick-reolution",
	  	label: "Quick Resolution",
      submenu: qr,
	  });
	}
	
	// Do the actual menu creation.
	MenuSystem.createAndShowStandardMenu(
	  menu,
	  elem,
	  { width: "120px", horizontal: false }
	);
};