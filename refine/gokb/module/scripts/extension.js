

var GoKBExtension = { handlers: {} };


GoKBExtension.handlers.editIngestProps = function() {
  // The form has to be created as part of the click handler. If you create it
  // inside the getJSON success handler, it won't work.

  // var form = document.createElement("form");
  // $(form)
  // .css("display", "none")
  // .attr("method", "GET")
  // .attr("target", "gokbproperties");

  // document.body.appendChild(form);
  // var w = window.open("about:blank", "gokbproperties");

  // var dialog = $(DOM.loadHTML("gokb", "scripts/dialogs/gokb_ingest.html"));

  // dialog.show()

  // Some useful references at http://code.google.com/p/google-refine/source/browse/trunk/main/webapp/modules/core/scripts/dialogs/clustering-dialog.html
  // and http://code.google.com/p/google-refine/source/browse/trunk/main/webapp/modules/core/scripts/dialogs/clustering-dialog.js

  var self = this;

  // https://code.google.com/p/google-refine/source/browse/trunk/main/webapp/modules/core/scripts/util/dom.js?spec=svn2356&r=2303
  var dialog = $(DOM.loadHTML("freebase", "scripts/dialogs/freebase-loading-dialog.html"));

  this.gokb_ingest_dlg_binding = DOM.bind(dialog);

  this.gokb_ingest_dlg_binding.closeButton.click(function() { self._dismiss(); });

  this._ingest_dlg = DialogSystem.showDialog(dialog);

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
      click: function() { GoKBExtension.handlers.editIngestProps(); }
    }
  ]
});

