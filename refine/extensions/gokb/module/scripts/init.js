/**
 * One time executed init methods.
 */
(function($) {
  
  // Need to do this in document 
  $(document).ready(function(){
    GOKb.timer().done(GOKb.preCoreUpdate);
  });
})(jQuery);
