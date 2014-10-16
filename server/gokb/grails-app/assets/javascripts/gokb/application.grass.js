/**
 * GOKb application javascript file.
 */
//=require jquery
//=require raphael.min
//=require morris.min
//=require bootstrap
//=require bootbox.min
//=require bootstrap-editable.min
//=require select2
//=require metisMenu.min
//=require sb-admin-2
//=require inline-content
//=require summernote.min
//=require action-forms
//=require annotations
//=require select-all-multistate
//=require moment.min

// Global namespace for GOKb functions.
window.gokb = {
  "config" : {
    "lookupURI" : "/gokb/ajaxSupport/lookup"
  }
};

(function($) {
  
  // When DOM is ready.
  $(document).ready(function(){

    gokb.dialog = bootbox.dialog;
  
    /**
     * Show a confirmation box.
     */
    gokb.confirm = function (confirmCallback, message, confirmText, cancelText, cancelCallback) {
      
      // Set the defaults.
      if (typeof message == 'undefined') {
        message = "Are you sure?";
      }
      if (typeof confirmText == 'undefined') {
        confirmText = "Yes";
      }
      if (typeof cancelText == 'undefined') {
        cancelText = "No";
      }
      if (typeof cancelCallback == 'undefined') {
        cancelCallback = false;
      }
      
      // Add the message.
      var options = {
        "title"   : "Confirm action",
        "message" : message,
        "buttons" : {
          "Confirm" : {
            "label": confirmText,
            "className": "btn btn-sm btn-success",
            "callback": confirmCallback
          },
          "No": {
            "label": cancelText,
            "className": "btn btn-sm btn-danger",
            "callback": cancelCallback
          }
        }
      };
  
      // Default to empty object.      
      return gokb.dialog (options);
    };
    
    // Add some default behaviours we wish to define application wide.
    $('.confirm-click').click(function(e) {
      
      // The target.
      var target = $(this);
      
      if (target.hasClass('confirm-click-confirmed')) {
        
        // This action has been confirmed.
        target.removeClass('confirm-click-confirmed');
        
        // If we are here then the action was confirmed.
        var href = target.attr("href");
        if (href) {
          window.location.href = href;
        }
        
        // Do nothing just allow the click.
        
      } else {
        
        // Get the message attribute.
        var message = target.attr('data-confirm-message');
        
        // Prevent default as we need to confirm the action.
        e.preventDefault();
        
        // Now we need to ask the user to confirm.
        gokb.confirm (
          function () {
            // We need to refire the click event on the target after setting the class as a flag.
            target.addClass('confirm-click-confirmed');
            
            // Now refire the click.
            target.trigger('click', e);
          },
          target.attr('data-confirm-message'),
          target.attr('data-confirm-yes'),
          target.attr('data-confirm-no')
        );
          
          
      }
    });
    
    // If we have error messages then let's display them in a modal.
    var messages = $('#msg');
    if (messages.children().length > 0) {
      bootbox.alert("<h2 class='text-error' >Error</h2>" + messages.html());
    }
    
    $('#modal').on('show.bs.modal', function () {
      $(this).find('.modal-body').css({
             width:'auto', //probably not needed
             height:'auto', //probably not needed 
             'max-height':'100%'
      });
    });
    
    $('#modal').on('hidden.bs.modal', function() {
      $(this).removeData('bs.modal')
    })
    
    
    /** Editable **/
    $.fn.editable.defaults.mode = 'inline';
    
    $('.xEditableValue').editable();
    $(".xEditableManyToOne").editable();
    $('.ipe').editable();
  
    
    var results = $(".simpleHiddenRefdata");
    
    results.editable({
      url: function(params) {
        var hidden_field_id = $(this).data('hidden-id');
        $("#"+hidden_field_id).val(params.value);
        // Element has a data-hidden-id which is the hidden form property that should be set to the appropriate value
      }
    });
  
    results = $(".simpleReferenceTypedown");
    
    results.each(function() {
      
      $(this).select2({
        placeholder: "Search for...",
        allowClear: true,
        width:'resolve',
        minimumInputLength: 0,
        ajax: { // instead of writing the function to execute the request we use Select2's convenient helper
          url: gokb.config.lookupURI,
          dataType: 'json',
          data: function (term, page) {
            return {
              format:'json',
              q: term,
              baseClass:$(this).data('domain'),
              filter1:$(this).data('filter1'),
              addEmpty:'Y'
            };
          },
          results: function (data, page) {
            // console.log("resultsFn");
            return {results: data.values};
          }
        },
        initSelection : function (element, callback) {
          var idv=$(element).val();
          console.log("initSelection..%o"+idv,element);
          var txt=$(element).context.dataset.displayvalue;
          var data = {id: idv, text: txt};
          callback(data);
        }
      });
    });
  
    $(".xEditableManyToOneS2").each(function(elem) {
      var dom = $(this).data('domain');
      var filter1 = $(this).data('filter1');
      $(this).editable({
        select2: {
          placeholder: "Search for...",
          allowClear: true,
          width:'resolve',
          minimumInputLength: 0,
          ajax: {
            url: gokb.config.lookupURI,
            dataType: 'json',
            data: function (term, page) {
              return {
                format:'json',
                q: term,
                baseClass:dom,
                filter1:filter1,
                addEmpty:'Y'
              }
            },
            results: function (data, page) {
              return {results: data.values};
            }
          }
        }
      });
    });
  
    $(".xEditableManyToOneS2Old").editable({
      select2: {
        placeholder: "Search for.....",
        width:'resolve',
        minimumInputLength: 1,
        ajax: {
          url: gokb.config.lookupURI,
          dataType: 'json',
          data: function (term, page) {
            return {
              format:'json',
              q: term,
              baseClass:'org.gokb.cred.Org',
              filter1:$(this).data('filter1')
            }
          },
          results: function (data, page) {
            return {results: data.values};
          }
        }
      }
    });
  });
  
})(jQuery);
