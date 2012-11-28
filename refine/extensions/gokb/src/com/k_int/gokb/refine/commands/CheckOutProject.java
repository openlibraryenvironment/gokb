package com.k_int.gokb.refine.commands;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.k_int.gokb.refine.A_RefineAPIBridge;
import com.k_int.gokb.refine.RefineAPICallback;

import com.google.refine.ProjectManager;
import com.google.refine.ProjectMetadata;
import com.google.refine.model.Project;


public class CheckOutProject extends A_RefineAPIBridge {
    final static Logger logger = LoggerFactory.getLogger("GOKb-checkout-project_command");

    @Override
    public void doPost(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {       

        final ProjectManager pm = ProjectManager.singleton;
        pm.setBusy(true);
        try {

            final long projectID = Project.generateID();
            logger.info("Checking out GOKb project into Refine project {}", projectID);

            // Get all params from the current request.
            final Map<String, String[]> params = params(request);
            
            // Add the local generated project ID.
            params.put("localProjectID", new String[]{"" + projectID});
            
            // Call the project download method with our callback to import the project.
            postToAPI("projectCheckout", params, null, new RefineAPICallback() {

                @Override
                protected void onSuccess(InputStream result) throws Exception {

                    // Import the project
                    pm.importProject(projectID, result, true);
                    
                    // Try and load the meta-data
                    ProjectMetadata meta;
                    if (pm.loadProjectMetadata(projectID) && (meta = pm.getProjectMetadata(projectID)) != null) {
                        
                        // Now we have the meta data, set the GOKb specifics.
                        meta.setCustomMetadata("gokb", true);
                        meta.setCustomMetadata("gokb-id", params.get("projectID")[0]);
                        
                        // Move to project page.
                        redirect(response, "/project?project=" + projectID);
                    } else {
                        logger.error("Failed to import project. Reason unknown.");
                    }
                }
            });
        } catch (Exception e) {
            
            // Respond with the error page.
            respondWithErrorPage(request, response, e.getLocalizedMessage(), e);
        } finally {
            pm.setBusy(false);
        }
    }
}