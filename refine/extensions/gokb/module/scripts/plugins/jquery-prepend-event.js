(function($) {
  $.fn.extend({
    prependEvent: function (event, handler) {
      
      return this.each(function () {
        
        var me = $(this);
        
        // Grab the events.
        var events = $._data(me.get(0), "events");
        
        var currentHandler;
        if (events) {
          // Add as the first event.
          if (events[event].length > 0) {
            currentHandler = events[event][0].handler;
            events[event][0].handler = function (event) {
              handler.apply(this, arguments);
              
              // Only run the next event if immediate propagation is not stopped.
              if (!event.isImmediatePropagationStopped()) {
                currentHandler.apply(this, arguments);
              }
            }      
          } else {
            
            // Otherwise we should just add in the normal way.
            me.on (event, handler);
          }
        }
      });
    }
  });
})(jQuery);