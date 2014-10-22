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
	
	// Add the action if it is not a function or false.
	if (action != false && !$.isFunction(action)) {
	  theForm.attr("action", action);
	}
	
	var submitFunction = function (callback) {
	  
	  // The deferred ovbject to listen for when form submission has succeeded.
	  var listener = $.Deferred();
		
		// Always store the values.
		var saving = GOKb.forms.saveValues(theForm);
		
		// Saving flag, is a deferred object.
		saving.done(function() {
		
		  // Successfully saved values in local storage.
  		if (!validate || !$.isFunction(validate) || validate(theForm)) {
  			
  			// Check for callback.
  			if (callback && $.isFunction(callback) && callback() == true) {
  				
  				// Default behaviour to deny submission as callback handles,
  				// the data instead.
  				listener.resolveWith(theForm);
  				
  			} else if (callback != false) {
  			  listener.resolveWith(theForm);
  			} else {
  			  listener.rejectWith(theForm);
  			}
  		} else {
  		  
  	    // As validation failed and there is no callback method.
        listener.rejectWith(theForm);
  		}
		});
		
		return listener;
	};
	
  // Create a new submit handler that submits the form via AJAX.
  var submitHandler = function(e) {
    
    var listener;
    
    // Always prevent the default. as we will do the submission in the background.
	  e.preventDefault();
	  
    if (action == false || $.isFunction(action)) {
      listener = submitFunction(action);
    } else {
      listener = submitFunction();
    }
    
    // Append the callbacks.
    listener.done(function(form) {
      
      var f = $(form);
      var method = f.attr("method");
      var action = f.attr("action");
      
      // Form needs to be submitted.
      $[method](action, f.serialize(), function(data){
        // Show something of success or failure.
        window.console.log(data);A                                                                                                                                  
      });
      
    });
    listener.fail(function(form) {
      // Do not submit the form.
      window.console.log("Form wasn't submitted");
    });
  };
  
	// Need to add on submit...
	theForm.submit(submitHandler);
	
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
  
  // Make this element a Select2.
  var conf = {
    placeholder         : (def.create ? "Add/" : "") + "Select a " + def.label,
    minimumInputLength  : 1,
    selectOnBlur        : true,
    escapeMarkup        : function (m) { return m; },
    id                  : function (object) { return object.value; },
    initSelection       : function (element, callback) {
      var data = {id: element.val(), text: element.val()};
      callback(data);
    }
  };
  
  // If not a select then add a query lookup, else we need to fetch all the results first and add them all.
  var type = elem.prop('tagName');
  if (type != "SELECT") {
    
    // Result formatter.
    var formatResult = function(result, label, query) {
      
      // The text.
      var text;
      var suffix = "0000}";
      if (query.term && "value" in result && result.value.indexOf(suffix, this.length - suffix.length) === -1) {
        
        // Highlight within the label the matched area.
        var highlight = new RegExp('(' + RegExp.escape(query.term) + ')', "i");
        text = result.label.replace(highlight, "<span class='select2-match' >$1</span>");
        
      } else {
        
        // Either a group or the current typed text. Just return the label.
        text = result.label;
      }
      
      return text;
    };
    
    // Set the formatters.
    conf.formatResult = formatResult;
    conf.formatSelection = formatResult;
    
    // Variable to hold the timeout method, to wait for
    // a timeout after the user stops typing.
    var toMethod = null;
    
    // Add as a query.
    conf.query = function (query) {
      
      // Cancel if we have a waiting query.
      if (toMethod != null) {
        clearTimeout(toMethod);
      }
      
      toMethod = setTimeout(function () {
        GOKb['get' + source[0]] (
          query.term,
          {
            "type" : source[1],
            "page" : query.page
          },
          {
            onDone : function (data) {
              
              // We also add the current value to the top of the list to allow for the,
              // current value to be added.
              var res = {
                results: []
              };
              
              if (def.create && query.page == 1) {
                res.results.push({value: (query.term + "::{" + source[1] + ":0000}"), label: (query.term)});
              }
              
              if (data && "list" in data && data.list.length > 0) {
                
                // Add more if we have more results that we can fetch.
                res.more = ((query.page * 10) < data.total);
                
                // Push the results list to the response.
                res.results = $.merge(res.results, data.list);
              }
              
              // Do the callback.
              query.callback( res );
            }
          }
        );
        toMethod = null;
      }, 1000);
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
	return GOKb.doCommand(
	  "datastore-save",
	  {},
	  {project : theProject.id, ds : JSON.stringify(data_store),},
	  {},
	  {type : "post",}
	);
};

/**
 * Return a list of option definitions for the current,
 * list of columns within the project. 
 */
GOKb.forms.getColumnsAsListOptions = function() {
	var opts = [];
	var cols = theProject.columnModel.columns;
	for (var i=0; i<cols.length; i++) {
		opts.push({
			type 		: 'option',
			text 		: cols[i].name,
			value 	: cols[i].name,
		});
	}
	
	return opts;
};