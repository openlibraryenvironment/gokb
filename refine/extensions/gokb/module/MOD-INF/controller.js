var html = "text/html";
var encoding = "UTF-8";
var ClientSideResourceManager = Packages.com.google.refine.ClientSideResourceManager;

/*
 * Register our custom commands.
 */
function registerCommands() {
  Packages.java.lang.System.out.print("\tRegistering commands...");
  var RS = Packages.com.google.refine.RefineServlet;
  RS.registerCommand(module, "project-checkout", new Packages.com.k_int.gokb.refine.commands.CheckOutProject());
  RS.registerCommand(module, "project-checkin", new Packages.com.k_int.gokb.refine.commands.CheckInProject());
  RS.registerCommand(module, "project-validate", new Packages.com.k_int.gokb.refine.commands.ValidateData());
  RS.registerCommand(module, "rules-suggest", new Packages.com.k_int.gokb.refine.commands.SuggestRules());
  RS.registerCommand(module, "datastore-save", new Packages.com.k_int.gokb.refine.commands.SaveDatastore());
  Packages.java.lang.System.out.println("done");
}

/*
 * Register new functions that extend the GREL language.
 */
function registerFunctions() {
	Packages.java.lang.System.out.print("\tRegistering functions...");
	registerFunction("ExtractHost", new com.k_int.gokb.refine.functions.ExtractHost());
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
 * Function invoked to initialize the extension.
 */
function init() {
  Packages.java.lang.System.out.println("Initializing GOKb...");
  Packages.java.lang.System.out.println(module.getMountPoint());
  registerCommands();
  registerFunctions();
  
  ClientSideResourceManager.addPaths(
		 "index/scripts",
		 module,
		 [
	     "scripts/plugins/jquery.uniform.min.js",
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
     "styles/uniform.default.css",
     "styles/uniform.aristo.css",
     "styles/common.less",
     "styles/index.less",
    ]
  );

  // Script files to inject into /project page
  ClientSideResourceManager.addPaths(
    "project/scripts",
    module,
    [
     "scripts/plugins/jquery.plugin.selectablerows.js",
     "scripts/plugins/jquery.uniform.min.js",
     "scripts/common.js",
     "scripts/forms.js",
     "scripts/project/validation-panel.js",
     "scripts/project.js",
     "scripts/project/handlers.js",
     "scripts/project/menu.js",
    ]
  );

  // Style files to inject into /project page
  ClientSideResourceManager.addPaths(
    "project/styles",
    module,
    [
      "styles/uniform.default.css",
      "styles/uniform.aristo.css",
      "styles/common.less",
    ]
  );
}