(function ($) {
  
  var defaults = {
    "more-text" : "Show more",
    "less-text" : "Show less",
    "rows"      : "5",
    "height"    : "0",
  };
  
  // The plugin object.
  var showMoreApi = function(options) {
    
    var button = null;
    
    // The target.
    var target = this;
    
    // Extend the defaults with any params supplied.
    var settings = $.extend( {}, defaults, options );
    
    var limited = false;
    
    // We should also allow the values defined against the element using "data-" attributes.
    target.each(function() {
      
      // Grab the attributes.
      var attrs = $(this).get(0).attributes;
      
      $.each(attrs, function(){
        if (this.nodeName.toLowerCase().match("^data\-show\-more\-")) {
          
          // Then we can add to the settings.
          var name = this.nodeName.substring(15);
          
          // Set the value in the settings.
          settings[name] = this.nodeValue;
        }
      });
    });
    
    // The start height.
    settings['start_height'] = target.outerHeight();
    
    // The element top coord.
    var y = target.offset().top;
    
    if (target.is("table")) {
      
      // Calculate the height by the rows option instead.
      var rows = settings['rows'];
      
      if (rows < 1) {
        settings['height'] = 0;
        
      } else {
        
        // Try and grab the nth tr child of the table body.
        var all_rows = $('tr', target);
        
        if (all_rows.length > rows) {
        
          // We should limit...
          var last_row = $(all_rows[(rows - 1)]);
          
          // Now we can grab the bottom coords.
          var bottom = last_row.offset().top + last_row.outerHeight(true);
          
          settings['height'] = bottom - y;
        }
      }
    } 

    // If we need to limit then we should wrap in a new div and replace the existing content.
    if (settings['height'] > 0 && settings['start_height'] > settings['height']) {
      
      limited = true;
      
      button = $("<button/>").text(settings['more-text'])
      .click(function(){
        
        // Bind our click to this button.
        if (limited) {
          viewport.animate({height : settings['start_height'] + "px"}, 500);
          $(this).text(settings['less-text']);
          limited = false;
        } else {
          viewport.animate({height : settings['height'] + "px"}, 500);
          $(this).text(settings['more-text']);
          limited = true;
        }
      });
      
      var viewport = $('<div class="show-more-viewport" />')
        .css ({
          "overflow-y"  : "hidden"
        })
        .height(settings['height'] + "px")
      ;
      
      // Create the new div.
      var show_more_control = $('<div class="show-more-wrapper" />')
        .append( viewport )
        .append(
          $('<div class="show-more-controls" />')
            .css({
              'text-align' : 'right',
              'padding'    : '1em',
            })
            .append( button )
        )
      ;
      
      // We now need to replace the element with our new control.
      target.before(show_more_control);
      viewport.append(target);
    }

    // Return the public api.
    return {
      getButton : function () {
        return button;
      }
    };
  };

  var api = null;
  
  // Create the jQuery plugin...
  $.fn.showMore = function () {
    
    // Convert the arguments into an array so we can use apply below.
    var args = Array.prototype.slice.call(arguments);
    
    if (args.length < 1 || $.isPlainObject(args[0])) {
      
      api = showMoreApi.apply(this, args);
      
    } else if ( api != null && arguments.length > 0 && typeof args[0] === 'string') {
      
      var func = api[args[0]];
      if (typeof func === 'function') {
        
        // Trim the first arg.
        args = args.slice(1);
        
        return func.apply ( this, args );
      }
    }
    
    // Return this for method chaining.
    return this;
  };

}( jQuery ));