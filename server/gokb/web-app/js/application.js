if (typeof jQuery !== 'undefined') {
	(function($) {
		$('#spinner').ajaxStart(function() {
			$(this).fadeIn();
		}).ajaxStop(function() {
			$(this).fadeOut();
		});
	})(jQuery);
}

// $(function(){
$(document).ready(function() {

  $.fn.editable.defaults.mode = 'inline';

  $('.xEditableValue').editable();

  $(".xEditableManyToOne").editable(
  );

  $(".simpleHiddenRefdata").editable({
    url: function(params) {
      alert("editable hidden");
    }
  });

  $(".simpleReferenceTypedown").select2({
    placeholder: "Search for...",
    minimumInputLength: 1,
    ajax: { // instead of writing the function to execute the request we use Select2's convenient helper
      url: "<g:createLink controller='ajaxSupport' action='lookup'/>",
      dataType: 'json',
      data: function (term, page) {
        return {
          format:'json',
          q: term,
          baseClass:$(this).data('domain')
        };
      },
      results: function (data, page) {
        return {results: data.values};
      }
      
  }});

});

