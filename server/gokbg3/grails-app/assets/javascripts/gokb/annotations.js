(function($) {
  
  var original_html = {};
  
  function quickUID() {
    return Math.random().toString(36).substring(2, 15) +
        Math.random().toString(36).substring(2, 15);
  }
  
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
        $('<button class="btn btn-small btn-success" />').text("Save").click(function(e) {

          // The popover
          var popover = editor_el.closest('.popover');

          // Save the data.
          var value = editor_el.summernote('code');
          
          // The edited element.
          var el = $('.annotation-editable', popover)
          
          // Post via ajax.
          $.ajax({
            type: "POST",
            url: el.attr('data-url'),
            data: {
              "pk"    : el.attr('data-pk'),
              "name"  : el.attr('data-name'),
              "value" : value
            }
          }).done(function( msg ) {
            
            // Remove the empty class if it exists.
            el.removeClass('annotation-empty');
            
            // Close the editor
            editor_el.summernote('destroy');
            
            // Remove the editor buttons.
            showHideEditorButtons(editor_el, false);
            
            // Refresh the position.
            refreshAnnotationPos (popover.prev('.annotated'), popover);
          });
          
          // Stop event bubbling and cancel the default action (submit).
          e.stopPropagation();
          e.preventDefault();
        })
      );
      
      // Append the cancel button.
      wrapper.append(
        $('<button class="btn btn-small btn-danger" />').text("Cancel").click(function(e) {
          editor_el.summernote('destroy');
          showHideEditorButtons(editor_el, false);

          // Shift the element back.
          var popover = editor_el.closest('.popover');
          var annotated = popover.prev('.annotated');
          
          // Restore original value.
          editor_el.html(original_html[editor_el.attr("id")]);
          
          // Refresh the position.
          refreshAnnotationPos (annotated, popover);
          
          // Stop event bubbling and cancel the default action (submit).
          e.stopPropagation();
          e.preventDefault();
        })
      );
      
      // Append next to the editor.
      $(editor_el).parent().append(wrapper);
      
    } else {
      // Hide/remove.
      $('.editor-buttons', editor_el.parent()).remove();
    }
  }

  // Document load.
  $(document).ready(function(){
    // Quick generate an ID if one isn't present.
    $('.annotation-editable').each(function(){
      var el = $(this);
      
      if (!el.attr('id')) {
        // Add one.
        el.attr('id', quickUID());
      }
    });
  
    // Add the double click listener to the HTML. All double clicks will bubble up to here so we need
    // to be selective on how we respond.
    $('html').click(function(e){
      
      // Get the element that was double-clicked.
      var me = $(e.target);
      var the_popover = $('.popover');
      
      // Editable annotation.

      if ( the_popover.length == 0 || (!me.hasClass("annotation") && me.attr('class').search('note\-.*') == -1 && !the_popover.hasClass("editing")) ) {
        $('.annotated').popover('hide');
      } else if ( the_popover.hasClass("editing") && !me.hasClass("annotation") && me.attr('class').search('note\-.*') == -1 ) {
        the_popover.removeClass("editing");
      }
      
    }).dblclick(function(e) {
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
          callbacks: {
            onInit: function() {
              showHideEditorButtons(me, true);
              original_html[me.attr("id")] = me.html();
            }
          }
        });
        
        // Move the popover to keep it inline with it's caller.
        var the_popover = me.closest('.popover')
        if (the_popover.length == 1) {
          
          // Shift the element.
          refreshAnnotationPos (the_popover.prev('.annotated'), the_popover);

          if (!the_popover.hasClass("editing")) {
            the_popover.addClass("editing");
          }
        }
        
        // Ensure we stop this even bubbling here so it doesn't trigger the on click,
        // event registered on the HTML.
        e.stopPropagation();
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
          var the_popover = me.closest('.popover');

          if ( !the_popover.hasClass("editing") ) {
            annotation.summernote("destroy");

            // Also need to ensure we remove the buttons.
            showHideEditorButtons(annotation, false);
          }
          else {
            the_popover.removeClass("editing");
          }
          
        }).on("shown.bs.popover", function(e) {
          
          // Bind the tooltip to the content every time the annotation is shown as it's lost.
          // after first show.
          var editableEl = $('.annotation-editable', $(this).next());
          if (editableEl.length > 0) {
            editableEl.tooltip({
              "title" : editableEl.attr("data-original-title")
            });
          }else{
            editableEl.tooltip({
              "title" : editableEl.attr("data-original-title")
            });
          } 
        }).on("inserted.bs.popover",function(e) {
          $('.annotation').show();
        }).click(function(e) {
          
          // Toggle this element.
          var current = $(this);
          
          // Close open ones here too.
          $('.annotated').each(function(){
            $(this).popover('hide');
          });
          current.popover('toggle');
          
          // Ensure we stop this even bubbling here so it doesn't trigger the on click,
          // event registered on the HTML.
          e.stopPropagation();
        });
      }
    });
  });
})(jQuery);
