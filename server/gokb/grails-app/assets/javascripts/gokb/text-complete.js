(function($){
  
  /**
   * Default privately scoped methods and props.
   */
  var words = ['apple', 'google', 'facebook', 'github'];
  function defaultLookup (term, callback) {
    callback($.map(words, function (word) {
      var termLc = term.toLowerCase();
      if (typeof term !== 'undefined') {
        return word.toLowerCase().indexOf(termLc) === 0 ? word : null;
      }
      
      return null;
    }));
  };
  
  /**
   * Add the text complete functionality.
   */
  var addAutoComplete = function ( scope ) {
    
    // Default scope to body.
    if (typeof scope === "undefined") {
      scope = $("body");
    }
    
    $('.text-complete', $(scope)).each(function(){
      var me = $(this);
      var method = me.attr('data-complete-search') || 'defaultLookup';
      method = eval(method);
      
      // If the method was found.
      if (typeof method === 'function') {
        // Add the complete.
        me.textcomplete([{
          match: /\B@(\w{2,})$/,
          search: method,
          replace: function (word) {
            return word + ' ';
          },
          index: 1,
        }]);
      } else {
        // Could not find suitable search method.
        throw "Text Complete: search method not found.";
      }
    });
  };
  
  // We want to add on default ready event.
  $(document).ready(function(){
    addAutoComplete();
  })
  
  // We also want to listen for a modal show.
  // There is a modal event fired when the "remote" attribute is loaded and shown, but this is now
  // deprecated. We will act on show as we do not care how, the modal has been populated. This should
  // future proof this code.
  .on('shown.bs.modal', function (e) {
    // Us the modal as the context.
    var modal = $(e.target);
    addAutoComplete (modal);
  });  
})(jQuery);