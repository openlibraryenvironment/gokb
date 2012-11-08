(function( $ ){

  $.fn.selectableRows = function( options ) {  

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
    	    $('<input type="checkbox" id="gokbAllSelected" value="1" />').change(function() {
    	    	
    	    	// Check/uncheck all boxes.
    	    	var cb = $(this);
    	    	$("td.cb-cell input", table).attr("checked", cb.is(":checked"));
    	    })
    	  );
    	}
    	
    	// Add a checkbox to each column of our table
    	var count = 0;
    	$("tbody tr", table).each(function() {
    		
    		// Add the onclick to every cell currently in this row
    		$("td", this).click(function() {
    			// Add an onClick listener to toggle current row checkbox
    			var cb = $(".cb-cell input", $(this).parent());
    			cb.attr("checked", !cb.is(":checked"));
    		});
    		
    		$(this).prepend(
    		  // Prepend the checkbox
    		  $('<td class="cb-cell" />').append(
    		     $('<input type="checkbox" id="selectedRow' + count + '" value="1" />')
    		  )
    		)
    		count++;
    	});
    });
  };
})( jQuery );