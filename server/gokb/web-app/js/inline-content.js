(function($) {
  
  $(document).ready(function(){
    // Find every inline link.
    $('a.display-inline').each(function(){
      
      // The link.
      var link = $(this);
      
      var href = link.attr("href");
      
      var desired_id = link.attr("data-content-id");
      if (!desired_id) desired_id = "mainarea";
      
      // Load in the content to a new div.
      $.get(href, {}, function(data, textStatus, jqXHR){
        
        // Load all the data.
        var content = $("<div class='inline-content' />").html(data);
        
        // Get just the desired area.
        var desired_content = $('#' + desired_id, content);
        if (desired_content.length == 1) {
          content.html(desired_content.html())
        }
        
        // Then swap out the link for the new content.
        link.replaceWith(content);
      });
    });
  });
  
})(jQuery);