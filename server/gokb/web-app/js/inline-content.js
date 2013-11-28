(function($) {
  
  $(document).ready(function(){
    // Find every inline link.
    $('a.display-inline').each(function(){
      
      // The link.
      var link = $(this);
      
      var href = link.attr("href");
      
      var desired_selector = link.attr("data-content-selector");
      if (!desired_selector) desired_selector = "#mainarea";
      
      // Load in the content to a new div.
      $.get(href, {}, function(data, textStatus, jqXHR){
        
        // Load all the data.
        var content = $("<div class='inline-content' />").html($.parseHTML(data));
        
        // Get just the desired area.
        var desired_content = $(desired_selector, content);
        if (desired_content.length == 1) {
          content = desired_content;
        }
        
        // Then swap out the link for the new content.
        link.replaceWith(content);
      });
    });
  });
  
})(jQuery);