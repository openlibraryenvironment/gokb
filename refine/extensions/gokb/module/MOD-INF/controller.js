var html = "text/html";
var encoding = "UTF-8";
var ClientSideResourceManager = Packages.com.k_int.gokb.module.ExtendedResourceManager;
var coreMod = module.getModule("core");

/*
 * Register our custom commands.
 */
function registerCommands() {
  Packages.java.lang.System.out.print("\tRegistering commands...");
  var RS = Packages.com.google.refine.RefineServlet;
  RS.registerCommand(module, "get-workspaces", new Packages.com.k_int.gokb.refine.commands.GetWorkspaces());
  RS.registerCommand(module, "set-active-workspace", new Packages.com.k_int.gokb.refine.commands.SetWorkspace());
  RS.registerCommand(module, "project-checkout", new Packages.com.k_int.gokb.refine.commands.CheckOutProject());
  RS.registerCommand(module, "project-checkin", new Packages.com.k_int.gokb.refine.commands.CheckInProject());
  RS.registerCommand(module, "project-validate", new Packages.com.k_int.gokb.refine.commands.ValidateData());
  RS.registerCommand(module, "project-estimate-changes", new Packages.com.k_int.gokb.refine.commands.EstimateDataChanges());
  RS.registerCommand(module, "rules-suggest", new Packages.com.k_int.gokb.refine.commands.SuggestRules());
  RS.registerCommand(module, "data-addrows", new Packages.com.k_int.gokb.refine.commands.AddRowsCommand());
  RS.registerCommand(module, "data-trimws", new Packages.com.k_int.gokb.refine.commands.TrimWhitespaceCommand());
  RS.registerCommand(module, "datastore-save", new Packages.com.k_int.gokb.refine.commands.SaveDatastore());
  RS.registerCommand(module, "login", new Packages.com.k_int.gokb.refine.commands.Login());
  RS.registerCommand(module, "lookup", new Packages.com.k_int.gokb.refine.commands.Lookup());
  Packages.java.lang.System.out.println("done");
}

/*
 * Register new functions that extend the GREL language.
 */
function registerFunctions() {
	Packages.java.lang.System.out.print("\tRegistering functions...");
	registerFunction("ExtractHost", new com.k_int.gokb.refine.functions.ExtractHost());
	registerFunction("CaseInsensitiveCellLookup", new com.k_int.gokb.refine.functions.CaseInsensitiveCellLookup());
	Packages.java.lang.System.out.println("done");
}

/*
 * Register a single GREL function extension.
 */
function registerFunction (name, clazz) {
	var FR = com.google.refine.grel.ControlFunctionRegistry;
	FR.registerFunction(module.getName() + name, clazz);
}


/*
 * Function invoked to initialise the extension.
 */
function init() {
  Packages.java.lang.System.out.println("Initializing GOKb...");
  Packages.java.lang.System.out.println(module.getMountPoint());
  registerCommands();
  registerFunctions();
  
  // Remove jQuery version 1.4.x - 1.7.x and replace with jQuery 1.8.
  // Latest OpenRefine code now uses jQuery 1.9. To keep compatibility with older,
  // refine versions we only replace pre-1.8 versions with 1.8. If no match is found
  // then the 1.8 library should not be added.
  ClientSideResourceManager.replacePath(
    "index/scripts",
    coreMod,
    'externals/\\Qjquery-\\E(1\\.[4-7]).*\\.js',
    'scripts/jquery/jquery.js',
    module
  );
  ClientSideResourceManager.replacePath(
    "project/scripts",
    coreMod,
    'externals/\\Qjquery-\\E(1\\.[4-7]).*\\.js',
    'scripts/jquery/jquery.js',
    module
  );
  
  // Replace jQuery UI version 1.8.x and lower with 1.8.24
  ClientSideResourceManager.replacePath(
    "index/scripts",
    coreMod,
    'externals/jquery-ui/\\Qjquery-ui-\\E(1\\.[1-8])[^\\d].*\\.js',
    'scripts/jquery/jquery-ui.min.js',
    module
  );
  ClientSideResourceManager.replacePath(
    "project/scripts",
    coreMod,
    'externals/jquery-ui/\\Qjquery-ui-\\E(1\\.[1-8])[^\\d]+.*\\.js',
    'scripts/jquery/jquery-ui.min.js',
    module
  );
  
  // Index paths.
  ClientSideResourceManager.addPaths(
		 "index/scripts",
		 module,
		 [
	     "scripts/plugins/jquery.uniform.min.js",
	     "scripts/plugins/jquery.ui-lookup.js",
	     "scripts/common.js",
	     "scripts/forms.js",
	     "scripts/index.js",
	     "scripts/index/ui-open-project.js",
		 ]
	);
  
  // Style files to inject into /project page
  ClientSideResourceManager.addPaths(
    "index/styles",
    module,
    [
     "styles/jqui/jquery-ui.css",
     "styles/uniform.aristo.min.css",
     "styles/common.less",
     "styles/index.less",
    ]
  );

  ClientSideResourceManager.addPaths(
    "project/scripts",
    module,
    [
     "scripts/plugins/jquery.plugin.selectablerows.js",
     "scripts/plugins/jquery.uniform.min.js",
     "scripts/plugins/jquery.insert-at-caret.js",
     "scripts/plugins/jquery.ui.position.js",
     "scripts/plugins/jquery.contextMenu.js",
     "scripts/plugins/jquery.ui-lookup.js",
     "scripts/plugins/select2.min.js",
     "scripts/common.js",
     "scripts/forms.js",
     "scripts/project/validation-panel.js",
     "scripts/project/validation-panel-messages.js",
     "scripts/project.js",
     "scripts/project/handlers.js",
     "scripts/project/menu.js",
     "scripts/project/context-menu.js",
     "scripts/project/pnotify.custom.min.js",
    ]
  );

  // Style files to inject into /project page
  ClientSideResourceManager.addPaths(
    "project/styles",
    module,
    [
      "styles/jqui/jquery-ui.css",
      "styles/uniform.aristo.min.css",
      "styles/jquery.contextMenu.css",
      "styles/select2.css",
      "styles/pnotify.custom.min.css",
      "styles/common.less",
    ]
  );
}