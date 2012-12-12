package com.k_int.gokb.refine;

import javax.servlet.ServletConfig;

import com.google.refine.importing.ImportingManager;

import edu.mit.simile.butterfly.ButterflyModule;
import edu.mit.simile.butterfly.ButterflyModuleImpl;


public class GOKbModuleImpl extends ButterflyModuleImpl {
    @Override
    public void init(ServletConfig config) throws Exception {
        super.init(config);
        
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