(function($) {
  
  $(document).ready(function() {
    
    // Find every inline link.
    $('a.display-inline').each(function(){
      
      // The link.
      var link = $(this);
      
      var href = link.attr("href");
      
      // Selector of the sub=part of the page to pull in.
      var desired_selector = link.attr("data-content-selector");
      if (!desired_selector) desired_selector = "#mainarea";
      
      // The function to refresh content of the target element from the url.
      var refreshContent = function (target, url) {
        
        // Load in the content to a new div.
        $.get(url, {}, function(data, textStatus, jqXHR) {
          target.html($.parseHTML(data));
        
          // Get just the desired area.
          var desired_content = target.find(desired_selector);
          if (desired_content.length == 1) {
            target.html(desired_content.html());
          }
        });
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
        var clicked = $(event.target).closest('.nav a, a.open-inline'); 
        
        if (clicked.length > 0) {
          // Is a nav link. First thing to do is to stop the event default.
          event.preventDefault();
          
          // Now let's refresh this object.
          refreshContent( $(this), clicked.attr('href') );
        }
        
        // Else just allow the event to propagate. 
      });
      
      // Then swap out the link for the new content.
      link.replaceWith(content);
    });
  });
  
})(jQuery);