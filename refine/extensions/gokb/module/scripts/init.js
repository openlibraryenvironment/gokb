/**
 * One time executed init methods for both index and project views.
 */
(function($) {
  
  // Need to do this in document 
  $(document).ready(function(){
    GOKb.timer().done(GOKb.preCoreUpdate);
  });
})(jQuery);
