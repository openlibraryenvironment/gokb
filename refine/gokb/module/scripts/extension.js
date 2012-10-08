

var GoKBExtension = { handlers: {} };


GoKBExtension.handlers.browseToDataLoad = function() {
  // The form has to be created as part of the click handler. If you create it
  // inside the getJSON success handler, it won't work.

  // var form = document.createElement("form");
  // $(form)
  // .css("display", "none")
  // .attr("method", "GET")
  // .attr("target", "gokbproperties");

  // document.body.appendChild(form);
  // var w = window.open("about:blank", "gokbproperties");


  var dialog = $(DOM.loadHTML("gokb", "scripts/dialogs/gokb_ingest.html"));
  var dialog_bindings = DOM.bind(dialog);
  // dialog_bindings.closeButton.click(function() {
  //   dismiss();
  // });
  var res = DialogSystem.showDialog(dialog);


  // dialog.show()
  alert("done");


  // $.getJSON(
  //   "/command/core/get-preference?" + $.param({ project: theProject.id, name: "freebase.load.jobID" }),
  //   null,
  //   function(data) {
  //     document.body.removeChild(form);
  //   }
  // );
};

ExtensionBar.addExtensionMenu({
  "id" : "GoKB",
  "label" : "GoKB",
  "submenu" : [
    {
      "id" : "GoKB/file-info",
      label: "Edit GoKB Ingest Properties",
      click: function() { 
        GoKBExtension.handlers.browseToDataLoad();
      }
    }
  ]
});

