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
        	 type : "submit",
        	 name : "submit",
        	 value : "submit",
        	 attr : {bind : "submit"},
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
		GOKb.forms.addDefinedElement(theForm, elem, {
			type : 'hidden',
			name : (key),
			value : params[key],
		});
	}
};

/**
 * Build a for from a definition array.
 */
GOKb.forms.build = function(name, def, action, attr, validate) {
	
	form_def = def.concat(GOKb.forms.defaultElems);
	
	// Default attributes
	attr = $.extend({"method" : "post"}, (attr || {}));
	
	// The form element.
	var theForm = $('<form />').attr({"id" : name, "name" : (name)});
	
	var submitFunction = function (callback) {
		if (!validate || !$.isFunction(validate) || validate(theForm)) {
			
			// Check for callback.
			if (callback && $.isFunction(callback)) {
				
				// Default behaviour to deny submission as callback handles,
				// the data instead.
				return (callback() == true);
			}
			
			// We have no callback and either we don't have a validation method,
			// or validation has succeeded.
			return true;
		}
		
		// As validation failed and there is no callback method.
		return false;
	};
	
	// Add the correct submit behaviour.
	if ($.isFunction(action)) {
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
	
	// Add the current value to the definition.
	GOKb.forms.addSavedValue (theForm, def);
	
	// Create the form row.
	var add_to = $('<div />')
		.attr({
			'class' : 'form-row'
		})
	;
	
	// Add the element based on the def.
	var	elem, opts;
	switch (def.type) {
		case 'refdata' :
			
			// Create the select element.
			elem = $("<select />");
			
			// Bind the refdata to the dropdown.
			GOKb.getRefData ("cp", {
				onDone : function (data) {
					if ("result" in data && "datalist" in data.result) {
						$.each(data.result.datalist, function (value, display) {
							var opt = $('<option />', {"value" : value})
								.text(display)
							;
							
							// Append the arguments...
							elem.append(
							  opt
							);
						});
						
						// Select the current one...
						if (def.currentValue) {
							elem.val(def.currentValue);
						}
					}
				}
			});
			break;
		case 'select' :
			elem = $("<select />");
			break;

		case 'legend'		:
		case 'fieldset' :
			add_to = parent;
		case 'textarea' :
			elem = $("<" + def.type + " />");
			break;
	
	
		default :
			
			// Default behaviour.
			elem = $("<input />")
				.attr ({type : def.type, value : def.value});
			break;
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
	elem.attr($.extend((def.attr || {}), {id : def.name, name : def.name}));
	
	// Append the element.
	add_to.append(elem);
	
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
};

GOKb.forms.setCurrentValue = function (elem, def) {
	switch (def.type) {
		case 'refdata' :
			
		case 'select' :
			elem = $("<select />");
		break;
	
		case 'legend'		:
		case 'fieldset' :
			add_to = parent;
		case 'textarea' :
			elem = $("<" + def.type + " />");
		break;
	
	
		default :
			
			// Default behaviour.
			elem = $("<input />")
				.attr ({type : def.type, value : def.value});
		break;
	}
};

/**
 * Retrieve the value set currently in the metadata.
 */
GOKb.forms.addSavedValue = function (theForm, def) {
	var form_name = theForm.attr("name"); 
	if (def.name && form_name) {
		// Set the metadata object if not already present.
		if (!('gokb-data' in theProject.metadata.customMetadata)) {
			theProject.metadata.customMetadata.gokb-data = {};
		}
		
		// The data store object.
		var data_store = theProject.metadata.customMetadata.gokb-data;
		
		var store_id = form_name + "-_-" + def.name;
		
		// Try and read the object back.
		if (store_id in data_store) {
			
			// Set the currentVal.
			def.currentVal = data_store[store_id];
		}
	}
}