(function($) {

  // Loads the correct sidebar on window load,
  // collapses the sidebar on window resize,
  // and sets the min-height of #page-wrapper to window size
  // $(window).on("load resize", function() {
  //   topOffset = 50;
  //   width = (this.window.innerWidth > 0) ? this.window.innerWidth : this.screen.width;
  //   if (width < 768) {
  //       $('div.navbar-collapse').addClass('collapse');
  //       topOffset = 100; // 2-row-menu
  //   } else {
  //       $('div.navbar-collapse').removeClass('collapse');
  //   }

  //   height = (this.window.innerHeight > 0) ? this.window.innerHeight : this.screen.height;
  //   height = height - topOffset;
  //   if (height < 1) height = 1;
  //   if (height > topOffset) {
  //       $("#page-wrapper").css("min-height", (height) + "px");
  //   }
  // });

  $(document).ready(function(){

    // Execute on the side menu.
    $('#side-menu').metisMenu();
  });
})(jQuery);