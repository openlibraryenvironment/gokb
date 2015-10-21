function ReconESPanel(column, service, container) {
  
  // The full column def.
  this._column = column;
  
  // The service as defined when registering.
  this._service = service;
  
  // The dom element that is the container for the panel.
  this._container = container;
  
  this._dir = "scripts/features/project/es_recon/";
  
  this._constructUI();
};

ReconESPanel.prototype.activate = function() {
  this._panel.show();
};

ReconESPanel.prototype.deactivate = function() {
  this._panel.hide();
};

ReconESPanel.prototype.dispose = function() {
  this._panel.remove();
  this._panel = null;

  this._column = null;
  this._service = null;
  this._container = null;
};

ReconESPanel.prototype.start = function() {
  var self = this;
  
  // Build up the values to pass to the job.
  var theType = $('input[type="radio"]:checked', self._elmts.type_select).val();
  
  ESRecon.recon(this._column.name, {
    /** Extra params **/
    service: self._service,
    type: theType
  });
};

ReconESPanel.prototype._constructUI = function() {
  var self = this;
  self._panel = $(DOM.loadHTML("gokb", self._dir + "es-recon-panel.html")).appendTo(this._container);
  self._elmts = DOM.bind(this._panel);
  
  ESRecon.getTypes().done(function(data){
    
    // Get the response and make a list.
    if (data && "types" in data) {
      
      // Clear the contents
      self._elmts.type_select.html("");
      
      // Let's filter the list here to only contain allowed reconcile types.
      var dataTypes = $.grep(data.types, function(item){
        return ($.inArray(item, ESRecon.exclTypes) > -1);
      }, true);
      
      $.each(dataTypes, function(i){
        
        // Add each option.
        var cb_label = $('<label for="type_' + i + '" />').text(this);
        var cb = $('<input id="type_' + i + '" type="radio" name="type" />')
          .val(this);
        if (i == 0) {
          cb.prop("checked", true);
        }
        
        // Append the 2 elements.
        self._elmts.type_select.append(
          $('<div class="form-row" >').append(cb).append(cb_label)
        );
      });
    }
  });
};