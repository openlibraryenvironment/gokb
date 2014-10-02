package com.k_int.gokb.module;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletConfig;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections.ExtendedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.ProjectManager;
import com.google.refine.RefineServlet;
import com.google.refine.grel.ControlFunctionRegistry;
import com.google.refine.importing.ImportingManager;
import com.google.refine.io.FileProjectManager;

import com.k_int.gokb.refine.RefineWorkspace;
import com.k_int.gokb.refine.commands.GerericProxiedCommand;
import com.k_int.gokb.refine.functions.GenericMatchRegex;

import edu.mit.simile.butterfly.ButterflyModule;
import edu.mit.simile.butterfly.ButterflyModuleImpl;

public class GOKbModuleImpl extends ButterflyModuleImpl {

    final static Logger _logger = LoggerFactory.getLogger("GOKb-ModuleImpl");

    public static GOKbModuleImpl singleton;
    public static ExtendedProperties properties;
    
    private RefineWorkspace[] workspaces;

    public static final String VERSION = "3.1";
    public static String getVersion() {
      if (version == null) {
        version = singleton.getProperties().getString("module.version");
      }
      
      return version;
    }

    private static String userDetails = null;

    public static String getCurrentUserDetails() {
        return userDetails;
    }

    public static void setCurrentUserDetails(String username, String password) {
        userDetails = Base64.encodeBase64String((username + ":" + password).getBytes());
        
        // Test restart here.
//        File module_path = singleton.getPath().getParentFile().getParentFile();
//        try {
//          Updater updt = new Updater(
//            module_path,
//            new URL("http://d7.localhost/test.zip"),
//            singleton.getPath().getParentFile().getParentFile());
//          updt.updateAndRestart();
//        } catch (IOException e) {
//          // TODO Auto-generated catch block
//          e.printStackTrace();
//        }
    }

    @Override
    public void init(ServletConfig config) throws Exception {

        // Run default init method.
        super.init(config);

        // Perform our extended initialisation...
        extendModuleProperties();
        swapImportControllers();

        // Add our proxied Commands from the config file.
        addProxiedCommands();

        // Add the generic regex functions.
        addRegexFunctions();

        // Set the singleton.
        singleton = this;

        // Set the properties
        properties = singleton.getProperties();
        
        // Add the workspaces detailed in the properties file.
        addWorkspaces();
        
        _logger.info("Connection timeout set to " + (double)((double)properties.getInt("timeout") / (double)60000) + " minutes");
    }
    
    private void addWorkspaces () throws IOException {
      
      // Get the file-based project manager.
      File current_ws = ((FileProjectManager)FileProjectManager.singleton).getWorkspaceDir();
      
      // Load the list from the properties file.
      @SuppressWarnings("unchecked")
      List<String> apis = properties.getList("api.entry");
      
      // Include local?
      if (properties.containsKey("apis")) {
        
        // Add any passed in via command line too. We add these as priorities.
        apis.addAll(0, properties.getList("apis"));
      }
      
      // Check that the list length is even as each should be in pairs.
      if (apis.size() % 3 != 0) {
        _logger.error("APIs must be defined as name/folder_suffix/url tuples.");
      } else {
        
        // The workspaces.
        workspaces = new RefineWorkspace[apis.size() / 3];
        
        _logger.info("Configuring workspaces...");
        
        // Go through each group and add the file and URL.
        for (int i=0; i<apis.size(); i+=3) {
          
          RefineWorkspace ws = new RefineWorkspace (
            apis.get(i),
            apis.get(i+2),
            new File(current_ws.getCanonicalPath() + "_" + apis.get(i+1))
          );
          
          // Add to the array.
          workspaces[i/3] = ws;

          _logger.info("\tAdded " + ws.getName() + " (" + ws.getService().getURL() + ")" + (!ws.isAvailable() ? " [offline]" : "") );
        }
        
        // Try and find the first available workspace.
        int wsindex = -1;
        for (int i=0; i < workspaces.length && wsindex < 0; i++) {
          RefineWorkspace ws = workspaces[i];
          if (ws.isAvailable()) {
            wsindex = i;
          }
        }
        
        // Set active workspace.
        if (wsindex >= 0) {
          setActiveWorkspace(wsindex);
        } else {
          // Could not connect to any gokb workspace.
          // TODO: We should disable the whole module here if we can.
          // for now we shall throw an exception.
          
          throw new IOException("Could not connect to any of the defined GOKb services.");
        }
      }
    }
    
    private int currentWorkspaceId;
    public void setActiveWorkspace(int workspace_id) {
      
      // We should now set the new workspace.
      ProjectManager.singleton.dispose();
      ProjectManager.singleton = null;
      
      // Set the id. 
      currentWorkspaceId = workspace_id;
      
      // Get the current WS.
      RefineWorkspace newWorkspace = workspaces[currentWorkspaceId];
      
      // Now we re-init the project manager, with our new directory.
      FileProjectManager.initialize(newWorkspace.getWsFolder());
      
      _logger.info(
        "Now using workspace '" + newWorkspace.getName() + "' at URL '" +
            newWorkspace.getService().getURL() + "'");
      
      // Need to clear login information too.
      userDetails = null;
      _logger.info("User login details reset to force login on workspace change.");
    }
    
    public RefineWorkspace[] getWorkspaces() {
      return workspaces;
    }
    
    public String getCurrentWorkspaceURL() {
      return workspaces[currentWorkspaceId].getService().getURL();
    }

    public int getCurrentWorkspaceId () {
      return currentWorkspaceId;
    }

    private void addProxiedCommands() {

        _logger.debug("Adding proxied commands from the properties.");

        @SuppressWarnings("unchecked")
        List<String> commands = getProperties().getList("proxyCommands");

        // Register each command from the list.
        for (String command : commands) {
            RefineServlet.registerCommand(this, command, new GerericProxiedCommand(command));
        }
    }

    private void addRegexFunctions() {

        _logger.info("Adding regex functions from the properties.");
        @SuppressWarnings("unchecked")
        List<String> names = getProperties().getList("regex.name");

        @SuppressWarnings("unchecked")
        List<String> patterns = getProperties().getList("regex.pattern");

        @SuppressWarnings("unchecked")
        List<String> offsets = getProperties().getList("regex.skip");

        if (names.size() == patterns.size() && patterns.size() == offsets.size()) {
            // Register each regex function from the properties.
            for (int i=0; i<names.size(); i++) {

                try {
                    ControlFunctionRegistry.registerFunction(
                            this.getName() + "Match" + names.get(i),
                            new GenericMatchRegex (patterns.get(i), Integer.parseInt(offsets.get(i)))
                            );
                } catch (Exception e){

                    // Log the error

                    _logger.error(e.getLocalizedMessage(), e);
                }
            }
        } else {
            _logger.error("regex items need to declare name, pattern and skip");
        }
    }

    public static File getTemporaryDirectory() {
        return singleton.getTemporaryDir();
    }

    private void extendModuleProperties() {
        // The module path
        File f = getPath();

        // Load our custom properties.
        File modFile = new File(f,"MOD-INF");
        if (modFile.exists()) {
          
          // Read in the gokb properties.
          ExtendedProperties p = loadProperties (new File(modFile,"gokb.properties"));

          // Also need to add the regex properties from the regex file.
          p.combine(
            loadProperties (new File(modFile,"regex.properties"))
          );
          
          // Add module properties to the GOKb properties to allow,
          // command-line passed params to override these values.
          p.combine(getProperties());

          // Set this modules properties.
          setProperties(p);
        }
    }
    
    private ExtendedProperties loadProperties (File propFile) {
      ExtendedProperties p = new ExtendedProperties();
      try {
        if (propFile.exists()) {
            _logger.info("Loading GOKb properties ({})", propFile);
            BufferedInputStream stream = null;
            try {
                stream = new BufferedInputStream(new FileInputStream(propFile));
                p.load(stream);
            } finally {
                // Close the stream.
                if (stream != null) stream.close();
            }

            // Add module properties to the GOKb properties to allow,
            // command-line passed params to override these values.
            p.combine(getProperties());

            // Set this modules properties.
            setProperties(p);
        }
      } catch (Exception e) {
          _logger.error("Error loading GOKb properties", e);
      }
      
      return p;
    }

    private void swapImportControllers() {
        // Get the core module.
        ButterflyModule coreMod = getModule("core");
        String controllerName = "default-importing-controller";

        // Remove default controller.
        ImportingManager.controllers.remove(
            coreMod.getName() + "/" + controllerName
        );

        // Now register our controller at the default key.
        ImportingManager.registerController(
            coreMod,
            controllerName,
            new GOKbImportingController()
        );

    }
}
