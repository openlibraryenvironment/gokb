function ValidationPanel(div, tabHeader) {
  this._div = div;
  this._tabHeader = tabHeader;
  this.update();
}

ValidationPanel.prototype.resize = function() {
  var body = this._div.find(".validation-panel-body");

  var bodyPaddings = body.outerHeight(true) - body.height();
  body.height((this._div.height() - bodyPaddings) + "px");
  body[0].scrollTop = body[0].offsetHeight;
};

ValidationPanel.prototype.update = function(onDone) {
  var self = this;
  
  // Set the _data attribute to the data.
  
  // Then render.
  this._render();
};

ValidationPanel.prototype._render = function() {
  var self = this;

  // Reload the HTML 
  this._div.empty().unbind().html(DOM.loadHTML("gokb", "scripts/project/validation-panel.html"));

  // Bind the elements.
  var elmts = DOM.bind(this._div);

  // Resize the panel.
  this.resize();
};