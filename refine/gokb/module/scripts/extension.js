

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

  var dialog = $(DOM.loadHTML("ckan-storage-extension", "scripts/dialogs/gokb_ingest.html"));

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
      click: function() { }
    }
  ]
});

