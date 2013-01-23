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
GOKb.forms.paramsAsHiddenFields = function (elem, params) {
	for(var key in params) {
		GOKb.forms.addDefinedElement(elem, {
			type : 'hidden',
			name : (key),
			value : params[key],
		});
	}
};

/**
 * Build a for from a definition array.
 */
GOKb.forms.build = function(def, action, attr, validate) {
	
	form_def = def.concat(GOKb.forms.defaultElems);
	
	// Default attributes
	attr = $.extend({"bind" : "form"}, (attr || {}));
	
	// The form element.
	var theForm = $('<form />');
	
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
		GOKb.forms.addDefinedElement(theForm, this);
	});
	
	return theForm;
};

/**
 * Add a single defined element to the parent.
 */
GOKb.forms.addDefinedElement = function (parent, def) {
	
	// Create the form row.
	var add_to = $('<div />')
		.attr({
			'class' : 'form-row'
		})
	;
	
	// Add the element based on the def.
	var	elem;
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
	
	// Lastly add any children this element may have.
	if (def.children) {
		$.each(def.children, function(){
			GOKb.forms.addDefinedElement(elem, this);
		});
	}
};