/**
 * Forms object to house form methods and config.
 */
GOKb.forms = {
	defaultElems : [
    {
    	 type : 'fieldset',
    	 attr : {'class' : 'form-footer', 'bind' : 'footer'},
    	 children : [
         {type : "legend", text : "footer"},
         {
        	 type 	: "submit",
        	 name 	: "submit",
        	 value 	: "submit",
        	 attr 	: {bind : "submit"},
         },
       ]
     },
   ]
};

/**
 * Add the parameters object as a series of hidden fields to the form.
 */
GOKb.forms.paramsAsHiddenFields = function (theForm, elem, params) {
	for(var key in params) {
		
		if ($('[name="' + key + '"]', theForm).length < 1) {
			GOKb.forms.addDefinedElement(theForm, elem, {
				type : 'hidden',
				name : (key),
				value : params[key],
			});
		}
	}
};

/**
 * Build a form from a definition array.
 */
GOKb.forms.build = function(name, def, action, attr, validate) {
	
	form_def = def.concat(GOKb.forms.defaultElems);
	
	// Default attributes
	attr = $.extend({"method" : "post"}, (attr || {}));
	
	// The form element.
	var theForm = $('<form />').attr({"id" : name, "name" : (name)});
	
	var submitFunction = function (callback) {
		
		// Always store the values.
		GOKb.forms.saveValues(theForm);
		
		if (!validate || !$.isFunction(validate) || validate(theForm)) {
			
			// Check for callback.
			if (callback && $.isFunction(callback)) {
				
				// Default behaviour to deny submission as callback handles,
				// the data instead.
				return (callback() == true);
			}
			
			// We have no callback and either we don't have a validation method,
			// or validation has succeeded.
			return (callback != false);
		}
		
		// As validation failed and there is no callback method.
		return false;
	};
	
	// Add the correct submit behaviour.
	if (action == false || $.isFunction(action)) {
		// Need to add on submit...
		theForm.submit(function(e) {
			if (!submitFunction(action)) {
				e.preventDefault();
				return false;
			}
			
			return true;
		});
	} else {
		// set the action parameter..
		theForm.submit(function(e) {
			
			if (!submitFunction()) {
				e.preventDefault();
				return false;
			}
			
			return true;
		});
		theForm.attr("action", action);
	}
	
	// Add any custom attributes supplied.
	if (attr) theForm.attr(attr);
	
	// Add each form item in turn.
	$.each(form_def, function(){
		GOKb.forms.addDefinedElement(theForm, theForm, this);
	});
	
	// Add the bindings to the form.
	theForm.bindings = DOM.bind(theForm);
	
	return theForm;
};

/**
 * Add a single defined element to the parent.
 */
GOKb.forms.addDefinedElement = function (theForm, parent, def) {
	
	if (!def.name || def.name != "gokb-data") {
	
		// Add the current value to the definition.
		GOKb.forms.addSavedValue (theForm, def);
		
		// Create the form row.
		var add_to = null;
		
		// Add the element based on the def.
		var	elem, opts;
		switch (def.type) {
			case 'select' :
				elem = $("<select />");
				break;
	
			case 'legend'		:
			case 'fieldset' :
			case 'option' 	:
				add_to = parent;
			case 'textarea' :
				elem = $("<" + def.type + "/>");
				break;
		
			case 'hidden' :
				add_to = parent;
			default :
				
				// Default behaviour.
				elem = $("<input />")
					.attr ({type : def.type, value : def.value});
				break;
		}
		
		// Default to add to new div.
		if (add_to == null) {
      // Create div container for the form element.
      add_to = $('<div />')
        .attr({
          'class' : 'form-row'
        })
      ;
    }
    
    // Append the element.
    add_to.append(elem);
		
		// Check if we have a source to which we should lookup values from.
		if ("source" in def) {
		  GOKb.forms.bindDataLookup(elem, def);
		  elem.addClass("none-uniform");
		}
		
		if (def.text) elem.text(def.text);
		
		// Label first
		if (def.label) {
			add_to.append($("<label />").text(def.label).attr({
				"for" : def.name
			}));
		}
		
		if (def.value) {
			elem.attr ("value", def.value);
		}
		
		// Add any attributes.
		var attr = def.attr || {};
		if (def.name) {
			attr = $.extend(attr, {id : def.name, name : def.name})
		}
		elem.attr(attr);
		
		// If add_to different to parent then add that to the parent.
		if (add_to != parent) parent.append(add_to);
		
		// Render the current value.
		if (def.currentValue) {
			elem.val(def.currentValue);
		}
		
		// Lastly add any children this element may have.
		if (def.children) {
			$.each(def.children, function(){
				GOKb.forms.addDefinedElement(theForm, elem, this);
			});
		}
	}
};

/**
 * Bind the data lookup.
 */
GOKb.forms.bindDataLookup = function (elem, def) {
  
  // Source needs splitting
  var source = def.source.split(":");
  
  
  var format = function (result) {
//    {id: query.term + i, text: s}
    var x = result;
  };
  
  // Make this element a Select2.
  var conf = {
    placeholder: "Search for " + def.label,
    minimumInputLength: 1,
    formatResult: format,
    formatSelection: format,
    escapeMarkup: function (m) { return m; }
  };
  
  // If not a select then add a query lookup, else we need to fetch all the results first and add them all.
  var type = elem.prop('tagName');
  if (type != "SELECT") {
    
    // Add as a query.
    conf.query = function (query) {
      
      // Get the list of options.
      GOKb['get' + source[0]] (source[1], {
        onDone : function (data) {
          if ("result" in data && "datalist" in data.result) {
            query.callback(data.result.datalist);
          }
        }
      });
    };
    
    // Add the select2.
    elem.select2(conf);
  } else {
    
    // Get the list of options.
    GOKb['get' + source[0]] (source[1], {
      onDone : function (data) {
        if ("result" in data && "datalist" in data.result) {
          
          // Add each element.
          $.each(data.result.datalist, function () {
            var op = this;
            elem.append($("<option />", {
              value : (op.value),
              text  : (op.name)
            }));
          });
          
          // Add the select2 once we have finished.
          elem.select2();
        }
      }
    });
  }
};

/**
 * Get the location where form data is to be stored within this project metadata
 */
GOKb.forms.ds = null;
GOKb.forms.getDataStore = function() {
	if (GOKb.forms.ds == null) {
		if ('gokb-data' in theProject.metadata.customMetadata) {
			GOKb.forms.ds = JSON.parse(theProject.metadata.customMetadata['gokb-data']);
		} else {
			GOKb.forms.ds = {};
		}
	}
	
	// The data store object.
	return GOKb.forms.ds;
}; 

/**
 * Retrieve the value set currently in the metadata.
 */
GOKb.forms.addSavedValue = function (theForm, def) {
	var form_name = theForm.attr("name"); 
	if (def.name && def.name != "gokb-data" && form_name && def.type != "hidden") {
		
		// Special case for project name
		if (def.name == "name") {
			
			// Add the project name as the default.
			def.currentValue = theProject.metadata["name"];
			
		} else {
		
			// The data store object.
			var data_store = GOKb.forms.getDataStore();
			
			var store_id = form_name + "_" + def.name;
			
			// Try and read the object back.
			if (store_id in data_store) {
				
				// Set the currentVal.
				def.currentValue = data_store[store_id];
			}
		}
	}
};

/**
 * Save the values of the form in our data store.
 */
GOKb.forms.saveValues = function(form) {
	
	// The data store object.
	var data_store = GOKb.forms.getDataStore();
	
	// none-hidden elements. 
	$('input[type!="hidden"], select, textarea', form).each(function() {
		var store_id = form.attr('name') + "_" + $(this).attr('name');
		data_store[store_id] = $(this).val();
	});
	
	// Save to the metadata.
	GOKb.doCommand(
	  "datastore-save",
	  {},
	  {project : theProject.id, ds : JSON.stringify(data_store)},
	  {},
	  {async : false, type : "post"}
	);
};

/**
 * Return a list of option definitions for the current,
 * list of columns within the project. 
 */
GOKb.forms.getColumnsAsListOptions = function() {
	var opts = [];
	var cols = theProject.columnModel.columns;
	for (i=0; i<cols.length; i++) {
		opts.push({
			type 		: 'option',
			text 		: cols[i].name,
			value 	: cols[i].name,
		});
	}
	
	return opts;
};