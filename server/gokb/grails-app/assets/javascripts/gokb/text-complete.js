(function($){
  
  /**
   * Default privately scoped methods and props.
   */
  var components = {
    'org'   : [
      {id: 1, 'class': 'org.gokb.cred.Org', title: 'apple'},
      {id: 2, 'class': 'org.gokb.cred.Org', title: 'google'},
      {id: 3, 'class': 'org.gokb.cred.Org', title: 'facebook'},
      {id: 4, 'class': 'org.gokb.cred.Org', title: 'github'}],
    'title' : [
      {id: 5, 'class': 'org.gokb.cred.TitleInstance', title: 'Title 1'},
      {id: 6, 'class': 'org.gokb.cred.TitleInstance', title: 'Title 2'},
      {id: 7, 'class': 'org.gokb.cred.TitleInstance', title: 'Title 3'},
      {id: 8, 'class': 'org.gokb.cred.TitleInstance', title: 'Title 4'}]
  };
  
  /**
   * Default lookup function that will be used if not 
   */
  function defaultLookup (term, callback, segments) {

    // Regex match groups contain or filter and title.
    var type = (segments[2] || "").toLowerCase();
    
    if (type in components) {
      // Match against the name.
      callback($.map(components[type], function ( component ) {
        var termLc = term.toLowerCase();
        if (typeof term !== 'undefined' && component) {
         
          // Return object instead of just a string.
          return component.title.toLowerCase().indexOf(termLc) === 0 ? component : null;
        }
        
        return null;
      }));
    }
    
    
  };
  
  /**
   * Add the text complete functionality.
   */
  var addAutoComplete = function ( scope ) {
    
    // Default scope to body.
    if (typeof scope === "undefined") {
      scope = $("body");
    }
    
    $('.text-complete:not(".text-complete-enhanced")', $(scope)).each(function(){
      var me = $(this);
      var method = me.attr('data-complete-search') || 'defaultLookup';
      method = eval(method);
      
      // If the method was found.
      if (typeof method === 'function') {
        // Add the complete.
        me.textcomplete([{
          match: /(\s|^)@(\w+)\:(\w+)$/,
          search: method,
          index: 3,
          replace: function ( component ) {
            return ['$1<a href="' + gokb.config.baseUrl + '/resource/show/' + component['class'] + ':' + component['id'] + '" >' + component['title'], '</a>'];
          },
          template: function (component, event) {
            return component.title;
          }
        }], 
        // Options...
        {
          zIndex:     99999,
          debounce:   650,
        })
        // Mark as enhanced.
        .addClass('text-complete-enhanced');
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