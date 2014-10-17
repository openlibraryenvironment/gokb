/**
 * Public facing site Javascript dependencies
 */
//=require jquery
//=require bootstrap

(function ($) {
  var changeHeaderOn = 111;
  var scrolling = false;

  // Add the scroll event listener.
  $(window).on("scroll", function() {

    var checkPos = function() {

      // Flag that the user has stopped scrolling.
      scrolling = false;

      var sy = $(window).scrollTop();
      var nav_bar = $('#primary-nav-bar');
      if ( sy >= changeHeaderOn ) {
        nav_bar.addClass( 'navbar-fixed-top' );

        $("#main-branding").addClass("pad-out");
      }
      else {
        nav_bar.removeClass( 'navbar-fixed-top' );
        $("#main-branding").removeClass("pad-out");
      }
    };

    // Wait until the user has stopped scrolling before firing.
    if (!scrolling) {
      scrolling = true;
      setTimeout( checkPos, 100 );
    }
  });

  //jQuery for page scrolling feature - requires jQuery Easing plugin
  $(document).ready(function(){
    $('a.page-scroll').bind('click', function(event) {
      var $anchor = $(this);
      $('html, body').stop().animate({
        scrollTop: ($($anchor.attr('href')).offset().top - ($(window).width() < 768 ? 250 : 75))
      }, 1000);
      event.preventDefault();
    });
    
    // Highlight the top nav as scrolling occurs
    $('body').scrollspy({
      target: '#primary-nav'
    });
    
    // Closes the Responsive Menu on Menu Item Click
    $('.navbar-collapse ul li a').click(function() {
      $('.navbar-toggle:visible').click();
    });
  });

})(jQuery);