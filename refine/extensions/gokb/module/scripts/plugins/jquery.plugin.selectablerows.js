(function( $ ){

  $.fn.selectableRows = function(options) {
  	
  	var methods = {
  		init			:	function (options) {
  			
  			// Defaults
  	    var settings = $.extend( {
  	      'checkAll'		: 	true
  	    }, options);

  	    return this.each(function() {

  	    	var table = this;
  	    	
  	    	// Add a blank header column for our checkbox
  	    	var allCell = $("<th />").prependTo($("thead tr", table));
  	    	
  	    	// Add the all checkbox if required
  	    	if (settings.checkAll) {
  	    		allCell.append(
  	    	    $('<input type="checkbox" value="all" />').change(function() {
  	    	    	
  	    	    	// Check/uncheck all boxes.
  	    	    	var cb = $(this);
  	    	    	$("td.cb-cell input[type=checkbox]", table).attr("checked", cb.is(":checked"));
  	    	    })
  	    	  );
  	    	}
  	    	
  	    	// Add a checkbox to each column of our table
  	    	var count = 0;
  	    	$("tbody tr", table).each(function() {
  	    		
  	    		// Add the onclick to every cell currently in this row
  	    		$("td", this).click(function() {
  	    			// Add an onClick listener to toggle current row checkbox
  	    			var cb = $(".cb-cell input[type=checkbox]", $(this).parent());
  	    			cb.trigger('click');
  	    		});
  	    		
  	    		$(this).prepend(
  	    		  // Prepend the checkbox
  	    		  $('<td class="cb-cell" />').append(
  	    		     $('<input type="checkbox" />').val(count)
  	    		  )
  	    		)
  	    		count++;
  	    	});
  	    });
  		},
  		
  		// Return the values of each selected item within this table
  		getSelected	: function( options ) {
				var indexes = [];
				$(".cb-cell input[type=checkbox]:checked", this).each(function() {
					indexes.push($(this).val());
				});
				
				// Return the checked indeces.
				return indexes;
			}
		};
  	
  	// Method calling logic
  	if ( methods[options] ) {
  		
  		// Method name past in and is present in method array, so run the method.
  	  return methods[ options ].apply( this, Array.prototype.slice.call( arguments, 1 ));
  	} else if ( typeof options === 'object' || ! options ) {
  		
  		// Just a regular object past in so run init method.
  	  return methods.init.apply( this, arguments );
  	  
  	} else {
  		
  		// No match so throw an error.
  	  $.error( 'Method ' +  options + ' is not a selectableRows method.' );
	  }
  };
})( jQuery );