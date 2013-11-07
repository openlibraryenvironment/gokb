package com.k_int.gokb.refine;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import javax.servlet.ServletConfig;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections.ExtendedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.k_int.gokb.refine.commands.GerericProxiedCommand;
import com.k_int.gokb.refine.functions.GenericMatchRegex;

import com.google.refine.RefineServlet;
import com.google.refine.grel.ControlFunctionRegistry;
import com.google.refine.importing.ImportingManager;

import edu.mit.simile.butterfly.ButterflyModule;
import edu.mit.simile.butterfly.ButterflyModuleImpl;

public class GOKbModuleImpl extends ButterflyModuleImpl {

    final static Logger _logger = LoggerFactory.getLogger("GOKb-ModuleImpl");

    public static GOKbModuleImpl singleton;
    public static ExtendedProperties properties;

    public static final String VERSION = "1.6";

    private static String userDetails = null;

    public static String getCurrentUserDetails() {
        return userDetails;
    }

    public static void setCurrentUserDetails(String username, String password) {
        userDetails = Base64.encodeBase64String((username + ":" + password).getBytes());
    }

    @Override
    public void init(ServletConfig config) throws Exception {

        // Run default init method.
        super.init(config);

        // Perform our extended initialisation...
        extendModuleProperties();
        swapImportControllers();

        // Output the url currently in use.
        _logger.info("Using URL '" + getProperties().getString("api.url") + "'");

        // Add our proxied Commands from the config file.
        addProxiedCommands();

        // Add the generic regex functions.
        addRegexFunctions();

        // Set the singleton.
        singleton = this;

        // Set the properties
        properties = singleton.getProperties();
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
            try {
                // Get the existing module properties and overload with our extras.
                File propFile = new File(modFile,"gokb.properties");
                if (propFile.exists()) {
                    ExtendedProperties p = new ExtendedProperties();
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
        }
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