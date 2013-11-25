(function($) {
  
  function refreshAnnotationPos (annotated, annotation) {
    
    var annotated_centre = (annotated.outerHeight() / 2) + annotated.offset().top;
    var annotation_half = annotation.outerHeight() / 2;
    
    var new_top = annotated_centre - annotation_half;
  
    // move the element up.
    annotation.offset({
      left: annotation.offset().left,
      top: new_top
    });
  }
  
  function showHideEditorButtons(editor_el, show) {
    if (show) {
      
      // Add buttons as a sibling of the editor.
      var wrapper = $('<div />').addClass('editor-buttons').append(
        $('<button class="btn btn-small btn-success" />').text("Save").click(function() {
          alert("Saved");
          editor_el.destroy();
          showHideEditorButtons(editor_el, false);

          // Shift the element back.
          var popover = editor_el.closest('.popover');
          
          // Refresh the position.
          refreshAnnotationPos (popover.prev('.annotated'), popover);
        })
      );
      
      // Append the cancel button.
      wrapper.append(
        $('<button class="btn btn-small btn-danger" />').text("Cancel").click(function() {
          editor_el.destroy();
          showHideEditorButtons(editor_el, false);

          // Shift the element back.
          var popover = editor_el.closest('.popover');
          
          // Refresh the position.
          refreshAnnotationPos (popover.prev('.annotated'), popover);
        })
      );
      
      // Append next to the editor.
      $(editor_el).parent().append(wrapper);
      
    } else {
      // Hide/remove.
      $('.editor-buttons', editor_el.parent()).remove();
    }
  }
  
  // Add a tooltip to each editable annotation to direct the admin user.
  $('.annotation-editable').tooltip();
  
  // Add the double click listener to the HTML. All double clicks will bubble up to here so we need
  // to be selective on how we respond.
  $('html').dblclick(function(e) {
    
    // Get the element that was double-clicked.
    var me = $(e.target);
    
    // Editable annotation.
    if (me.hasClass('annotation-editable')) {
      
      // Add an editor inline.
      me.summernote({
        focus: true,
        toolbar: [
          ['style', ['bold', 'italic', 'underline', 'clear']],
          ['para', ['ul', 'ol']],
          ['web',['link']]
        ],
        oninit: function() {
          showHideEditorButtons(me, true);
        }
      });
      
      // Move the popover to keep it inline with it's caller.
      var the_popover = me.closest('.popover')
      if (the_popover.length == 1) {
        
        // Shift the element.
        refreshAnnotationPos (the_popover.prev('.annotated'), the_popover);
      }
    }
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
        "content" : annotation.detach().show()
        
      }).on("hidden", function(e) {
        
        // Listen to the hidden even and destroy any editor that might exist here.
        annotation.destroy();

        // Also need to ensure we remove the buttons.
        showHideEditorButtons(annotation, false);
        
      }).click(function(e) {
        
        // Toggle this element.
        var current = $(this);
        current.popover('toggle');
        
        // Close the other pop-overs.
        $('.annotated').each(function(){
          if ($(this).object != current.object) {
            $(this).popover('hide');
          }
        });
        
        // Ensure we stop this even bubbling here so it doesn't trigger the on click,
        // event registered on the HTML.
        e.stopPropagation();
        
      });
    }
  });
})(jQuery);