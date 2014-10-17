/**
 * Timer recurring functions.
 */
GOKb.timer = function() {
  
  // Just call with empty callbacks. If the api is not up there will be a timeout.
  // If the versions are wrong then the default error callback will be fired and the,
  // version missmatch reported to the user.
  GOKb.doCommand("isUp", {}, {}, {});
  
  GOKb.getCoreData();
  
  if (!GOKb.versionError) {
    // Check again in 3 minutes.
    setTimeout(GOKb.timer, 30000);
  }
};

/**
 * One time executed init methods.
 */
(function($) {
  $(document).ready(function(){
  
    // Start the timer functions.
    GOKb.timer();
  });
})(jQuery);
