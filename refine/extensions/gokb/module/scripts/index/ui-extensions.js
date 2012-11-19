// Create GOKb Project UI
var GOKb = {};
GOKb.ProjectsUI = function (elmt) {
  var self = this;
	this._elmt = elmt;
  this._elmts = DOM.bind(elmt);
};

// Allow resizing of this element.
GOKb.ProjectsUI.prototype.resize = function() {
  var height = this._elmt.height();
  var width = this._elmt.width();
  var controlsHeight = this._elmts.workspaceControls.outerHeight();

  this._elmts.projectsContainer
  .css("height", (height - controlsHeight - DOM.getVPaddings(this._elmts.projectsContainer)) + "px");

  this._elmts.workspaceControls
  .css("bottom", "0px")
  .css("width", (width - DOM.getHPaddings(this._elmts.workspaceControls)) + "px");
};

// Push the extra element to the action areas.
Refine.actionAreas.push({
  id: "gokb-projects",
  label: "GOKb Projects",
  uiClass: GOKb.ProjectsUI
});