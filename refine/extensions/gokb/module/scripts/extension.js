/**
 * Handlers
 */
GOKbExtension.handlers.suggest = function() {
  var dialog = GOKbExtension.createDialog("Suggested Transformations", "suggest");
  
  
//  dialog.bindings.dialogContent.html(GOKbExtension.server.suggest);
//  dialog.bindings.dialogContent.html(JSON.stringify(theProject.columnModel.columns));
  GOKbExtension.showDialog(dialog);
};