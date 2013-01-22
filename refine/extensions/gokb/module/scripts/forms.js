/**
 * Add the parameters object as a series of hidden fields to the form.
 */
GOKb.paramsAsHiddenFields = function (form, params) {
	for(var key in params) {
		form.append(
		  $("<input />")
		    .attr('type', 'hidden')
		    .attr('name', key)
		    .attr('value', params[key])
		);
	}
};

/**
 * Build a for from a definition array.
 */
GOKb.buildForm = function(definition) {
	
	
};