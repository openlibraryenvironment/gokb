/**
 * GOKb application javascript file.
 */
//=require jquery-2.2.0.min
//=require jquery.mask
//=require raphael.min
//=require morris.min
//=require bootstrap
//=require bootbox.min
//=require bootstrap-editable
//=require select2
//=require metisMenu.min
//=require show-more
//=require sb-admin-2
//=require inline-content
//=require summernote.min
//=require action-forms
//=require annotations
//=require select-all-multistate
//=require moment.min
//=require decisionSupport
//=require jquery.textcomplete.min
//=require text-complete

// Global namespace for GOKb functions.
// Ian:: We are now deploying the G3 application at / instead of /gokb. URLs changed accordingly
// However - this feels brittle, and likely to change on a deployment by deployment basis. Needs
// discussion I think?
window.gokb = {
  "config" : {
    "lookupURI" : "/ajaxSupport/lookup",
    "baseUrl" : "/",
  },
  validateJson : function (value) {
    
    if (value && value != "") {
      try {
        // Parse the JSON
        var data = $.parseJSON ( value );
        
        data = JSON.stringify(data, null, "  ");
        return data;
      } catch (e) {
        return false;
      }
    }
  } 
};

(function($) {
  
  // When DOM is ready.
  $(document).ready(function(){

    if(contextPath) {
      gokb.config.baseUrl = contextPath;
      gokb.config.lookupURI = contextPath + "/ajaxSupport/lookup";
    }

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
      bootbox.alert("<h2 class='text-info' >Info</h2>" + messages.html());
    }

    var errors = $('#error');
    if (errors.children().length > 0) {
      bootbox.alert("<h2 class='text-danger' >Error</h2>" + errors.html());
    }

    var success = $('#success');
    if (success.children().length > 0) {
      bootbox.alert("<h2 class='text-success' >Success</h2>" + success.html());
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
    $.fn.editable.defaults.onblur = 'ignore';
    
    $('.xEditableValue').editable();
    
    // Add the client-side validation to test for valid json.
    $('.refine-transform .xEditableValue').editable('option', 'validate', function(value){
      if ( gokb.validateJson( value ) == false ) {
        return "The JSON is incorrectly formatted.";
      }
    });
    
    $(".xEditableManyToOne").editable();
    
    // Handle dates differently now.
    $('.ipe').each(function() {

      // The context.
      var me = $(this);

      if (me.is(".date")) {
        // This is a date element. We should add the date functionality.
        me.on('shown', function(e, editable) {
          console.log(editable);
          $('.form-date').mask(
            '0000-M0-D0',
            {
              'translation': {
                M: {pattern: /[0-1]/},
                D: {pattern: /[0-3]/},
              },
              'placeholder': "YYYY-MM-DD"
            }
          );
        });
      }

      // Make it editbale()
      me.editable();
    });
  
    
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

      var conf = {
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
              addEmpty:($(this).data('require') ? 'N' : 'Y')
            };
          },
          results: function (data, page) {
            // console.log("resultsFn");
            return {results: data.values};
          }
        },
        initSelection : function (element, callback) {
          var idv=$(element).val();
          // console.log("initSelection..%o"+idv,element);
          var txt=$(element).context.dataset.displayvalue;
          var data = {id: idv, text: txt};
          callback(data);
        }
      };
      
      var me = $(this);
      
      if (me.hasClass("allow-add")) {
        // Add to the config...
        conf.createSearchChoice = function(term, data) {
          if ($(data).filter(function() {
            return term.localeCompare(this.text) === 0;
          }).length === 0) {
            return {
              id: me.data('domain') + ":__new__:" + me.data('filter1') + ":" + term, 
              text:  term  + ' (new tag)'
            };
          }
        };
      }
      
      me.select2(conf);
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
                addEmpty:($(this).data('required') ? 'N' : 'Y')
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

    $('#savedItemsPopup').on('show.bs.dropdown', function () {
      // do something…
      $('#savedItemsContent').load('/gokb/savedItems/index?folder=userHome');
    });
    
    // Add show mores...
    $('.show-more').each(function() {
      var me = $(this);
      me.showMore();
      var button = me.showMore('getButton');
      if (button) {
        button.addClass('btn btn-default');
      }
    });
    
    // Add the json handling to the textareas.
    $(".json").on("paste", function(e){
      
      data = gokb.validateJson( e.originalEvent.clipboardData.getData('text') );
      if (data == false) {
        bootbox.alert("<h2 class='text-danger' >Error</h2>" +
          '<p>The JSON you are attempting to paste is incorrectly formatted. Please ensure you copy everything from the source.</p>'
        );
        
        // Halt all other events.
        e.preventDefault();
        e.stopImmediatePropagation();
      }
    });

  });
})(jQuery);
