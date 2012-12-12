package com.k_int.gokb.refine.commands;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileUploadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.k_int.gokb.refine.A_RefineAPIBridge;
import com.k_int.gokb.refine.ResponseWrapper;

import com.google.refine.ProjectManager;
import com.google.refine.RefineServlet;
import com.google.refine.commands.Command;
import com.google.refine.commands.project.CreateProjectCommand;

import edu.mit.simile.butterfly.ButterflyModule;


public class CreateProject extends A_RefineAPIBridge {
    final static Logger logger = LoggerFactory.getLogger("GOKb-create-project_command");
    
    @Override
    public void init(RefineServlet servlet) {
        
        // Do the defaults.
        super.init(servlet);
        
        // Create a copy of the core create command and override any methods here.
        coreCreateCommand = new CreateProjectCommand();
        coreCreateCommand.init(servlet);
        
        // Get the core module.
        ButterflyModule core = servlet.getModule("core");
        
        // Replace the core command with our own.
        RefineServlet.registerCommand(
            core,
           "create-project-from-upload",
           this
        );
        
    }
    
    private Command coreCreateCommand;

    @Override
    public void doPost(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {

        // Wrap the servlet response so we can grab the redirect URL when done.
        ResponseWrapper resp = new ResponseWrapper(response);
        
        // Fire the original import command with our wrapped response.
        coreCreateCommand.doPost(request, resp);
        
        // Get the project manager.
        ProjectManager pm = ProjectManager.singleton;
        
        // Flag that we are busy.
        pm.setBusy(true);
        
        try {
        
            // Now we can grab the redirect URL and parse some parameters from it.
            String redirectTo = resp.getRedirectURL();
            
            if (redirectTo != null) {
                // Project was imported and the project ID should be on the param string.
                Matcher m = Pattern.compile("[\\?|\\&]\\Qproject\\E\\=([^\\?|\\&]+)").matcher(
                   resp.getRedirectURL()
                );
                
                if (m.find()) {
                    // Get the project ID.
                    final long projId = Long.parseLong(m.group(1));
                    
                    // Now we need to get the file contents from the post.
                    Map<String, Object> files = files(request);
                    
                    // Need to get the file and hash it.
                    
                }
            }
            
        } catch (FileUploadException e) {
            
            // Respond with the error page.
            respondException(response, e);
            
        } finally {
            // Clear busy flag.
            pm.setBusy(false);
        }
    }
}