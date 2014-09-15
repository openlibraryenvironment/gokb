(function($) {
  
  $(document).ready(function() {
    
    // Find every inline link.
    $('a.display-inline').each(function(){
      
      // The link.
      var link = $(this);
      
      // The actual url.
      var href = link.attr("href");
      
      // Selector of the sub=part of the page to pull in.
      var desired_selector = link.attr("data-content-selector");
      if (!desired_selector) desired_selector = "#mainarea";
      
      // The function to refresh content of the target element from the url.
      var refreshContent = function (target, url, type, the_data) {
        
        // Default data.
        if (the_data == undefined) the_data = {};
        
        // Default type
        if (type == undefined) type = "get";
        
        if (type == "get") {
          
          // Do the get.
          $.get(url, the_data, function(data, textStatus, jqXHR) {
            
            // The returned data.
            var dataDom = $("<div>" + data + "</div>");
            var desired_content = dataDom.find(desired_selector);
            if (desired_content.length == 1) {
              target.html(desired_content.html());
            } else {
              target.html(dataDom.html())
            }
          });
        } else {
          // Assume post.
          $.post(url, the_data, function(data, textStatus, jqXHR) {
            
            // The returned data.
            var dataDom = $("<div>" + data + "</div>");
            var desired_content = dataDom.find(desired_selector);
            if (desired_content.length == 1) {
              target.html(desired_content.html());
            } else {
              target.html(dataDom.html())
            }
          });
        }
        
        // Return the target.
        return target;
      };
      
      // Start with a new div.
      var content = refreshContent($("<div class='inline-content' />"), href);

      // The next thing to do is to bind an event listener to the content,
      // that can intercept links that are nav. These links should reload in the content area
      // rather than navigating away from the page.
      content.on ('click', function(event) {
        // The clicks should bubble up here before being actioned.
        
        // The clicked item. Get the closest matching a tag.
        var clicked = $(event.target).closest('.nav a, .inline-nav a, a.open-inline'); 
        
        if (clicked.length > 0) {
          // Is a nav link. First thing to do is to stop the event default.
          event.preventDefault();
          
          // Now let's refresh this object.
          refreshContent( $(this), clicked.attr('href') );
        }
        
        // Else just allow the event to propagate. 
      });
      
      // Add the new submit listener for forms here too.
      // We should catch the form submit and then simply serialise the form and do
      // the get or post n the background using ajax.
      content.on ('submit', function(event) {
        // The clicks should bubble up here before being actioned.
        
        // The clicked item. Get the closest matching a tag.
        var form = $(event.target).closest('form.open-inline'); 
        
        if (form.length > 0) {
          
          // Is a inline form. First thing to do is to stop the event default.
          event.preventDefault();
          
          // Now let's refresh this object.
          refreshContent( $(this), form.attr('action'), form.attr('method'), form.serialize());
        }
      });
      
      // Then swap out the link for the new content.
      link.replaceWith(content);
    });
  });
  
})(jQuery);