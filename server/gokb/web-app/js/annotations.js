(function($) {
  
  // Add a click even to hide any pop-overs that are visible.
  $('html').click(function(e) {
    $('.annotated').popover('hide');
  });
  
  // Need to add the annotations in pop-overs.
  $(".annotated").each(function(){
    
    // The target.
    var target = $(this);
    
    // Get the next sibling if it's an annotation.
    var annotation = target.next(".annotation");
    
    if (annotation.length == 1) {
      
      // Add a pop-over to the target, using manual triggering so we can close on click away.
      target.popover({
        
        "html"    : true,
        "trigger" : "manual",
        "title"   : target.text(),
        "content" : $('<div />').append(annotation.detach().show()).html()
        
      }).click(function(e) {
        
        // Toggle this element.
        var current = $(this).popover('toggle');
        
        // Close the other pop-overs.
        $('.annotated').each(function(){
          if ($(this).object != current.object) {
            $(this).popover('hide');
          }
        });
        
        // Ensure we stop this even bubbling here so it doesn't trigger the on click,
        // event registered on the html.
        e.stopPropagation();
      });
      
      // Now remove the annotation from the dom.
      annotation.remove()
    }
  });
  
})(jQuery);